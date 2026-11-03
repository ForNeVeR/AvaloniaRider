// Checks the design hook against a real previewer process, without the IDE: starts the previewer the same way the plugin
// does, hit-tests a grid of points of a XAML file, and verifies that every answer points to a matching tag of that file.
//
// usage: DesignHookHarness <previewerHost> <targetDir> <targetName> <xamlAssembly> <xamlFile> <projectPath> <hookAssembly> [gridStep]
// Prints one summary line. The exit code is 0 if the answers were all correct.

using System.Diagnostics;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.RegularExpressions;
using Avalonia.Remote.Protocol;
using Avalonia.Remote.Protocol.Designer;
using Avalonia.Remote.Protocol.Viewport;

var (host, targetDir, targetName, xamlAssembly, xamlFile, projectPath, hookAssembly) =
    (args[0], args[1], args[2], args[3], args[4], args[5], args[6]);
var step = args.Length > 7 ? int.Parse(args[7]) : 48;

var xaml = File.ReadAllText(xamlFile);
var lines = File.ReadAllLines(xamlFile);

int GetFreePort()
{
    var listener = new TcpListener(IPAddress.Loopback, 0);
    listener.Start();
    var port = ((IPEndPoint)listener.LocalEndpoint).Port;
    listener.Stop();
    return port;
}

var transportPort = GetFreePort();
var hookListener = new TcpListener(IPAddress.Loopback, 0);
hookListener.Start();

var updateResult = new TaskCompletionSource<UpdateXamlResultMessage>();
FrameMessage? lastFrame = null;

using var server = new BsonTcpTransport().Listen(IPAddress.Loopback, transportPort, connection =>
{
    connection.OnMessage += async (transport, message) =>
    {
        switch (message)
        {
            case StartDesignerSessionMessage:
                await transport.Send(new ClientSupportedPixelFormatsMessage { Formats = [PixelFormat.Rgba8888] });
                await transport.Send(new ClientRenderInfoMessage { DpiX = 96, DpiY = 96 });
                await transport.Send(new ClientViewportAllocatedMessage { Width = 1280, Height = 800, DpiX = 96, DpiY = 96 });
                await transport.Send(new UpdateXamlMessage { Xaml = xaml, AssemblyPath = xamlAssembly, XamlFileProjectPath = projectPath });
                break;
            case UpdateXamlResultMessage result:
                updateResult.TrySetResult(result);
                break;
            case FrameMessage frame:
                if (frame.Width > 0) lastFrame = frame;
                await transport.Send(new FrameReceivedMessage { SequenceId = frame.SequenceId });
                break;
        }
    };
    connection.Start();
});

var startInfo = new ProcessStartInfo("dotnet")
{
    WorkingDirectory = targetDir,
    RedirectStandardError = true,
    RedirectStandardOutput = true
};
foreach (var argument in new[]
         {
             "exec",
             "--runtimeconfig", Path.Combine(targetDir, targetName + ".runtimeconfig.json"),
             "--depsfile", Path.Combine(targetDir, targetName + ".deps.json"),
             host,
             "--transport", $"tcp-bson://127.0.0.1:{transportPort}/",
             "--method", "avalonia-remote",
             Path.Combine(targetDir, targetName + ".dll")
         })
{
    startInfo.ArgumentList.Add(argument);
}
startInfo.Environment["DOTNET_STARTUP_HOOKS"] = hookAssembly;
startInfo.Environment["AVALONIARIDER_DESIGN_HOOK_PORT"] = ((IPEndPoint)hookListener.LocalEndpoint).Port.ToString();

using var process = Process.Start(startInfo)!;
var hookLog = new StringBuilder();
process.ErrorDataReceived += (_, e) =>
{
    if (e.Data?.Contains("DesignHook") != true) return;
    lock (hookLog) hookLog.Append(e.Data).Append(' ');
};
process.OutputDataReceived += (_, _) => { };
process.BeginErrorReadLine();
process.BeginOutputReadLine();

var exitCode = 1;
var summary = "not finished";
try
{
    var completed = await Task.WhenAny(updateResult.Task, Task.Delay(TimeSpan.FromSeconds(60)), process.WaitForExitAsync());
    if (completed != updateResult.Task)
    {
        summary = $"no XAML result, exited={process.HasExited}";
        return;
    }

    if (updateResult.Task.Result.Error is { } error)
    {
        summary = "XAML error: " + error.Split('\n')[0];
        return;
    }

    await Task.Delay(1500);
    var accepted = hookListener.AcceptTcpClientAsync();
    if (await Task.WhenAny(accepted, Task.Delay(5000)) != accepted || lastFrame is null)
    {
        summary = $"hook did not connect, frame={lastFrame != null}";
        return;
    }

    using var client = accepted.Result;
    using var stream = client.GetStream();
    using var reader = new StreamReader(stream);
    await using var writer = new StreamWriter(stream, new UTF8Encoding(false));
    writer.AutoFlush = true;
    writer.NewLine = "\n";

    int points = 0, found = 0, mismatches = 0;
    var failures = new List<string>();
    for (var y = 10; y < lastFrame.Height; y += step)
    for (var x = 10; x < lastFrame.Width; x += step)
    {
        await writer.WriteLineAsync($"hit {x} {y}");
        var response = await reader.ReadLineAsync() ?? throw new IOException("The hook has closed the connection.");
        points++;
        if (response == "-") continue;

        found++;
        var parts = response.Split(' ');
        var (line, type) = (int.Parse(parts[0]), parts[2]);
        var text = line >= 1 && line <= lines.Length ? lines[line - 1] : "";
        if (Regex.IsMatch(text, $@"<(\w+:)?{Regex.Escape(type)}\b")) continue;

        mismatches++;
        if (failures.Count < 2) failures.Add($"{type}@{line}: {text.Trim()}");
    }

    summary = $"points={points} found={found} mismatches={mismatches} {string.Join(" | ", failures)}";
    exitCode = mismatches == 0 ? 0 : 1;
}
finally
{
    if (!process.HasExited) process.Kill(true);
    lock (hookLog) Console.WriteLine($"{Path.GetFileNameWithoutExtension(xamlFile)}: {summary} {hookLog}");
    Environment.Exit(exitCode);
}
