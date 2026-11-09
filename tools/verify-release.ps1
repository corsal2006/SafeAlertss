param(
    [Parameter(Mandatory=$true)][string]$ApkPath,
    [string]$SdkPath = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$BuildToolsVersion = '36.0.0',
    [switch]$AllowUnsignedReview
)
$ErrorActionPreference = 'Stop'
$apk = (Resolve-Path -LiteralPath $ApkPath).Path
$toolDirectory = Join-Path $SdkPath "build-tools\$BuildToolsVersion"
$aapt = Join-Path $toolDirectory 'aapt.exe'
$signer = Join-Path $toolDirectory 'apksigner.bat'
$badging = (& $aapt dump badging $apk 2>&1) -join "`n"
if ($LASTEXITCODE -ne 0) { throw 'APK cannot be decoded by Android build tools.' }
$manifest = (& $aapt dump xmltree $apk AndroidManifest.xml 2>&1) -join "`n"
if ($LASTEXITCODE -ne 0) { throw 'APK manifest is invalid.' }
$problems = [System.Collections.Generic.List[string]]::new()
if ($badging -notmatch "package: name='com.siddesh.smsalerts'") { $problems.Add('Wrong production package ID.') }
if ($badging -match 'application-debuggable') { $problems.Add('Debuggable APK must not be distributed.') }
if ($manifest -match 'android:testOnly[^\r\n]*0xffffffff') { $problems.Add('Test-only APK must not be distributed.') }
if ($manifest -notmatch 'android:allowBackup[^\r\n]*0x0\b') { $problems.Add('App backup must be disabled.') }
if ($manifest -notmatch 'android:usesCleartextTraffic[^\r\n]*0x0\b') { $problems.Add('Cleartext transport must be disabled.') }
if ($manifest -match 'android:networkSecurityConfig') { $problems.Add('Unexpected network security override in production APK.') }
if ($manifest -match 'android:debuggable[^\r\n]*0xffffffff') { $problems.Add('Debugging enabled in binary manifest.') }
foreach ($permission in @('READ_SMS','RECEIVE_SMS','READ_CALL_LOG','WRITE_CALL_LOG','REQUEST_INSTALL_PACKAGES','QUERY_ALL_PACKAGES','MANAGE_EXTERNAL_STORAGE','BIND_ACCESSIBILITY_SERVICE','BIND_NOTIFICATION_LISTENER_SERVICE')) {
    if ($manifest -match ('android.permission.' + $permission + '"')) { $problems.Add("Unexpected sensitive capability: $permission") }
}
# Verify each receiver block from the binary manifest, not just the source manifest.
$receiverBlocks = [regex]::Matches($manifest, '(?ms)^      E: receiver\b.*?(?=^      E:|\z)')
$boot = @($receiverBlocks | Where-Object { $_.Value -match 'com\.siddesh\.smsalerts\.service\.BootReceiver"' })
if ($boot.Count -ne 1 -or $boot[0].Value -notmatch 'android:exported[^\r\n]*0x0\b') { $problems.Add('Boot receiver must be private to the app/system.') }
$permissions = @([regex]::Matches($badging, "uses-permission: name='([^']+)'") | ForEach-Object { $_.Groups[1].Value })
foreach ($required in @('SEND_SMS','ACCESS_FINE_LOCATION','READ_CONTACTS','RECORD_AUDIO','BLUETOOTH_SCAN','BLUETOOTH_CONNECT','BLUETOOTH_ADVERTISE')) {
    if ("android.permission.$required" -notin $permissions) { $problems.Add("Required feature permission missing: $required") }
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($apk)
try {
    $entries = @($zip.Entries.FullName)
    foreach ($entry in $entries) {
        if ($entry -match '(?i)(\.jks$|\.keystore$|(^|/)(signing|secrets)\.properties$|(^|/)service[-_]account[^/]*\.json$|(^|/)google-services\.json$|(^|/)debug_network_security\.xml$)') {
            $problems.Add("Private or QA file packaged: $entry")
        }
    }
    if ('assets/map/leaflet.js' -notin $entries -or 'assets/map/map.js' -notin $entries) { $problems.Add('Bundled map implementation missing.') }
} finally { $zip.Dispose() }
$signatureOutput = (& $signer verify --verbose --print-certs $apk 2>&1) -join "`n"
$signatureValid = $LASTEXITCODE -eq 0
$debugCertificate = $signatureOutput -match 'CN=Android Debug'
if ($debugCertificate) { $problems.Add('Android debug certificate cannot be shipped as the release identity.') }
if (!$signatureValid -and !$AllowUnsignedReview) { $problems.Add('Release must have a valid APK signature.') }
$report = [ordered]@{
    checkedAtUtc = [DateTime]::UtcNow.ToString('o')
    apk = $apk
    sha256 = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
    packageMetadata = ($badging -split "`n" | Where-Object { $_ -match '^(package:|sdkVersion:|targetSdkVersion:)' })
    packagingChecksPassed = $problems.Count -eq 0
    signatureValid = $signatureValid
    releaseReady = $problems.Count -eq 0 -and $signatureValid -and !$debugCertificate
    playProtectStatus = 'NOT VERIFIED: local checks are not a Play Protect verdict'
    permissions = $permissions
    problems = @($problems)
}
$directory = Join-Path $PSScriptRoot '..\build'
[IO.Directory]::CreateDirectory($directory) | Out-Null
$report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $directory 'release-artifact-audit.json') -Encoding utf8
$report | ConvertTo-Json -Depth 5
if ($problems.Count -gt 0) { throw ($problems -join ' ') }
if (!$signatureValid) { Write-Warning 'Unsigned review artifact only. It cannot be installed or distributed.' }
