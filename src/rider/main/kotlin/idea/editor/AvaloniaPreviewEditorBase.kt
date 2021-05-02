package me.fornever.avaloniarider.idea.editor

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.rd.platform.util.launchOnUi
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.reactive.IPropertyView
import com.jetbrains.rd.util.reactive.Property
import com.jetbrains.rd.util.reactive.adviseUntil
import com.jetbrains.rider.xaml.FocusableEditor
import com.jetbrains.rider.xaml.PreviewEditorToolbar
import com.jetbrains.rider.xaml.XamlPreviewEditor
import com.jetbrains.rider.xaml.preview.editor.XamlPreviewEditorSplitLayout
import com.jetbrains.rider.xaml.preview.editor.XamlPreviewerSplitEditor
import me.fornever.avaloniarider.idea.editor.actions.RestartPreviewerAction
import me.fornever.avaloniarider.idea.editor.actions.RunnableAssemblySelectorAction
import me.fornever.avaloniarider.previewer.AvaloniaPreviewerSessionController
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

abstract class AvaloniaPreviewEditorBase(
    final override val project: Project,
    private val currentFile: VirtualFile
) : UserDataHolderBase(), XamlPreviewEditor {

    override var parentEditor: FocusableEditor? = null
    final override val toolbar: PreviewEditorToolbar? = null
    override val virtualFilePath: String = currentFile.path
    override val zoomFactor: Double = 1.0
    override val zoomFactorLive: IPropertyView<Double> = Property(1.0)

    override fun updateLayout() {
    }

    override fun getName() = "Avalonia Preview"
    override fun isValid() = true

    override fun getFile() = currentFile

    private val lifetimeDefinition = LifetimeDefinition()
    protected val lifetime: Lifetime = lifetimeDefinition

    private val assemblySelectorAction = RunnableAssemblySelectorAction(lifetime, project)
    private val selectedProjectPath = assemblySelectorAction.selectedProjectPath
    protected val sessionController = AvaloniaPreviewerSessionController(project, lifetime, file, selectedProjectPath)
    init {
        sessionController.status.adviseUntil(lifetime) { status ->
            when (status) {
                AvaloniaPreviewerSessionController.Status.Working -> {
                    lifetime.launchOnUi {
                        (parentEditor as? XamlPreviewerSplitEditor<*, *>)?.triggerLayoutChange(
                            XamlPreviewEditorSplitLayout.SPLIT,
                            false
                        )
                    }
                    true
                }
                else -> false
            }
        }
    }

    protected abstract val toolbarComponent: JComponent
    protected abstract val editorComponent: JComponent

    private val component = lazy {
        JPanel().apply {
            layout = BorderLayout()

            val toolbarPanel = JPanel().apply {
                layout = BorderLayout()
                add(toolbarComponent, BorderLayout.LINE_END)
            }

            add(toolbarPanel, BorderLayout.PAGE_START)
            add(editorComponent, BorderLayout.CENTER)
        }
    }

    final override fun getComponent() = component.value
    override fun getPreferredFocusedComponent() = editorComponent

    protected fun createToolbarComponent(vararg actions: AnAction): JComponent {
        val actionGroup = DefaultActionGroup().apply {
            add(assemblySelectorAction)
            add(RestartPreviewerAction(lifetime, sessionController, selectedProjectPath))
            addAll(*actions)
        }

        val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, actionGroup, true)
        return toolbar.component
    }

    override fun isModified() = false
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun setState(state: FileEditorState) {}
    override fun selectNotify() {}
    override fun deselectNotify() {}
    override fun getCurrentLocation(): FileEditorLocation? = null
    override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? = null
    override fun dispose() {
        lifetimeDefinition.terminate()
    }
}
