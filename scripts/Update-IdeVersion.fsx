open System
open System.Diagnostics
open System.IO
open System.Net.Http
open System.Text.RegularExpressions
open System.Threading.Tasks
open System.Xml.Linq
open System.Xml.XPath

let snapshotMetadataUrl =
    Uri "https://www.jetbrains.com/intellij-repository/snapshots/com/jetbrains/intellij/rider/riderRD/maven-metadata.xml"
let releaseMetadataUrl =
    Uri "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/rider/riderRD/maven-metadata.xml"

type TaskResult =
    | HasChanges of {|
            BranchName: string
            CommitMessage: string
            PrTitle: string
            PrBodyMarkdown: string
        |}
    | NoChanges

type IdeWave =
    | YearBased of year: int * number: int // 2024.1
type IdeFlavor =
    | Snapshot
    | EAP of int * dev: bool
    | RC of int
    | Stable

    static member Parse(x: string) =
        if x.StartsWith "EAP" && x.EndsWith "D" then EAP(int(x.Substring(3, x.Length - 4)), true)
        else if x.StartsWith "EAP" then EAP(int(x.Substring 3), false)
        else if x.StartsWith "RC" then RC(int(x.Substring 2))
        else if x = "" then Stable
        else failwithf $"Cannot parse IDE flavor: {x}."

type IdeVersion = // TODO[#358]: Verify ordering
    {
        Wave: IdeWave
        Minor: int
        Flavor: IdeFlavor
        IsSnapshot: bool
    }

    static member Parse(description: string) =
        let components = description.Split '-'

        let parseComponents (yearBased: string) flavor isSnapshot =
            let yearBasedComponents = yearBased.Split '.'
            let year, number, minor =
                match yearBasedComponents with
                | [| year; number |] -> int year, int number, 0
                | [| year; number; minor |] -> int year, int number, int minor
                | _ -> failwithf $"Cannot parse year-based version \"{yearBased}\"."
            let flavor =
                match IdeFlavor.Parse flavor, isSnapshot with
                | Stable, true -> Snapshot
                | flavor, _ -> flavor
            {
                Wave = YearBased(year, number)
                Minor = minor
                Flavor = flavor
                IsSnapshot = isSnapshot
            }

        match components with
        | [| yearBased |] -> parseComponents yearBased "" false
        | [| yearBased; "SNAPSHOT" |] -> parseComponents yearBased "" true
        | [| yearBased; flavor; "SNAPSHOT" |] -> parseComponents yearBased flavor true
        | _ -> failwithf $"Cannot parse IDE version \"{description}\"."

    override this.ToString() =
        String.concat "" [|
            let (YearBased(year, number)) = this.Wave
            string year
            "."
            string number
            if this.Minor <> 0 then
                "."
                string this.Minor

            match this.Flavor with
            | Snapshot -> ""
            | EAP(n, dev) ->
                let d = if dev then "D" else ""
                $"-EAP{string n}{d}"
            | RC n -> $"-RC{n}"
            | Stable -> ""

            if this.IsSnapshot then "-SNAPSHOT"
        |]

type IdeBuildSpec = {
    IdeVersions: Map<string, IdeVersion>
    KotlinVersion: Version
    UntilVersion: string
}

// https://plugins.jetbrains.com/docs/intellij/using-kotlin.html#kotlin-standard-library
let GetKotlinVersion wave =
    match wave with
    | YearBased(2024, 2) -> Version.Parse "1.9.24"
    | YearBased(2024, 1) -> Version.Parse "1.9.22"
    | YearBased(2023, 3) -> Version.Parse "1.9.21"
    | YearBased(2023, 2) -> Version.Parse "1.8.20"
    | YearBased(2023, 1) -> Version.Parse "1.8.0"
    | _ -> failwithf $"Cannot determine Kotlin version for IDE wave {wave}."

let GetUntilVersion ide =
    let (YearBased(year, number)) = ide.Wave
    let waveNumber = ((string year) |> Seq.skip 2 |> Seq.toArray |> String) + (string number)
    $"{waveNumber}.*"

