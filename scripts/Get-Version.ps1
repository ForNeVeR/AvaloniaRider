<#
    .SYNOPSIS
        The purpose of this script is to extract the version information from the compressed plugin artifact, and to
        return it via the standard output.

        It is used during CI builds to generate name for the artifact to upload.
    .PARAMETER DistributionsLocation
        Path to the directory containing compressed plugin distribution.
#>
param (
    [string] $DistributionsLocation = "$PSScriptRoot/../build/distributions"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$file = & "$PSScriptRoot/Get-Distribution.ps1" -DistributionsLocation $DistributionsLocation
if (!($file.Name -match 'avaloniarider-(.*?)\.zip')) {
    throw "File name `"$($file.Name)`" doesn't match the expected pattern"
}

$Matches[1]
