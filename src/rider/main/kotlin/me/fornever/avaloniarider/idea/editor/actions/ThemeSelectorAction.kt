package me.fornever.avaloniarider.idea.editor.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.rd.util.reactive.Property
import me.fornever.avaloniarider.AvaloniaRiderBundle
import me.fornever.avaloniarider.idea.settings.AvaloniaPreviewerTheme
import javax.swing.JComponent

class ThemeSelectorAction(
    private val selectedTheme: Property<AvaloniaPreviewerTheme>
) : ComboBoxAction() {

    private val popupActionGroup: DefaultActionGroup = DefaultActionGroup().apply {
        add(ThemeOptionAction(AvaloniaPreviewerTheme.None, AvaloniaRiderBundle.message("action.theme.noTheme")))
        add(ThemeOptionAction(AvaloniaPreviewerTheme.Light, AvaloniaRiderBundle.message("action.theme.lightTheme")))
        add(ThemeOptionAction(AvaloniaPreviewerTheme.Dark, AvaloniaRiderBundle.message("action.theme.darkTheme")))
    }

    override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup {
        return popupActionGroup
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.text = when (selectedTheme.value) {
            AvaloniaPreviewerTheme.None -> AvaloniaRiderBundle.message("action.theme.noTheme")
            AvaloniaPreviewerTheme.Light -> AvaloniaRiderBundle.message("action.theme.lightTheme")
            AvaloniaPreviewerTheme.Dark -> AvaloniaRiderBundle.message("action.theme.darkTheme")
        }
    }

    private inner class ThemeOptionAction(
        private val option: AvaloniaPreviewerTheme,
        text: String
    ) : DumbAwareAction(text) {
        override fun actionPerformed(e: AnActionEvent) {
            selectedTheme.value = option
        }

        override fun getActionUpdateThread() = ActionUpdateThread.EDT
    }
}

