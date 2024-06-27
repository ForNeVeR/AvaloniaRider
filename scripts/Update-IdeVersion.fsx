#r "nuget: HtmlAgilityPack, 1.11.61"

open System
open System.Diagnostics
open System.IO
open System.Net.Http
open System.Text.RegularExpressions
open System.Threading.Tasks
open HtmlAgilityPack

let repositoryUrl = Uri "https://www.jetbrains.com/intellij-repository/snapshots/"

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

type IdeVersion =
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
    IdeVersion: IdeVersion
    KotlinVersion: Version
}

// https://plugins.jetbrains.com/docs/intellij/using-kotlin.html#kotlin-standard-library
let GetKotlinVersion wave =
    match wave with
    | YearBased(2024, 3) -> Version.Parse "1.9.24" // tentative
    | YearBased(2024, 2) -> Version.Parse "1.9.24"
    | YearBased(2024, 1) -> Version.Parse "1.9.22"
    | YearBased(2023, 3) -> Version.Parse "1.9.21"
    | YearBased(2023, 2) -> Version.Parse "1.8.20"
    | YearBased(2023, 1) -> Version.Parse "1.8.0"
    | _ -> failwithf $"Cannot determine Kotlin version for IDE wave {wave}."

let ReadLatestIdeSpec filter = task {
    printfn $"Loading page \"{repositoryUrl}\"."
    let sw = Stopwatch.StartNew()
    use http = new HttpClient()
    let! response = http.GetAsync(repositoryUrl)
    response.EnsureSuccessStatusCode() |> ignore

    let! content = response.Content.ReadAsStringAsync()
    let document = HtmlDocument()
    document.LoadHtml content
    printfn $"Loaded and processed the page in {sw.ElapsedMilliseconds} ms."

    // body > h2:nth-child(179)
    let headers = document.DocumentNode.SelectNodes "//body//h2"
    let riderSectionHeader =
        headers
        |> Seq.tryFind(fun h2 -> h2.InnerText = "com.jetbrains.intellij.rider")
        |> Option.defaultWith(fun() -> failwith "Cannot find Rider section at the page.")

    let nextNonTextSibling(x: HtmlNode) =
        let mutable c = x.NextSibling
        while c :? HtmlTextNode do
            let prev = c
            c <- c.NextSibling
            if c = null then failwithf $"Cannot find new non-text sibling of element {prev}."
        c

    let table = nextNonTextSibling riderSectionHeader
    if table.Name <> "table" then
        failwithf $"Next element after Rider section header is a {table.Name} and not a table."

    let rows = table.SelectNodes "tbody//tr"
    if rows.Count = 0 then failwithf "No rows found in Rider version table."
    let maxVersion =
        rows
        |> Seq.map(fun row ->
            let version = row.SelectNodes("td").[0].InnerText
            printfn $"Version found: {version}"
            version
        )
        |> Seq.map IdeVersion.Parse
        |> Seq.filter filter
        |> Seq.max

    return {
        IdeVersion = maxVersion
        KotlinVersion = GetKotlinVersion maxVersion.Wave
    }
}

let FindRepoRoot() = Task.FromResult(Environment.CurrentDirectory)
let ReadIdeVersion filePath = task {
    let! properties = File.ReadAllTextAsync filePath
    let re = Regex @"[\r\n]riderSdkVersion=(.*?)[\r\n]"
    let matches = re.Match(properties)
    if not matches.Success then failwithf $"Cannot parse properties file \"{filePath}.\""
    return IdeVersion.Parse matches.Groups[1].Value
}

let ReadKotlinVersion filePath = task {
    let! toml = File.ReadAllTextAsync filePath
    let re = Regex @"[\r\n]kotlin = ""(.*?)"""
    let matches = re.Match(toml)
    if not matches.Success then failwithf $"Cannot parse TOML file \"{filePath}.\""
    return Version.Parse matches.Groups[1].Value
}

let ReadCurrentIdeSpec() = task {
    let! repoRoot = FindRepoRoot()
    let gradleProperties = Path.Combine(repoRoot, "gradle.properties")
    let versionsTomlFile = Path.Combine(repoRoot, "gradle/libs.versions.toml")
    let! ideVersion = ReadIdeVersion gradleProperties
    let! kotlinVersion = ReadKotlinVersion versionsTomlFile
    return {
        IdeVersion = ideVersion
        KotlinVersion = kotlinVersion
    }
}

let WriteIdeVersion (ide: IdeVersion) filePath = task {
    let! properties = File.ReadAllTextAsync filePath
    let re = Regex @"[\r\n]riderSdkVersion=(.*?)[\r\n]"
    let version = ide.ToString()
    let newContent = re.Replace(properties, $"\nriderSdkVersion={version}\n")
    do! File.WriteAllTextAsync(filePath, newContent)
    return properties = newContent
}

let WriteKotlinVersion (version: Version) filePath = task {
    let! toml = File.ReadAllTextAsync filePath
    let re = Regex @"[\r\n]kotlin = ""(.*?)"""
    let version = version.ToString()
    let newContent = re.Replace(toml, $"\nkotlin = \"{version}\"")
    do! File.WriteAllTextAsync(filePath, newContent)
    return toml = newContent
}

let ApplySpec { IdeVersion = ide; KotlinVersion = kotlin } = task {
    let! repoRoot = FindRepoRoot()
    let gradleProperties = Path.Combine(repoRoot, "gradle.properties")
    let versionsTomlFile = Path.Combine(repoRoot, "gradle/libs.versions.toml")

    let! ideVersionUpdated = WriteIdeVersion ide gradleProperties
    let! kotlinVersionUpdated = WriteKotlinVersion kotlin versionsTomlFile
    if not ideVersionUpdated && not kotlinVersionUpdated then
        failwithf "Cannot update neither IDE nor Kotlin version in the configuration files."
}
let GenerateResult latestSpec =
    let newIdeVersion = latestSpec.IdeVersion
    let (YearBased(year, number)) = newIdeVersion.Wave
    let fullVersion = String.concat "" [|
        string year
        "."
        string number

        match newIdeVersion.Minor with
        | 0 -> ()
        | x -> string x

        match newIdeVersion.Flavor with
        | Snapshot -> ()
        | EAP(n, dev) ->
            let d = if dev then "D" else ""
            $" EAP{string n}{d}"
        | RC n -> $" RC{string n}"
        | Stable -> ()
    |]

    {|
        BranchName = $"dependencies/rider-{fullVersion}"
        CommitMessage = $"Dependencies: update Rider to {fullVersion}"
        PrTitle = $"Rider {fullVersion}"
        PrBodyMarkdown = $"""Update Rider to {fullVersion}.

Update Kotlin to {latestSpec.KotlinVersion}.
"""
    |}

let atLeastEap version =
    match version.Flavor with
    | Snapshot -> false
    | EAP _ -> true
    | RC _ -> true
    | Stable -> true

let main() = task {
    let! latestSpec = ReadLatestIdeSpec atLeastEap
    let! currentSpec = ReadCurrentIdeSpec()
    if latestSpec <> currentSpec then
        printfn "Changes detected."
        printfn $"Local spec: {currentSpec}."
        printfn $"Remote spec: {latestSpec}."
        do! ApplySpec latestSpec
        return HasChanges <| GenerateResult latestSpec
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
