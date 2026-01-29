package me.fornever.avaloniarider.idea.editor

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.WindowWrapper
import com.intellij.openapi.ui.WindowWrapperBuilder
import com.intellij.openapi.util.BooleanGetter
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.DimensionService
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.application
import com.intellij.util.ui.UIUtil
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.reactive.IPropertyView
import com.jetbrains.rd.util.reactive.Property
import com.jetbrains.rd.util.reactive.compose
import com.jetbrains.rd.util.threading.coroutines.launch
import com.jetbrains.rd.util.threading.coroutines.nextValue
import com.jetbrains.rider.build.BuildParameters
import com.jetbrains.rider.build.tasks.BuildTaskThrottler
import com.jetbrains.rider.model.BuildTarget
import com.jetbrains.rider.xaml.core.XamlPreviewEditor
import com.jetbrains.rider.xaml.previewEditor.PreviewEditorToolbar
import com.jetbrains.rider.xaml.splitEditor.XamlSplitEditor
import com.jetbrains.rider.xaml.splitEditor.XamlSplitEditorSplitLayout
import kotlinx.coroutines.Dispatchers
import me.fornever.avaloniarider.AvaloniaRiderBundle
import me.fornever.avaloniarider.idea.editor.actions.*
import me.fornever.avaloniarider.previewer.AvaloniaPreviewerSessionController
import me.fornever.avaloniarider.ui.bindVisible
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagLayout
import java.awt.Window
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

