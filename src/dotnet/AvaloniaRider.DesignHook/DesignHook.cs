using System;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Reflection;
using System.Text;
using System.Threading;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Markup.Xaml;
using Avalonia.Markup.Xaml.Diagnostics;
using Avalonia.Threading;
using Avalonia.VisualTree;

namespace AvaloniaRider.DesignHook;

/// <summary>
/// Runs inside the previewer process and answers the IDE's question "which XAML element is at this point?".
/// </summary>
/// <remarks>
/// The protocol is line-based. The IDE sends <c>hit X Y</c> to find an element, or <c>click X Y</c> to also select the
/// tab under the point. The answer is <c>LINE COLUMN TYPE</c>, or <c>-</c> if there is no element.
/// </remarks>
internal static class DesignHook
{
    private const string PortVariableName = "AVALONIARIDER_DESIGN_HOOK_PORT";

    /// <summary>The XAML compiler uses this host for the document it gets from the IDE.</summary>
    private const string OpenDocumentHostPrefix = "runtimexaml";

    private static volatile Window? s_window;

    public static void Install()
    {
        ReplaceXamlLoader();
        Window.WindowOpenedEvent.AddClassHandler<Window>((window, _) => s_window = window);

        var port = int.Parse(Environment.GetEnvironmentVariable(PortVariableName)
                             ?? throw new InvalidOperationException($"{PortVariableName} is not set"),
            CultureInfo.InvariantCulture);
        new Thread(() => Serve(port)) { IsBackground = true, Name = "AvaloniaRider.DesignHook" }.Start();
    }

    private static void ReplaceXamlLoader()
    {
        const BindingFlags anyVisibility = BindingFlags.Public | BindingFlags.NonPublic;
        var loaderType = typeof(AvaloniaXamlLoader).GetNestedType("IRuntimeXamlLoader", anyVisibility)
                         ?? throw new MissingMemberException("IRuntimeXamlLoader");

        // AvaloniaLocator is public in the implementation assemblies, but hidden from the reference assemblies.
        const BindingFlags publicStatic = BindingFlags.Public | BindingFlags.Static;
        var locatorType = typeof(AvaloniaObject).Assembly.GetType("Avalonia.AvaloniaLocator", throwOnError: true)!;
        var resolver = locatorType.GetProperty("Current", publicStatic)!.GetValue(null)!;
        var locator = locatorType.GetProperty("CurrentMutable", publicStatic)!.GetValue(null)!;

        var loader = resolver.GetType().GetMethod("GetService", [typeof(Type)])!.Invoke(resolver, [loaderType])
                     ?? throw new InvalidOperationException("The XAML loader is not registered");

        var proxy = DispatchProxy.Create(loaderType, typeof(XamlLoaderProxy));
        ((XamlLoaderProxy)proxy).Target = loader;

        var registration = locatorType.GetMethod("Bind")!.MakeGenericMethod(loaderType).Invoke(locator, null)!;
        registration.GetType().GetMethods().First(method => method.Name == "ToConstant")
            .MakeGenericMethod(loaderType)
            .Invoke(registration, [proxy]);
    }

    private static void Serve(int port)
    {
        try
        {
            using var client = new TcpClient();
            client.Connect(IPAddress.Loopback, port);
            using var stream = client.GetStream();
            using var reader = new StreamReader(stream, Encoding.UTF8);
            using var writer = new StreamWriter(stream, new UTF8Encoding(false));
            writer.AutoFlush = true;
            writer.NewLine = "\n";

            while (reader.ReadLine() is { } request)
            {
                var parts = request.Split(' ');
                var point = new Point(
                    double.Parse(parts[1], CultureInfo.InvariantCulture),
                    double.Parse(parts[2], CultureInfo.InvariantCulture));
                var selectTab = parts[0] == "click";
                writer.WriteLine(
                    Dispatcher.UIThread.InvokeAsync(() => FindElement(point, selectTab)).GetTask().GetAwaiter().GetResult());
            }
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine("[AvaloniaRider.DesignHook] Connection terminated: " + ex);
        }
    }

    private static string FindElement(Point point, bool selectTab)
    {
        if (s_window is not { } window) return "-";

        var painted = window.GetVisualsAt(point).FirstOrDefault();
        if (selectTab)
        {
            var tab = painted?.GetSelfAndVisualAncestors().OfType<TabItem>().FirstOrDefault();
            tab?.IsSelected = true;
        }

        // Start from what is painted at the point, because it is what the user sees and clicks, and then descend by
        // bounds, because e.g. the blank part of a TextBlock isn't painted, and would otherwise resolve to its parent.
        var start = (painted?.GetSelfAndVisualAncestors() ?? []).FirstOrDefault(IsInOpenDocument) ?? (Visual)window;
        var element = FindDeepestElement(start, window, point) ?? start;

        return GetOpenDocumentInfo(element) is { } info
            ? string.Create(CultureInfo.InvariantCulture, $"{info.LineNumber} {info.LinePosition} {element.GetType().Name}")
            : "-";
    }

    private static Visual? FindDeepestElement(Visual visual, Window window, Point pointInWindow)
    {
        if (!visual.IsVisible) return null;

        var point = window.TranslatePoint(pointInWindow, visual);
        var isInside = point is { } local && new Rect(visual.Bounds.Size).Contains(local);
        if (visual.ClipToBounds && !isInside) return null;

        foreach (var child in visual.GetVisualChildren().OrderByDescending(child => child.ZIndex))
        {
            if (FindDeepestElement(child, window, pointInWindow) is { } found)
                return found;
        }

        return isInside && IsInOpenDocument(visual) ? visual : null;
    }

    private static bool IsInOpenDocument(Visual visual) => GetOpenDocumentInfo(visual) != null;

    /// <summary>Elements of precompiled controls may have source info too, but it points to their own files.</summary>
    private static XamlSourceInfo? GetOpenDocumentInfo(Visual visual) =>
        XamlSourceInfo.GetXamlSourceInfo(visual) is { } info
        && info.SourceUri?.Host.StartsWith(OpenDocumentHostPrefix, StringComparison.Ordinal) == true
            ? info
            : null;
}
