package me.fornever.avaloniarider.idea.settings

import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.panel
import me.fornever.avaloniarider.AvaloniaRiderBundle
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class AvaloniaProjectSettingsComponent {

    var currentState: ObservableMutableProperty<AvaloniaProjectSettingsControlState?> = AtomicProperty(null)

    val panel = panel {
        row(AvaloniaRiderBundle.message("settings.previewerMethod")) {
            comboBox(AvaloniaPreviewerMethod.values().toList()).bi
        }
    }

    private val initialProjectState = AvaloniaProjectSettingsState().apply {
        copyFrom(initialProjectState)
    }

    private val initialWorkspaceState = initialWorkspaceState.workingDirectory

    private val previewerTransportTypeSelector = ComboBox()
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

    private val workingDirectoryEditor = WorkingDirectoryEditor()
    private var workingDirectorySpecification: WorkingDirectorySpecification
        get() = workingDirectoryEditor.value
        set (value) {
            workingDirectoryEditor.value = value
        }

    init {
        previewerMethod = this.initialProjectState.previewerMethod
        synchronizeWithRunConfiguration = this.initialProjectState.synchronizeWithRunConfiguration

        layout = GridBagLayout()
        fun addComponent(component: JComponent, constraints: GridBagConstraints.() -> Unit) {
            add(component, GridBagConstraints().apply(constraints))
        }

        addComponent(JBLabel()) {
            anchor = GridBagConstraints.LINE_START
        }
        addComponent(previewerTransportTypeSelector) { gridx = 1 }

        addComponent(synchronizeWithRunConfigurationEditor) { gridy = 1; gridwidth = 2 }

        addComponent(JBLabel(AvaloniaRiderBundle.message("settings.fpsLimit"))) { gridy = 2 }
        addComponent(fpsLimitEditor) { gridy = 2; gridx = 1 }

        addComponent(JBLabel(AvaloniaRiderBundle.message("settings.workingDirectory"))) { gridy = 3 }
        addComponent(workingDirectoryEditor) { gridy = 3; gridx = 1 }
    }

    var currentProjectState: AvaloniaProjectSettingsState
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

    var currentWorkspaceState: WorkingDirectorySpecification
        get() = workingDirectorySpecification
        set(value) {
            workingDirectorySpecification = value
        }

    val isModified: Boolean
        get() = currentProjectState != initialProjectState && currentWorkspaceState != initialWorkspaceState
}

