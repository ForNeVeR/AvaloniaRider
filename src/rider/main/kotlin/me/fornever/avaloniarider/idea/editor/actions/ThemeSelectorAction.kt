package me.fornever.avaloniarider.idea.editor.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.rd.util.reactive.Property
import me.fornever.avaloniarider.AvaloniaRiderBundle
import me.fornever.avaloniarider.idea.editor.AvaloniaPreviewEditorBase
import javax.swing.JComponent

class ThemeSelectorAction(
    private val selectedTheme: Property<AvaloniaPreviewEditorBase.ThemeOption>
) : ComboBoxAction() {

    private val popupActionGroup: DefaultActionGroup = DefaultActionGroup().apply {
        add(ThemeOptionAction(AvaloniaPreviewEditorBase.ThemeOption.NONE, "No Theme"))
        add(ThemeOptionAction(AvaloniaPreviewEditorBase.ThemeOption.LIGHT, "Light"))
        add(ThemeOptionAction(AvaloniaPreviewEditorBase.ThemeOption.DARK, "Dark"))
    }

    override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup {
        return popupActionGroup
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.text = when (selectedTheme.value) {
            AvaloniaPreviewEditorBase.ThemeOption.NONE -> AvaloniaRiderBundle.message("action.theme.noTheme")
            AvaloniaPreviewEditorBase.ThemeOption.LIGHT -> AvaloniaRiderBundle.message("action.theme.lightTheme")
            AvaloniaPreviewEditorBase.ThemeOption.DARK -> AvaloniaRiderBundle.message("action.theme.darkTheme")
        }
    }

    private inner class ThemeOptionAction(
        private val option: AvaloniaPreviewEditorBase.ThemeOption,
        text: String
    ) : DumbAwareAction(text) {
        override fun actionPerformed(e: AnActionEvent) {
            selectedTheme.value = option
        }

        override fun getActionUpdateThread() = ActionUpdateThread.BGT
    }
}

