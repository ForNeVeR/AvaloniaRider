<#
    .SYNOPSIS
        This script publishes the plugin distribution from the $DistributionsLocation to the JetBrains Marketplace.
    .PARAMETER DistributionsLocation
        Path to the directory containing compressed plugin distribution.
    .PARAMETER PluginXmlId
        Plugin identifier.
    .PARAMETER Channel
        Channel name to publish the plugin.
    .PARAMETER AuthToken
        Token to authenticate to the Marketplace.
#>
param (
    [string] $DistributionsLocation = "$PSScriptRoot/../build/distributions",
    [string] $PluginXmlId = 'avalonia-rider',
    [string] $Channel = 'dev',
    [Parameter(Mandatory = $true)]
    [string] $AuthToken
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$file = & "$PSScriptRoot/Get-Distribution.ps1" -DistributionsLocation $DistributionsLocation
curl -i `
    --header "Authorization: Bearer $AuthToken" `
    -F xmlId=$PluginXmlId `
    -F file=@$file `
    -F channel=$Channel `
    https://plugins.jetbrains.com/plugin/uploadPlugin

if (!$?) {
    throw "Curl failed with exit code $LASTEXITCODE"
}
