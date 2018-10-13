Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. ".\settings.ps1"

Invoke-Exe $MSBuildPath "/t:Rebuild" "$SolutionPath" "/v:minimal"