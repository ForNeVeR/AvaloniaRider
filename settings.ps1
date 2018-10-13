$PSScriptRoot = Split-Path $MyInvocation.MyCommand.Path -Parent
$PluginId = "ReSharperPlugin.AvaloniaRider"
$SolutionPath = "$PSScriptRoot\AvaloniaRider.sln"
$SourceBasePath = "$PSScriptRoot\src\dotnet"

$VisualStudioBaseDirectory = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\2017\*\"
if (@(Get-ChildItem "$VisualStudioBaseDirectory").Count -ne 1) { throw "Could not find single VisualStudio base directory. Please adjust the search pattern. " }
$DevEnvPath = Get-ChildItem "$VisualStudioBaseDirectory\Common7\IDE\devenv.exe"
$MSBuildPath = Get-ChildItem "$VisualStudioBaseDirectory\MSBuild\15.0\Bin\MSBuild.exe"

$OutputDirectory = "$PSScriptRoot\output"
$NuGetPath = "$PSScriptRoot\tools\nuget.exe"

Function Invoke-Exe {
    param(
        [parameter(mandatory=$true,position=0)] [ValidateNotNullOrEmpty()] [string] $Executable,
        [Parameter(ValueFromRemainingArguments=$true)][String[]] $Arguments,
        [parameter(mandatory=$false)] [array] $ValidExitCodes = @(0)
    )

    Write-Host "> $Executable $Arguments"
    $rc = Start-Process -FilePath $Executable -ArgumentList $Arguments -NoNewWindow -Wait -Passthru
    if (-Not $ValidExitCodes.Contains($rc.ExitCode)) {
        throw "'$Executable $Arguments' failed with exit code $($rc.ExitCode), valid exit codes: $ValidExitCodes"
    }
}