package me.fornever.avaloniarider.idea.settings

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import java.awt.FlowLayout
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

    init {
        previewerMethod = initialState.previewerMethod

        layout = FlowLayout(FlowLayout.LEFT)
        add(JBLabel("Previewer Method:"))
        add(previewerTransportTypeSelector)
    }

    var currentState: AvaloniaSettingsState
        get() = AvaloniaSettingsState().apply {
            previewerMethod = this@AvaloniaSettingsComponent.previewerMethod
        }
        set(value) {
            previewerMethod = value.previewerMethod
        }

    val isModified: Boolean
        get() = currentState != initialState
}
