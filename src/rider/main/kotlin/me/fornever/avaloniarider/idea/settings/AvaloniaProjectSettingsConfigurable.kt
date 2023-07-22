package me.fornever.avaloniarider.idea.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import me.fornever.avaloniarider.AvaloniaRiderBundle

class AvaloniaProjectSettingsConfigurable(private val project: Project) : Configurable {

    private val projectSettings by lazy { AvaloniaProjectSettings.getInstance(project) }
    private val workspaceSettings by lazy { AvaloniaWorkspaceSettings.getInstance(project) }

    override fun getDisplayName() = "Avalonia"

    private val panel by lazy {
        panel {
            row(AvaloniaRiderBundle.message("settings.previewerMethod")) {
                comboBox(AvaloniaPreviewerMethod.values().toList()).bindItem(
                    { projectSettings.previewerTransportType },
                    { projectSettings.state.previewerMethod = it ?: AvaloniaPreviewerMethod.AvaloniaRemote },
                )
            }
            row {
                checkBox(AvaloniaRiderBundle.message("settings.synchronizeWithRunConfiguration")).bindSelected(
                    { projectSettings.synchronizeWithRunConfiguration },
                    { projectSettings.state.synchronizeWithRunConfiguration = it }
                )
            }
            row(AvaloniaRiderBundle.message("settings.fpsLimit")) {
                intTextField(IntRange(0, 1000)).bindIntText(
                    { projectSettings.fpsLimit },
                    { projectSettings.state.fpsLimit = it }
                )
            }
            row(AvaloniaRiderBundle.message("settings.workingDirectory")) {
                comboBox(WorkingDirectorySpecification.values().toList()).bindItem(
                    { workspaceSettings.state.workingDirectorySpecification },
                    {
                        workspaceSettings.state.workingDirectorySpecification =
                            it ?: WorkingDirectorySpecification.DefinedByMsBuild
                    }
                )
            }
        }
    }

    override fun createComponent() = panel
    override fun isModified() = panel.isModified()
    override fun apply() = panel.apply()
    override fun reset() = panel.reset()
}
