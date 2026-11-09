$ErrorActionPreference = 'Stop'
$projectDirectory = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$dependencies = @(Get-Content -LiteralPath (Join-Path $projectDirectory 'build/release-dependencies.json') -Raw | ConvertFrom-Json)
# Leaflet is bundled as an asset rather than resolved by Gradle.
$queries = $dependencies + @(@{ package = @{ ecosystem = 'npm'; name = 'leaflet' }; version = '1.9.4' })
$response = Invoke-RestMethod -Uri 'https://api.osv.dev/v1/querybatch' -Method Post -ContentType 'application/json' -Body (@{ queries = $queries } | ConvertTo-Json -Depth 7) -TimeoutSec 60
if ($response.results.Count -ne $queries.Count) { throw 'Incomplete vulnerability database response.' }
$matches = @()
for ($index = 0; $index -lt $queries.Count; $index++) {
    $result = $response.results[$index]
    if ($result.next_page_token) { throw 'Vulnerability results are paginated; complete the additional pages before claiming the scan passed.' }
    if ($result.vulns.Count -gt 0) {
        $matches += @{ package = $queries[$index].package; version = $queries[$index].version; advisories = @($result.vulns.id) }
    }
}
$report = @{
    checkedAtUtc = [DateTime]::UtcNow.ToString('o')
    source = 'https://api.osv.dev/v1/querybatch'
    queriedVersions = $queries.Count
    matches = $matches
    limitation = 'Known version matches only. No APK upload, reachability analysis, malware certification or Play Protect verdict.'
}
$report | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath (Join-Path $projectDirectory 'build/release-dependency-audit.json') -Encoding utf8
$report | ConvertTo-Json -Depth 7
if ($matches.Count -gt 0) { throw 'Dependency advisories require review; see release-dependency-audit.json.' }
