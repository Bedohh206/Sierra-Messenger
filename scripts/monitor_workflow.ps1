<#
Monitors the GitHub Actions workflow run for the android-release.yml workflow
Requires environment variable GITHUB_TOKEN set to a PAT with repo scope.
Run from the repository root:

# powershell
$env:GITHUB_TOKEN = 'ghp_...'
.\scripts\monitor_workflow.ps1

# The script polls the latest run on branch 'main' and prints status, URL and artifacts.
#>

$Param()

# Accept token from MONITOR_PAT (preferred) or GITHUB_TOKEN (fallback)
$token = $env:MONITOR_PAT
if (-not $token) { $token = $env:GITHUB_TOKEN }

if (-not $token) {
    Write-Error "No token found. Set MONITOR_PAT or GITHUB_TOKEN environment variable and re-run."
    exit 1
}

$owner = 'Bedohh206'
$repo = 'Sierra-Messenger'
$workflow_file = 'android-release.yml'
$branch = 'main'
$pollInterval = 15

$authHeader = @{ Authorization = "token $env:GITHUB_TOKEN"; 'User-Agent' = 'GitHub-API' }

Write-Host "Using token from $(if ($env:MONITOR_PAT) { 'MONITOR_PAT' } else { 'GITHUB_TOKEN' })"
Write-Host "Attempting Bearer auth to fetch workflow ID for $workflow_file..."

# Try Bearer auth first; fall back to "token" scheme if GitHub responds with 401
$authHeader = @{ Authorization = "Bearer $token"; 'User-Agent' = 'GitHub-API' }
try {
    $wf = Invoke-RestMethod -Headers $authHeader -Uri "https://api.github.com/repos/$owner/$repo/actions/workflows/$workflow_file"
} catch {
    $errMsg = $_.Exception.Message
    if ($errMsg -match '401|Unauthorized') {
        Write-Host "Bearer auth failed (401). Retrying with 'token' scheme..."
        $authHeader = @{ Authorization = "token $token"; 'User-Agent' = 'GitHub-API' }
        try {
            $wf = Invoke-RestMethod -Headers $authHeader -Uri "https://api.github.com/repos/$owner/$repo/actions/workflows/$workflow_file"
        } catch {
            Write-Error "Failed to fetch workflow after retry. Check token scopes, SSO, and repo access. $_"
            exit 1
        }
    } else {
        Write-Error "Failed to fetch workflow. Check token and repo permissions. $_"
        exit 1
    }
}
$workflow_id = $wf.id
if (-not $workflow_id) { Write-Error 'Workflow not found or auth failed.'; exit 1 }

Write-Host "Monitoring workflow id $workflow_id on branch $branch..."

while ($true) {
    try {
        $runs = Invoke-RestMethod -Headers $authHeader -Uri "https://api.github.com/repos/$owner/$repo/actions/workflows/$workflow_id/runs?branch=$branch&per_page=1"
    } catch {
        Write-Error "Failed to list workflow runs: $_"
        Start-Sleep -Seconds $pollInterval
        continue
    }

    $run = $runs.workflow_runs | Select-Object -First 1
    if (-not $run) { Write-Host 'No runs yet; waiting...'; Start-Sleep -Seconds $pollInterval; continue }
    $runId = $run.id
    $status = $run.status
    $conclusion = $run.conclusion
    Write-Host "$(Get-Date -Format o) RunId:$runId Status:$status Conclusion:$conclusion"
    if ($status -eq 'completed') { break }
    Start-Sleep -Seconds $pollInterval
}

# final fetch and report
$runDetails = Invoke-RestMethod -Headers $authHeader -Uri "https://api.github.com/repos/$owner/$repo/actions/runs/$runId"
Write-Host "Final status: $($runDetails.status) / $($runDetails.conclusion)"
Write-Host "View run in browser: $($runDetails.html_url)"

# list artifacts (if any)
$artifacts = Invoke-RestMethod -Headers $authHeader -Uri "https://api.github.com/repos/$owner/$repo/actions/runs/$runId/artifacts"
if ($artifacts.total_count -gt 0) {
    $artifacts.artifacts | ForEach-Object {
        Write-Host "Artifact: $($_.name) size:$($_.size) bytes url:$($_.archive_download_url)"
    }
} else {
    Write-Host "No artifacts found."
}
