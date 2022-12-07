package me.fornever.avaloniarider.idea.editor

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.util.launchBackground
import com.intellij.openapi.rd.util.launchOnUi
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.UIUtil
import com.jetbrains.rd.framework.util.nextValue
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.reactive.IPropertyView
import com.jetbrains.rd.util.reactive.Property
import com.jetbrains.rd.util.reactive.compose
import com.jetbrains.rider.build.BuildParameters
import com.jetbrains.rider.build.tasks.BuildTaskThrottler
import com.jetbrains.rider.model.BuildTarget
import com.jetbrains.rider.xaml.core.XamlPreviewEditor
import com.jetbrains.rider.xaml.previewEditor.PreviewEditorToolbar
import com.jetbrains.rider.xaml.splitEditor.XamlSplitEditor
import com.jetbrains.rider.xaml.splitEditor.XamlSplitEditorSplitLayout
import me.fornever.avaloniarider.AvaloniaRiderBundle
import me.fornever.avaloniarider.idea.editor.actions.RestartPreviewerAction
import me.fornever.avaloniarider.idea.editor.actions.RunnableAssemblySelectorAction
import me.fornever.avaloniarider.idea.editor.actions.TogglePreviewerLogAction
import me.fornever.avaloniarider.previewer.AvaloniaPreviewerSessionController
import me.fornever.avaloniarider.ui.bindVisible
import java.awt.BorderLayout
import java.awt.GridBagLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

abstract class AvaloniaPreviewEditorBase(
    final override val project: Project,
    private val currentFile: VirtualFile,
    private val buildTaskThrottler: Lazy<BuildTaskThrottler>
) : UserDataHolderBase(), XamlPreviewEditor {

    constructor(project: Project, currentFile: VirtualFile) : this(
        project,
        currentFile,
        lazy { BuildTaskThrottler.getInstance(project) }
    )

    override val parentEditor: XamlSplitEditor? = null
    final override val toolbar: PreviewEditorToolbar? = null
    override val virtualFilePath: String = currentFile.path
    override val zoomFactorLive: IPropertyView<Double> = Property(1.0)

    override fun updateLayout() {
    }

    override fun getName() = "Avalonia Preview"
    override fun isValid() = true

    override fun getFile() = currentFile

    private val lifetimeDefinition = LifetimeDefinition()
    protected val lifetime: Lifetime = lifetimeDefinition

    private val mainComponentWrapper = JPanel(BorderLayout())

    private val isLogManuallyVisible = Property(false)
    private val isLogAutoVisible = Property(false)
    private val isLogVisible = isLogManuallyVisible.compose(isLogAutoVisible) { manual, auto -> manual || auto }
    private val isMainComponentVisible = Property(true)
    private val mainComponent = Property<JComponent?>(null).apply {
        advise(lifetime) { component ->
            for (i in mainComponentWrapper.componentCount - 1 downTo 0)
                mainComponentWrapper.remove(i)

            component?.let {
                mainComponentWrapper.add(it, BorderLayout.CENTER)
            }
        }
    }

    private val buildLabelMessage = AtomicProperty("")
    private val buildPanel = lazy {
        JPanel().apply {
            layout = GridBagLayout()
            add(
                panel {
                    row {
                        label("").apply { bindText(buildLabelMessage) }.align(Align.CENTER)
                    }
                    row {
                        link(AvaloniaRiderBundle.message("previewer.build-project")) {
                            val selectedProjectPath = selectedProjectPath.valueOrNull ?: return@link
                            lifetime.launchBackground {
                                val parameters = BuildParameters(BuildTarget(), listOf(selectedProjectPath.toString()))
                                buildTaskThrottler.value.buildSequentially(parameters)
                            }
                        }.align(Align.CENTER)
                    }
                }
            )
        }
    }

    private val consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).console

    private val assemblySelectorAction = RunnableAssemblySelectorAction(lifetime, project, currentFile)
    private val selectedProjectPath = assemblySelectorAction.selectedProjectPath
    protected val sessionController = AvaloniaPreviewerSessionController(project, lifetime, consoleView, file, selectedProjectPath)
    init {
        lifetime.launchOnUi {
            sessionController.status.nextValue { it == AvaloniaPreviewerSessionController.Status.Working }
            parentEditor?.triggerLayoutChange(XamlSplitEditorSplitLayout.SPLIT, requestFocus = false)
        }

        sessionController.status.advise(lifetime) { status ->
            UIUtil.invokeLaterIfNeeded {
                val isTerminated = status == AvaloniaPreviewerSessionController.Status.Terminated
                isLogAutoVisible.value = isTerminated
                isMainComponentVisible.value = !isTerminated

                when (status) {
                    is AvaloniaPreviewerSessionController.Status.NoOutputAssembly -> {
                        buildLabelMessage.set(
                            AvaloniaRiderBundle.message(
                                "previewer.no-output-assembly",
                                status.path
                            )
                        )
                        mainComponent.value = buildPanel.value
                    }

                    else -> {
                        mainComponent.value = editorComponent
                    }
                }
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
            add(Splitter(/* vertical = */ true).apply {
                firstComponent = mainComponentWrapper.apply {
                    bindVisible(lifetime, isMainComponentVisible)
                }
                secondComponent = consoleView.component.apply {
                    bindVisible(lifetime, isLogVisible)
                }
            }, BorderLayout.CENTER)
        }
    }

    final override fun getComponent() = component.value
    override fun getPreferredFocusedComponent() = editorComponent

    protected fun createToolbarComponent(vararg actions: AnAction): JComponent {
        val actionGroup = DefaultActionGroup().apply {
            add(assemblySelectorAction)
            add(RestartPreviewerAction(lifetime, sessionController, selectedProjectPath))
            addAll(*actions)
            add(TogglePreviewerLogAction(isLogManuallyVisible))
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
