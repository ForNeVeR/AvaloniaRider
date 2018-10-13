Param(
    $RootSuffix = "AvaloniaRider",
    $Version = "1.0.0"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. ".\settings.ps1"

$UserProjectXmlFile = "$SourceBasePath\$PluginId\$PluginId.csproj.user"

if (!(Test-Path "$UserProjectXmlFile")) {
    # Determine download link
    $ReleaseUrl = "https://data.services.jetbrains.com/products/releases?code=RSU&type=eap&type=release"
    $DownloadLink = [uri] $(Invoke-WebRequest -UseBasicParsing $ReleaseUrl | ConvertFrom-Json).RSU[0].downloads.windows.link

    # Download installer
    $InstallerFile = "$PSScriptRoot\build\installer\$($DownloadLink.Segments[-1])"
    if (!(Test-Path $InstallerFile)) {
        mkdir -Force $(Split-Path $InstallerFile -Parent) > $null
        Write-Output "Downloading from $DownloadLink"
        (New-Object System.Net.WebClient).DownloadFile($DownloadLink, $InstallerFile)
    } else {
        Write-Output "Using $($DownloadLink.segments[-1]) from cache"
    }

    # Execute installer
    Write-Output "Installing experimental hive"
    Invoke-Exe $InstallerFile "/VsVersion=15.0" "/SpecificProductNames=ReSharper" "/Hive=$RootSuffix" "/Silent=True"

    $PluginRepository = "$env:LOCALAPPDATA\JetBrains\plugins"
    $InstallationDirectory = $(Get-ChildItem "$env:APPDATA\JetBrains\ReSharperPlatformVs*\v*_*$RootSuffix\NuGet.Config").Directory

    # Adapt packages.config
    if (Test-Path "$InstallationDirectory\packages.config") {
        $PackagesXml = [xml] (Get-Content "$InstallationDirectory\packages.config")
    } else {
        $PackagesXml = [xml] ("<?xml version=`"1.0`" encoding=`"utf-8`"?><packages></packages>")
    }

    if ($null -eq $PackagesXml.SelectSingleNode(".//package[@id='$PluginId']/@id")) {
        $PluginNode = $PackagesXml.CreateElement('package')
        $PluginNode.setAttribute("id", "$PluginId")
        $PluginNode.setAttribute("version", "$Version")

        $PackagesNode = $PackagesXml.SelectSingleNode("//packages")
        $PackagesNode.AppendChild($PluginNode) > $null

        $PackagesXml.Save("$InstallationDirectory\packages.config")
    }

    # Install plugin
    Invoke-Exe $MSBuildPath "/t:Restore;Build;Pack" "$SolutionPath" "/v:minimal" "/p:PackageVersion=$Version" "/p:PackageOutputPath=`"$OutputDirectory`""
    Invoke-Exe $NuGetPath install $PluginId -OutputDirectory "$PluginRepository" -Source "$OutputDirectory" -DependencyVersion Ignore

    Write-Output "Re-installing experimental hive"
    Invoke-Exe "$InstallerFile" "/VsVersion=15.0" "/SpecificProductNames=ReSharper" "/Hive=$RootSuffix" "/Silent=True"

    # Adapt user project file
    $HostIdentifier = "$($InstallationDirectory.Parent.Name)_$($InstallationDirectory.Name.Split('_')[-1])"
    
    Set-Content -Path "$UserProjectXmlFile" -Value "<Project><PropertyGroup><HostFullIdentifier></HostFullIdentifier></PropertyGroup></Project>"

    $ProjectXml = [xml] (Get-Content "$UserProjectXmlFile")
    $HostIdentifierNode = $ProjectXml.SelectSingleNode(".//HostFullIdentifier")
    $HostIdentifierNode.InnerText = $HostIdentifier
    $ProjectXml.Save("$UserProjectXmlFile")

    # Update Version.props
    $VersionSplit = $DownloadLink.Segments[-1].Split(".")
    $VersionsPropsFile = "$SourceBasePath\Versions.props"
    $VersionsPropsXml = [xml] (Get-Content "$VersionsPropsFile")
    $SdkVersionNode = $VersionsPropsXml.SelectSingleNode(".//SdkVersion")
    if ($VersionSplit.Count -eq 5){
        $SdkVersion = "$($VersionSplit[2]).$($VersionSplit[3]).0"
    } elseif ($VersionSplit[4].StartsWith("EAP")) {
        $SdkVersion = "$($VersionSplit[2]).$($VersionSplit[3]).0-*"
    } else {
        $SdkVersion = "$($VersionSplit[2]).$($VersionSplit[3]).$($VersionSplit[4])"
    }
    $SdkVersionNode.InnerText = $SdkVersion
    $VersionsPropsXml.Save("$VersionsPropsFile")
} else {
    Write-Warning "Plugin is already installed. To trigger reinstall, delete $UserProjectXmlFile."
}

Invoke-Exe $MSBuildPath "/t:Rebuild" "$SolutionPath" "/v:minimal"
Invoke-Exe $DevEnvPath "/rootSuffix $RootSuffix" "/ReSharper.Internal"