let ReadLatestIdeSpecs specs kotlinKey untilKey = task {
    use http = new HttpClient()
    let readVersions (url: Uri) filter = task {
        printfn $"Loading document \"{url}\"."
        let sw = Stopwatch.StartNew()

        let! response = http.GetAsync(url)
        response.EnsureSuccessStatusCode() |> ignore

        let! content = response.Content.ReadAsStringAsync()
        let document = XDocument.Parse content
        printfn $"Loaded and processed the document in {sw.ElapsedMilliseconds} ms."

        let versions =
            document.XPathSelectElements "//metadata//versioning//versions//version"
            |> Seq.toArray
        if versions.Length = 0 then failwithf "No Rider SDK versions found."
        let maxVersion =
            versions
            |> Seq.map(fun version ->
                let version = version.Value
                printfn $"Version found: {version}"
                version
            )
            |> Seq.map IdeVersion.Parse
            |> Seq.filter filter
            |> Seq.max

        return maxVersion
    }

    let! pairs =
        specs
        |> Map.toSeq
        |> Seq.map(fun(id, (url, filter)) -> task {
            let! versions = readVersions url filter
            return id, versions
        })
        |> Task.WhenAll

    let ideVersions = Map.ofArray pairs
    let ideVersionForKotlin = ideVersions |> Map.find kotlinKey
    let ideVersionForUntilBuild = ideVersions |> Map.find untilKey

    return {
        IdeVersions = ideVersions
        KotlinVersion = GetKotlinVersion ideVersionForKotlin.Wave
        UntilVersion = GetUntilVersion ideVersionForUntilBuild
    }
}

let FindRepoRoot() = Task.FromResult(Environment.CurrentDirectory)
let ReadIdeVersions tomlFilePath keys = task {
    let! toml = File.ReadAllTextAsync tomlFilePath
    return
        keys
        |> Seq.map(fun key ->
            let re = Regex $@"[\r\n]{Regex.Escape key} = ""(.*?)"""
            let matches = re.Match(toml)
            if not matches.Success then failwithf $"Cannot find the key {key} in the TOML file \"{tomlFilePath}.\""
            key, IdeVersion.Parse matches.Groups[1].Value
        )
        |> Map.ofSeq
}

let ReadKotlinVersion filePath = task {
    let! toml = File.ReadAllTextAsync filePath
    let re = Regex @"[\r\n]kotlin = ""(.*?)"""
    let matches = re.Match(toml)
    if not matches.Success then failwithf $"Cannot parse TOML file \"{filePath}.\""
    return Version.Parse matches.Groups[1].Value
}

let ReadUntilBuild propertiesFilePath = task {
    let! properties = File.ReadAllTextAsync propertiesFilePath
    let re = Regex @"\r?\nuntilBuildVersion=(.*?)\r?\n"
    let matches = re.Match(properties)
    if not matches.Success then failwithf $"Cannot parse properties file \"{propertiesFilePath}.\""
    return matches.Groups[1].Value
}

let ReadCurrentIdeSpecs(ideVersionKeys: string seq) = task {
    let! repoRoot = FindRepoRoot()
    let gradleProperties = Path.Combine(repoRoot, "gradle.properties")
    let versionsTomlFile = Path.Combine(repoRoot, "gradle/libs.versions.toml")
    let! ideVersions = ReadIdeVersions versionsTomlFile ideVersionKeys
    let! kotlinVersion = ReadKotlinVersion versionsTomlFile
    let! untilBuild = ReadUntilBuild gradleProperties
    return {
        IdeVersions = ideVersions
        KotlinVersion = kotlinVersion
        UntilVersion = untilBuild
    }
}

let WriteIdeVersions (versions: Map<string, IdeVersion>) tomlFilePath = task {
    let! toml = File.ReadAllTextAsync tomlFilePath
    let mutable newContent = toml
    for key, version in Map.toSeq versions do
        let re = Regex $@"[\r\n]{Regex.Escape key} = ""(.*?)"""
        let version = version.ToString()
        newContent <- re.Replace(newContent, $"\n{key} = \"{version}\"")
    do! File.WriteAllTextAsync(tomlFilePath, newContent)
    return toml <> newContent
}

let WriteKotlinVersion (version: Version) filePath = task {
    let! toml = File.ReadAllTextAsync filePath
    let re = Regex @"[\r\n]kotlin = ""(.*?)"""
    let version = version.ToString()
    let newContent = re.Replace(toml, $"\nkotlin = \"{version}\"")
    do! File.WriteAllTextAsync(filePath, newContent)
    return toml <> newContent
}

let WriteUntilVersion (version: string) propertiesFilePath = task {
    let! properties = File.ReadAllTextAsync propertiesFilePath
    let re = Regex @"\r?\nuntilBuildVersion=(.*?)\r?\n"
    let newContent = re.Replace(properties, $"\nuntilBuildVersion={version}\n")
    do! File.WriteAllTextAsync(propertiesFilePath, newContent)
    return properties <> newContent
}

