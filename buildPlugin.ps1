Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. ".\settings.ps1"

Invoke-Exe $NuGetPath Restore
Invoke-Exe $MSBuildPath "/t:Rebuild" "$SolutionPath" "/v:minimal"
