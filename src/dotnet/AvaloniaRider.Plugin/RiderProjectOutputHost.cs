using System.Linq;
using JetBrains.Application.Components;
using JetBrains.Diagnostics;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.ProjectModel.ProjectsHost;
using JetBrains.ProjectModel.ProjectsHost.SolutionHost;
using JetBrains.Rd.Tasks;
using JetBrains.ReSharper.Host.Features;
using JetBrains.ReSharper.Host.Features.ProjectModel.TargetFrameworks;
using JetBrains.Rider.Model;
using JetBrains.Util;

namespace ReSharperPlugin.AvaloniaRider
{
    [SolutionComponent]
    public class RiderProjectOutputHost
    {
        private readonly ISolution _solution;
        private readonly ILogger _logger;

        public RiderProjectOutputHost(ISolution solution, ILogger logger)
        {
            _solution = solution;
            _logger = logger;

            var riderProjectOutputModel = solution.GetProtocolSolution().GetRiderProjectOutputModel();
            riderProjectOutputModel.GetProjectOutput.Set(GetProjectOutputModel);
        }

        private RdTask<RdProjectOutput> GetProjectOutputModel(Lifetime lifetime, RdGetProjectOutputArgs args)
        {
            var projectsHostContainer = _solution.ProjectsHostContainer();
            var solutionStructureContainer = projectsHostContainer.GetComponent<ISolutionStructureContainer>();
            var projectMark = solutionStructureContainer
                .GetProjectsByLocation(FileSystemPath.Parse(args.ProjectFilePath))
                .First();

            var project = _solution.GetProjectByMark(projectMark).NotNull();
            var targetFramework = project.TargetFrameworkIds
                // Take .NET Core first, then .NET Framework, and then .NET Standard. The comparer below will hold this order.
                .OrderBy(tfm => (!tfm.IsNetCoreApp, !tfm.IsNetFramework, !tfm.IsNetStandard))
                .First();
            var rdTargetFramework = targetFramework.ToRdTargetFrameworkInfo();

            _logger.Trace("TFM selected for project {0}: {1}", args, targetFramework);
            var assemblyInfo = project.GetOutputAssemblyInfo(targetFramework).NotNull();
            _logger.Trace("Assembly file path detected for project {0}: {1}", args, assemblyInfo.Location);
            return RdTask<RdProjectOutput>.Successful(
                new RdProjectOutput(
                    new RdTargetFrameworkIdMock(
                        rdTargetFramework.ShortName,
                        rdTargetFramework.PresentableName,
                        isNetCoreApp: rdTargetFramework.IsNetCoreApp,
                        isNetFramework: rdTargetFramework.IsNetFramework),
                    assemblyInfo.Location.ToString()));
        }
    }
}