let ApplySpec { IdeVersions = ideVersions; KotlinVersion = kotlin; UntilVersion = untilVersion } = task {
    let! repoRoot = FindRepoRoot()
    let gradleProperties = Path.Combine(repoRoot, "gradle.properties")
    let versionsTomlFile = Path.Combine(repoRoot, "gradle/libs.versions.toml")

    let! ideVersionsUpdated = WriteIdeVersions ideVersions versionsTomlFile
    let! kotlinVersionUpdated = WriteKotlinVersion kotlin versionsTomlFile
    let! untilVersionUpdated = WriteUntilVersion untilVersion gradleProperties
    if not ideVersionsUpdated && not kotlinVersionUpdated && not untilVersionUpdated then
        failwithf "Cannot update neither IDE nor Kotlin version in the configuration files."
}
let GenerateResult localSpec remoteSpec =
    let fullVersion v =
        let (YearBased(year, number)) = v.Wave
        String.concat "" [|
            string year
            "."
            string number

            match v.Minor with
            | 0 -> ()
            | x -> string x

            match v.Flavor with
            | Snapshot -> ()
            | EAP(n, dev) ->
                let d = if dev then "D" else ""
                $" EAP{string n}{d}"
            | RC n -> $" RC{string n}"
            | Stable -> ()
        |]

    let message = String.concat "\n" [|
        yield!
            localSpec.IdeVersions
            |> Map.toSeq
            |> Seq.map(fun(key, version) ->
                let localVersion = fullVersion version
                let remoteVersion =
                    remoteSpec.IdeVersions
                    |> Map.find key
                    |> fullVersion
                $"- {key}: {localVersion} -> {remoteVersion}"
            )
        $"- Kotlin: {localSpec.KotlinVersion} -> {remoteSpec.KotlinVersion}"
        $"- untilBuildVersion: {localSpec.UntilVersion} -> {remoteSpec.UntilVersion}"
    |]

    {|
        BranchName = "dependencies/rider"
        CommitMessage = "Dependencies: update Rider"
        PrTitle = "Rider Update"
        PrBodyMarkdown = $"""
## Maintainer Note
> [!WARNING]
> This PR will not trigger CI by default. Please **close it and reopen manually** to trigger the CI.
>
> Unfortunately, this is a consequence of the current GitHub Action security model (by default, PRs created
> automatically aren't allowed to trigger other automation).

## Version updates
{message}
"""
    |}

let isStable version =
    version.Flavor = Stable

let atLeastEap version =
    match version.Flavor with
    | Snapshot -> false
    | EAP _ -> true
    | RC _ -> true
    | Stable -> true

let ideVersionSpec = Map.ofArray [|
    "riderSdk", (releaseMetadataUrl, isStable)
    "riderSdkPreview", (snapshotMetadataUrl, atLeastEap)
|]

let main() = task {
    let! latestSpec = ReadLatestIdeSpecs ideVersionSpec "riderSdk" "riderSdkPreview"
    let! currentSpec = ReadCurrentIdeSpecs ideVersionSpec.Keys
    if latestSpec <> currentSpec then
        printfn "Changes detected."
        printfn $"Local spec: {currentSpec}."
        printfn $"Remote spec: {latestSpec}."
        do! ApplySpec latestSpec
        return HasChanges <| GenerateResult currentSpec latestSpec
    else
        printfn $"No changes detected: both local and remote specs are {latestSpec}."
        return NoChanges
}

let writeOutput result out = task {
    match result with
    | NoChanges ->
        do! File.WriteAllTextAsync(out, "has-changes=false")
    | HasChanges changes ->
        let prBodyMarkdownPath = Path.GetTempFileName()
        do! File.WriteAllTextAsync(prBodyMarkdownPath, changes.PrBodyMarkdown)
        let text = $"""has-changes=true
branch-name={changes.BranchName}
commit-message={changes.CommitMessage}
pr-title={changes.PrTitle}
pr-body-path={prBodyMarkdownPath}
"""
        do! File.WriteAllTextAsync(out, text.ReplaceLineEndings "\n")

    printfn $"Result printed to \"{out}\"."
}

async {
    let gitHubOutputPath =
        Environment.GetEnvironmentVariable "GITHUB_OUTPUT"
        |> Option.ofObj
        |> Option.defaultValue "output.txt" // for tests
    let! result = Async.AwaitTask <| main()
    do! Async.AwaitTask <| writeOutput result gitHubOutputPath
} |> Async.RunSynchronously
