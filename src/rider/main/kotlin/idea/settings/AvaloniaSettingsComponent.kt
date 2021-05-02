package me.fornever.avaloniarider.idea.settings

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JPanel

class AvaloniaSettingsComponent(state: AvaloniaSettingsState) : JPanel() {
    private val initialState = AvaloniaSettingsState().apply {
        copyFrom(state)
    }

    private val previewerTransportTypeSelector = ComboBox(AvaloniaPreviewerMethod.values())
    private var previewerMethod: AvaloniaPreviewerMethod
        get() = previewerTransportTypeSelector.selectedItem as AvaloniaPreviewerMethod
        set(value) {
            previewerTransportTypeSelector.selectedItem = value
        }

    private val projectSelectionModeSelector = ComboBox(ExecutableProjectSelectionMode.values())
    private var projectSelectionMode: ExecutableProjectSelectionMode
        get() = projectSelectionModeSelector.selectedItem as ExecutableProjectSelectionMode
        set(value) {
            projectSelectionModeSelector.selectedItem = value
        }

    init {
        previewerMethod = initialState.previewerMethod
        projectSelectionMode = initialState.projectSelectionMode

        layout = GridBagLayout()
        add(JBLabel("Previewer Method:"), GridBagConstraints().apply { anchor = GridBagConstraints.LINE_START })
        add(previewerTransportTypeSelector, GridBagConstraints().apply { gridx = 1 })

        add(JBLabel("Project Selection Mode:"), GridBagConstraints().apply { gridy = 1 })
        add(projectSelectionModeSelector, GridBagConstraints().apply { gridx = 1; gridy = 1 })
    }

    var currentState: AvaloniaSettingsState
        get() = AvaloniaSettingsState().apply {
            previewerMethod = this@AvaloniaSettingsComponent.previewerMethod
            projectSelectionMode = this@AvaloniaSettingsComponent.projectSelectionMode
        }
        set(value) {
            previewerMethod = value.previewerMethod
            projectSelectionMode = value.projectSelectionMode
        }

    val isModified: Boolean
        get() = currentState != initialState
}
