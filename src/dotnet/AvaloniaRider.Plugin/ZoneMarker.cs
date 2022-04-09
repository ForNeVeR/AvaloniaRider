using JetBrains.Application.BuildScript.Application.Zones;
using JetBrains.Platform.RdFramework;
using JetBrains.ProjectModel;
using JetBrains.Rider.Model;

namespace AvaloniaRider
{
    [ZoneMarker]
    public class ZoneMarker : IRequire<IProjectModelZone>, IRequire<ISinceClr4HostZone>, IRequire<IRdFrameworkZone>, IRequire<IRiderModelZone>
    {
    }
}
