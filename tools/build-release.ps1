param([string]$SdkPath = "$env:LOCALAPPDATA\Android\Sdk")
$ErrorActionPreference = 'Stop'
$projectDirectory = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
Push-Location $projectDirectory
try {
    # Fail before packaging if credentials are absent, invalid, expired or use Android's debug certificate.
    & .\gradlew.bat :app:verifyReleaseSigning :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Release signing/build/tests failed. No release was delivered.' }
    & "$PSScriptRoot\verify-release.ps1" -ApkPath 'app\build\outputs\apk\release\app-release.apk' -SdkPath $SdkPath
    Write-Output 'Signed release passed local artifact checks. Google Play Protect review remains a separate check.'
} finally { Pop-Location }
