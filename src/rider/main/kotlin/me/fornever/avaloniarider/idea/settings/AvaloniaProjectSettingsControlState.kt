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
                workspaceState.workingDirectorySpecification
            )
        }
    }
}

fun AvaloniaProjectSettingsState.apply(state: AvaloniaProjectSettingsControlState) {
    previewerMethod = state.previewerMethod
    synchronizeWithRunConfiguration = state.synchronizeWithRunConfiguration
    fpsLimit = state.fpsLimit
}

fun AvaloniaWorkspaceState.apply(state: AvaloniaProjectSettingsControlState) {
    workingDirectorySpecification = state.workingDirectorySpecification
}
