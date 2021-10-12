using System.Collections.Generic;
using System.Linq;
using JetBrains.Application.Components;
using JetBrains.Diagnostics;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.ProjectModel.Model2.Assemblies.Interfaces;
using JetBrains.ProjectModel.ProjectsHost;
using JetBrains.ProjectModel.ProjectsHost.SolutionHost;
using JetBrains.Rd.Tasks;
using JetBrains.RdBackend.Common.Features;
using JetBrains.RdBackend.Common.Features.ProjectModel.TargetFrameworks;
using JetBrains.Rider.Model;
using JetBrains.Util;

namespace ReSharperPlugin.AvaloniaRider
{
    [SolutionComponent]
    public class AvaloniaRiderProjectModelHost
    {
        private readonly ISolution _solution;
        private readonly ILogger _logger;
        private readonly IModuleReferencesResolveStore _moduleReferencesResolveStore;

        public AvaloniaRiderProjectModelHost(
            ISolution solution,
            ILogger logger,
            IModuleReferencesResolveStore moduleReferencesResolveStore)
        {
            _solution = solution;
            _logger = logger;
            _moduleReferencesResolveStore = moduleReferencesResolveStore;

            var riderProjectOutputModel = solution.GetProtocolSolution().GetAvaloniaRiderProjectModel();
            riderProjectOutputModel.GetProjectOutput.Set(GetProjectOutputModel);
            riderProjectOutputModel.GetReferencingProjects.Set(GetReferencingProjects);
        }

        private IProject GetProject(VirtualFileSystemPath projectFilePath)
        {
            var projectsHostContainer = _solution.ProjectsHostContainer();
            var solutionStructureContainer = projectsHostContainer.GetComponent<ISolutionStructureContainer>();
            var projectMark = solutionStructureContainer
                .GetProjectsByLocation(projectFilePath)
                .First();

            return _solution.GetProjectByMark(projectMark).NotNull();
        }

        private RdTask<RdProjectOutput> GetProjectOutputModel(Lifetime lifetime, RdGetProjectOutputArgs args)
        {
            var project = GetProject(
                VirtualFileSystemPath.Parse(args.ProjectFilePath, InteractionContext.SolutionContext));
            var targetFramework = project.TargetFrameworkIds
                // Take .NET Core first, then .NET Framework, and then .NET Standard. The comparer below will hold this order.
                .OrderBy(tfm => (!tfm.IsNetCoreApp, !tfm.IsNetFramework, !tfm.IsNetStandard))
                .First();

            _logger.Trace("TFM selected for project {0}: {1}", args, targetFramework);
            var assemblyInfo = project.GetOutputAssemblyInfo(targetFramework).NotNull();
            _logger.Trace("Assembly file path detected for project {0}: {1}", args, assemblyInfo.Location);
            return RdTask<RdProjectOutput>.Successful(
                new RdProjectOutput(
                    targetFramework.ToRdTargetFrameworkInfo(),
                    assemblyInfo.Location.ToString()));
        }

        private List<string> GetReferencingProjects(RdGetReferencingProjectsRequest request)
        {
            var targetProject = GetProject(
                VirtualFileSystemPath.Parse(request.TargetProjectFilePath, InteractionContext.Local));
            var targetFramework = targetProject.TargetFrameworkIds.FirstOrDefault(); // TODO: Determine from the document context [project.solution.projectModelTasks.targetFrameworks[projectEntityId] on frontend]
            var referencingProjects = targetProject.GetReferencingProjects(_moduleReferencesResolveStore, targetFramework);
            var potentialPaths = request.PotentiallyReferencingProjects
                .Select(s => VirtualFileSystemPath.Parse(s, InteractionContext.SolutionContext))
                .ToSet();
            return referencingProjects
                .Select(p => p.ProjectFileLocation)
                .Where(potentialPaths.Contains)
                .Select(p => p.ToString())
                .ToList();
        }
    }
}
