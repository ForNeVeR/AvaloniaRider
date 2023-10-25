package me.fornever.avaloniarider.idea.editor.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.rd.util.reactive.Property
import javax.swing.JComponent

class ZoomLevelSelectorAction(val zoom: Property<Double>) : ComboBoxAction() {

    companion object {

        val allZoomLevels = doubleArrayOf(
            0.25,
            0.33,
            0.5,
            0.67,
            0.75,
            0.8,
            0.9,
            1.0,
            1.1,
            1.25,
            1.5,
            1.75,
            2.0,
            2.5,
            3.0,
            4.0,
            5.0
        )

        private fun formatPercent(number: Double) = "${String.format("%,.0f", number * 100.0)}%"
    }

    val popupActionGroup: DefaultActionGroup = DefaultActionGroup(allZoomLevels.map { level ->
        object : DumbAwareAction(formatPercent(level)) {
            override fun actionPerformed(e: AnActionEvent) {
                zoom.value = level
                ActivityTracker.getInstance().inc()
            }
        }
    })
    override fun createPopupActionGroup(button: JComponent, dataContext: DataContext) = popupActionGroup

    override fun getActionUpdateThread() = ActionUpdateThread.EDT
    override fun update(e: AnActionEvent) {
        e.presentation.apply {
            text = formatPercent(zoom.value)
            icon = AllIcons.General.ZoomIn
        }
    }
}
