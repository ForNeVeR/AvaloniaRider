package me.fornever.avaloniarider.idea.settings

data class AvaloniaProjectSettingsControlState(
    val previewerMethod: AvaloniaPreviewerMethod,
    val synchronizeWithRunConfiguration: Boolean,
    val fpsLimit: Int,
    val workingDirectorySpecification: WorkingDirectorySpecification
) {

    companion object {

        operator fun invoke(
            projectState: AvaloniaProjectSettingsState,
            workspaceState: AvaloniaWorkspaceState
        ): AvaloniaProjectSettingsControlState {
            return AvaloniaProjectSettingsControlState(
                projectState.previewerMethod,
                projectState.synchronizeWithRunConfiguration,
                projectState.fpsLimit,
                workspaceState.workingDirectory
            )
        }
    }
}
