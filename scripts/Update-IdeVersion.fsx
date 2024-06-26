#r "nuget: HtmlAgilityPack, 1.11.61"

open System
open System.Diagnostics
open System.IO
open System.Net.Http
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

let ReadLatestIdeSpec() = task {
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
    let versions =
        rows
        |> Seq.map(fun row ->
            let version = row.SelectNodes("td").[0].InnerText
            printfn $"Version found: {version}"
            version
        )
        |> Seq.toArray

    return ()
}

let ReadCurrentIdeSpec() = failwithf "TODO"
let ApplySpec _ = failwithf "TODO"
let GenerateResult _ _ = failwithf "TODO"

let main() = task {
    let! latestSpec = ReadLatestIdeSpec()
    let! currentSpec = ReadCurrentIdeSpec()
    if latestSpec <> currentSpec then
        do! ApplySpec latestSpec
        return GenerateResult currentSpec latestSpec
    else
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
