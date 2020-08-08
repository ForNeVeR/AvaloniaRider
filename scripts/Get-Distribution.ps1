<#
    .SYNOPSIS
        This script gets the distribution file available in the path passed to it.
    .PARAMETER DistributionsLocation
        Path to the directory containing compressed plugin distribution.
#>
param (
    [string] $DistributionsLocation = "$PSScriptRoot/../build/distributions"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$file = Get-Item $DistributionsLocation/*.zip
if (!$file) {
    throw "File not found in $DistributionsLocation"
}
if (@($file).Count -gt 1) {
    throw "Found more files than expected in ${DistributionsLocation}: $($file.Count)"
}

return $file
