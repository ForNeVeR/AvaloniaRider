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

$file = Get-Item $DistributionsLocation/*
if (!$file) {
    throw "File not found in $DistributionsLocation"
}
if ($file.Count -gt 1) {
    throw "Found more files than expected in ${DistributionsLocation}: $($file.Count)"
}

Expand-Archive -Path $file -DestinationPath $DistributionsLocation/unpacked
