package me.fornever.avaloniarider.idea.settings

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.JBLabel
import me.fornever.avaloniarider.AvaloniaRiderBundle
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

    private val synchronizeWithRunConfigurationEditor = CheckBox(AvaloniaRiderBundle.message("settings.synchronizeWithRunConfiguration"))
    private var synchronizeWithRunConfiguration: Boolean
        get() = synchronizeWithRunConfigurationEditor.isSelected
        set(value) {
            synchronizeWithRunConfigurationEditor.isSelected = value
        }

    init {
        previewerMethod = initialState.previewerMethod
        synchronizeWithRunConfiguration = initialState.synchronizeWithRunConfiguration

        layout = GridBagLayout()
        add(JBLabel("Previewer Method:"), GridBagConstraints().apply { anchor = GridBagConstraints.LINE_START })
        add(previewerTransportTypeSelector, GridBagConstraints().apply { gridx = 1 })

        add(synchronizeWithRunConfigurationEditor, GridBagConstraints().apply { gridy = 1; gridwidth = 2 })
    }

    var currentState: AvaloniaSettingsState
        get() = AvaloniaSettingsState().apply {
            previewerMethod = this@AvaloniaSettingsComponent.previewerMethod
            synchronizeWithRunConfiguration = this@AvaloniaSettingsComponent.synchronizeWithRunConfiguration
        }
        set(value) {
            previewerMethod = value.previewerMethod
            synchronizeWithRunConfiguration = value.synchronizeWithRunConfiguration
        }

    val isModified: Boolean
        get() = currentState != initialState
}
