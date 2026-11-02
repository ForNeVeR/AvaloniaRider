using System.Reflection;
using System.Runtime.ExceptionServices;
using Avalonia.Markup.Xaml;

namespace AvaloniaRider.DesignHook;

/// <summary>
/// Wraps the previewer's XAML loader to make the XAML compiler record the source position of every element.
/// </summary>
public class XamlLoaderProxy : DispatchProxy
{
    public object Target { get; set; } = null!;

    protected override object? Invoke(MethodInfo? targetMethod, object?[]? args)
    {
        foreach (var argument in args ?? [])
        {
            if (argument is RuntimeXamlLoaderConfiguration configuration)
                configuration.CreateSourceInfo = true;
        }

        try
        {
            return targetMethod!.Invoke(Target, args);
        }
        catch (TargetInvocationException ex) when (ex.InnerException != null)
        {
            ExceptionDispatchInfo.Capture(ex.InnerException).Throw();
            throw;
        }
    }
}
