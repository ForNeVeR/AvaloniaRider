package me.fornever.avaloniarider.idea.settings

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.JBLabel
import me.fornever.avaloniarider.AvaloniaRiderBundle
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class AvaloniaProjectSettingsComponent(state: AvaloniaProjectSettingsState) : JPanel() {
    private val initialState = AvaloniaProjectSettingsState().apply {
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

    private val fpsLimitEditor = JSpinner(SpinnerNumberModel(0, 0, Int.MAX_VALUE, 1))
    private var fpsLimit: Int
        get() = fpsLimitEditor.model.value as Int
        set(value) {
            fpsLimitEditor.model.value = value
        }

    init {
        previewerMethod = initialState.previewerMethod
        synchronizeWithRunConfiguration = initialState.synchronizeWithRunConfiguration

        layout = GridBagLayout()
        fun addComponent(component: JComponent, constraints: GridBagConstraints.() -> Unit) {
            add(component, GridBagConstraints().apply(constraints))
        }

        addComponent(JBLabel(AvaloniaRiderBundle.message("settings.previewerMethod"))) {
            anchor = GridBagConstraints.LINE_START
        }
        addComponent(previewerTransportTypeSelector) { gridx = 1 }

        addComponent(synchronizeWithRunConfigurationEditor) { gridy = 1; gridwidth = 2 }

        addComponent(JBLabel(AvaloniaRiderBundle.message("settings.fpsLimit"))) { gridy = 2 }
        addComponent(fpsLimitEditor) { gridy = 2; gridx = 1 }
    }

    var currentState: AvaloniaProjectSettingsState
        get() = AvaloniaProjectSettingsState().apply {
            previewerMethod = this@AvaloniaProjectSettingsComponent.previewerMethod
            synchronizeWithRunConfiguration = this@AvaloniaProjectSettingsComponent.synchronizeWithRunConfiguration
            fpsLimit = this@AvaloniaProjectSettingsComponent.fpsLimit
        }
        set(value) {
            previewerMethod = value.previewerMethod
            synchronizeWithRunConfiguration = value.synchronizeWithRunConfiguration
            fpsLimit = value.fpsLimit
        }

    val isModified: Boolean
        get() = currentState != initialState
}
