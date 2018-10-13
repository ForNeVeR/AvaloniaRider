using JetBrains.ProjectModel;

namespace ReSharperPlugin.AvaloniaRider
{
    [SolutionComponent]
    public class SampleComponent
    {
        public SampleComponent(ISolution solution)
        {
            
            System.Diagnostics.Debugger.Launch();
        }
    }
}