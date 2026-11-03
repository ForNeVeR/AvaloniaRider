using System;
using System.IO;
using System.Threading;

/// <summary>
/// The entry point for <c>DOTNET_STARTUP_HOOKS</c>. The runtime requires this exact name in the global namespace.
/// </summary>
/// <remarks>This class must not reference Avalonia types: they cannot be loaded this early.</remarks>
internal class StartupHook
{
    private static int s_installed;

    public static void Initialize()
    {
        var applicationName = Path.GetFileNameWithoutExtension(Environment.GetCommandLineArgs()[^1]);
        AppDomain.CurrentDomain.AssemblyLoad += (_, e) =>
        {
            if (Volatile.Read(ref s_installed) != 0) return;

            // The previewer host binds its XAML loader before it loads the application assembly, so this is the first
            // moment when the loader exists, and it still precedes any XAML update.
            if (e.LoadedAssembly.GetName().Name != applicationName) return;
            if (Interlocked.Exchange(ref s_installed, 1) != 0) return;

            Install();
        };
    }

    private static void Install()
    {
        try
        {
            AvaloniaRider.DesignHook.DesignHook.Install();
        }
        catch (Exception ex)
        {
            // Never take the previewer down: rendering keeps working, only the navigation becomes unavailable.
            Console.Error.WriteLine("[AvaloniaRider.DesignHook] Unable to install: " + ex);
        }
    }
}