abstract class AvaloniaPreviewEditorBase(
    final override val project: Project,
    private val currentFile: VirtualFile,
    private val buildTaskThrottler: Lazy<BuildTaskThrottler>,
    override val parentEditor: XamlSplitEditor? = null
) : UserDataHolderBase(), XamlPreviewEditor {

    constructor(project: Project, currentFile: VirtualFile, parentEditor: XamlSplitEditor? = null) : this(
        project,
        currentFile,
        lazy { BuildTaskThrottler.getInstance(project) },
        parentEditor
    )
    final override val toolbar: PreviewEditorToolbar? = null
    override val virtualFilePath: String = currentFile.path
    override val zoomFactorLive: IPropertyView<Double> = Property(1.0)

    override fun updateLayout() {
    }

    override fun getName() = AvaloniaRiderBundle.message("previewer.editor.name")
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
            mainComponentWrapper.revalidate()
            mainComponentWrapper.repaint()
        }
    }

    private var detachedWindow: WindowWrapper? = null
    private val isPreviewDetached = Property(false)
    private val detachedWindowDimensionKey = "AvaloniaPreviewer.DetachedWindow.${currentFile.path}"

    private val detachedPlaceholderPanel = lazy {
        JPanel().apply {
            layout = GridBagLayout()
            add(
                panel {
                    row {
                        label(AvaloniaRiderBundle.message("previewer.detached.message"))
                            .align(Align.CENTER)
                    }
                    row {
                        link(AvaloniaRiderBundle.message("previewer.detached.return")) {
                            attachPreviewToEditor()
                        }.align(Align.CENTER)
                    }
                }
            )
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
                            lifetime.launch {
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
    private val baseDocument: Document? =
        application.runReadAction<Document?> { FileDocumentManager.getInstance().getDocument(file) }
    protected val sessionController = AvaloniaPreviewerSessionController(
        project,
        lifetime,
        consoleView,
        file,
        selectedProjectPath,
        baseDocument
    )
    init {
        lifetime.launch(Dispatchers.EDT) {
            sessionController.status.nextValue { it == AvaloniaPreviewerSessionController.Status.Working }
            parentEditor?.triggerLayoutChange(XamlSplitEditorSplitLayout.SPLIT, requestFocus = false)
        }

        lifetime.onTermination {
            UIUtil.invokeLaterIfNeeded {
                saveDetachedWindowState(detachedWindow?.window)
                detachedWindow?.dispose()
                detachedWindow = null
            }
        }

        isPreviewDetached.advise(lifetime) {
            UIUtil.invokeLaterIfNeeded {
                updateMainComponentForDetachState()
            }
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
                        updateMainComponentForDetachState()
                    }
                }
            }
        }
    }

    private fun updateMainComponentForDetachState() {
        mainComponent.value = if (isPreviewDetached.value) {
            detachedPlaceholderPanel.value
        } else {
            editorComponent
        }
    }

    internal fun isPreviewDetached() = isPreviewDetached.value

    internal fun detachPreviewToWindow() {
        if (isPreviewDetached.value) return

        UIUtil.invokeLaterIfNeeded {
            val windowTitle = AvaloniaRiderBundle.message("previewer.detached.window-title", currentFile.name)

            // Remove editor component from current parent
            editorComponent.parent?.remove(editorComponent)

            // Create a content panel that contains both toolbar and preview
            val contentPanel = JPanel().apply {
                layout = BorderLayout()

                // Create toolbar for the detached window (without detach action)
                val toolbarPanel = JPanel().apply {
                    layout = BorderLayout()
                    add(createDetachedWindowToolbar(editorComponent), BorderLayout.LINE_END)
                }
                add(toolbarPanel, BorderLayout.PAGE_START)
                add(editorComponent, BorderLayout.CENTER)
            }

            var windowWrapper: WindowWrapper? = null
            val wrapper = WindowWrapperBuilder(WindowWrapper.Mode.FRAME, contentPanel)
                .setProject(project)
                .setTitle(windowTitle)
                .setPreferredFocusedComponent(editorComponent)
                .setOnCloseHandler(BooleanGetter {
                    saveDetachedWindowState(windowWrapper?.window)
                    attachPreviewToEditor(closeWindow = false)
                    true
                })
                .build()
            windowWrapper = wrapper
            detachedWindow = wrapper
            isPreviewDetached.value = true

            // Switch to "Editor only" mode to maximize useful space
            parentEditor?.triggerLayoutChange(XamlSplitEditorSplitLayout.EDITOR_ONLY, requestFocus = false)

            wrapper.show()

            val window = wrapper.window
            window.minimumSize = Dimension(320, 240)
            
            // Ensure the window has decorations (title bar) - fixes regression on Linux
            if (window is javax.swing.JFrame) {
                window.isUndecorated = false
                window.type = Window.Type.NORMAL
            }
            
            restoreDetachedWindowState(window)
            window.toFront()
        }
    }

    internal fun attachPreviewToEditor(closeWindow: Boolean = true) {
        if (!isPreviewDetached.value) return

        UIUtil.invokeLaterIfNeeded {
            val wrapper = detachedWindow
            saveDetachedWindowState(wrapper?.window)
            detachedWindow = null

            editorComponent.parent?.remove(editorComponent)
            isPreviewDetached.value = false

            // The mainComponent property observer will handle re-adding to mainComponentWrapper
            mainComponent.value = editorComponent

            // Restore split mode to show the preview in editor
            parentEditor?.triggerLayoutChange(XamlSplitEditorSplitLayout.SPLIT, requestFocus = false)

            if (closeWindow) {
                wrapper?.close()
            }
        }
    }

    protected abstract fun createToolbar(targetComponent: JComponent): JComponent
    protected abstract val editorComponent: JComponent
    protected abstract fun getExtraActions(): Array<AnAction>

    private val component = lazy {
        JPanel().apply {
            val rootPanel = this
            layout = BorderLayout()

            val toolbarPanel = JPanel().apply {
                layout = BorderLayout()
                add(createToolbar(rootPanel), BorderLayout.LINE_END)
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

    protected fun createToolbarComponent(targetComponent: JComponent, includeDetachAction: Boolean, vararg actions: AnAction): JComponent {
        val actionGroup = DefaultActionGroup()
        val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, actionGroup, true).apply {
            this.targetComponent = targetComponent
        }

        actionGroup.apply {
            add(getShowErrorAction(toolbar))
            add(assemblySelectorAction)
            add(RestartPreviewerAction(lifetime, sessionController, selectedProjectPath))
            if (includeDetachAction) {
                add(ToggleDetachedPreviewAction(this@AvaloniaPreviewEditorBase))
            }
            addAll(*actions)
            add(TogglePreviewerLogAction(isLogManuallyVisible))
            add(DebugPreviewerAction(lifetime, sessionController, selectedProjectPath))
        }

        return toolbar.component
    }

    protected fun createToolbarComponent(targetComponent: JComponent, vararg actions: AnAction): JComponent =
        createToolbarComponent(targetComponent, true, *actions)

    private fun createDetachedWindowToolbar(targetComponent: JComponent): JComponent =
        createToolbarComponent(targetComponent, false, *getExtraActions())

    override fun isModified() = false
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun setState(state: FileEditorState) {}
    override fun selectNotify() {}
    override fun deselectNotify() {}
    override fun getCurrentLocation(): FileEditorLocation? = null
    override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? = null
    override fun dispose() {
        saveDetachedWindowState(detachedWindow?.window)
        detachedWindow?.dispose()
        detachedWindow = null
        lifetimeDefinition.terminate()
    }

    private fun getShowErrorAction(toolbar: ActionToolbar) =
        ShowErrorAction(
            lifetime,
            getErrorMessageProperty(sessionController.updateXamlResult)
        ) {
            toolbar.updateActionsAsync()
        }

    private fun restoreDetachedWindowState(window: Window) {
        val dimensionService = DimensionService.getInstance()
        val savedSize = dimensionService.getSize(detachedWindowDimensionKey, project)
        val savedLocation = dimensionService.getLocation(detachedWindowDimensionKey, project)

        if (savedSize != null) {
            window.size = savedSize
        } else {
            window.size = Dimension(800, 600)
        }

        if (savedLocation != null) {
            window.location = savedLocation
        } else {
            val parentWindow = WindowManager.getInstance().suggestParentWindow(project)
            window.setLocationRelativeTo(parentWindow)
        }
    }

    private fun saveDetachedWindowState(window: Window?) {
        if (window == null) return
        val dimensionService = DimensionService.getInstance()
        dimensionService.setSize(detachedWindowDimensionKey, window.size, project)
        dimensionService.setLocation(detachedWindowDimensionKey, window.location, project)
    }
}
