package me.fornever.avaloniarider.idea.settings

import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.map
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import me.fornever.avaloniarider.AvaloniaRiderBundle
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class AvaloniaProjectSettingsComponent(initialState: AvaloniaProjectSettingsControlState) {

    val currentState: ObservableMutableProperty<AvaloniaProjectSettingsControlState> = AtomicProperty(initialState)

    private fun <T> getState(getter: (AvaloniaProjectSettingsControlState) -> T): T {
        return getter(currentState.get())
    }

    private fun updateState(update: (AvaloniaProjectSettingsControlState) -> AvaloniaProjectSettingsControlState) {
        currentState.set(update(currentState.get()))
    }

    val panel = panel {
        row(AvaloniaRiderBundle.message("settings.previewerMethod")) {
            comboBox(AvaloniaPreviewerMethod.values().toList()).bindItem(
                { getState { it.previewerMethod } },
                { value ->
                    updateState {
                        it.copy(
                            previewerMethod = value ?: AvaloniaPreviewerMethod.AvaloniaRemote
                        )
                    }
                },
            )
        }
        row {
            checkBox(AvaloniaRiderBundle.message("settings.synchronizeWithRunConfiguration")).bindSelected(
                { getState { it.synchronizeWithRunConfiguration } },
                { value -> updateState { it.copy(synchronizeWithRunConfiguration = value) } }
            )
        }
        row(AvaloniaRiderBundle.message("settings.fpsLimit")) {
            intTextField(IntRange(0, 1000)).bindIntText(
                { getState { it.fpsLimit } },
                { value -> updateState { it.copy(fpsLimit = value) } }
            )
        }
        row(AvaloniaRiderBundle.message("settings.workingDirectory")) {
            comboBox(WorkingDirectorySpecification.values().toList()).bindItem(
                { getState { it.workingDirectorySpecification } },
                { value ->
                    updateState {
                        it.copy(workingDirectorySpecification = value ?: WorkingDirectorySpecification.DefinedByMsBuild)
                    }
                }
            )
        }
    }
}
