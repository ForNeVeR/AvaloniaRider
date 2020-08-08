<#
    .SYNOPSIS
        The purpose of this script is to unpack the compressed plugin artifact.

        It is used during CI builds to generate the layout for uploading.
    .PARAMETER DistributionsLocation
        Path to the directory containing compressed plugin distribution.
#>
param (
    [string] $DistributionsLocation = "$PSScriptRoot/../build/distributions"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$file = & "$PSScriptRoot/Get-Distribution.ps1" -DistributionsLocation $DistributionsLocation

Expand-Archive -Path $file -DestinationPath $DistributionsLocation/unpacked
