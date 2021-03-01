using JetBrains.Application.BuildScript.Application.Zones;
using JetBrains.Platform.RdFramework;
using JetBrains.ProjectModel;

namespace ReSharperPlugin.AvaloniaRider
{
    [ZoneMarker]
    public class ZoneMarker : IRequire<IProjectModelZone>, IRequire<ISinceClr4HostZone>, IRequire<IRdFrameworkZone>
    {
    }
}
