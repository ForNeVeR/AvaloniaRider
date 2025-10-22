package me.fornever.avaloniarider.previewer

import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.createNestedDisposable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.Alarm
import com.intellij.util.application
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.SequentialLifetimes
import com.jetbrains.rd.util.lifetime.isAlive
import com.jetbrains.rd.util.reactive.*
import com.jetbrains.rd.util.threading.coroutines.async
import com.jetbrains.rd.util.threading.coroutines.launch
import com.jetbrains.rd.util.throttleLast
import com.jetbrains.rider.build.BuildHost
import com.jetbrains.rider.model.riderSolutionLifecycle
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import com.jetbrains.rider.projectView.workspace.containingProjectEntity
import com.jetbrains.rider.projectView.workspace.getProjectModelEntities
import com.jetbrains.rider.ui.SwingScheduler
import com.jetbrains.rider.ui.components.utils.documentChanged
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.fornever.avaloniarider.AvaloniaRiderBundle
import me.fornever.avaloniarider.controlmessages.AvaloniaInputEventMessage
import me.fornever.avaloniarider.controlmessages.FrameMessage
import me.fornever.avaloniarider.controlmessages.HtmlTransportStartedMessage
import me.fornever.avaloniarider.controlmessages.UpdateXamlResultMessage
import me.fornever.avaloniarider.exceptions.AvaloniaPreviewerExecutionException
import me.fornever.avaloniarider.exceptions.AvaloniaPreviewerInitializationException
import me.fornever.avaloniarider.idea.concurrency.adviseOnUiThread
import me.fornever.avaloniarider.idea.settings.AvaloniaPreviewerMethod
import me.fornever.avaloniarider.idea.settings.AvaloniaProjectSettings
import me.fornever.avaloniarider.rd.compose
import me.fornever.avaloniarider.rider.AvaloniaRiderProjectModelHost
import me.fornever.avaloniarider.rider.projectRelativeVirtualPath
import java.io.EOFException
import java.net.ServerSocket
import java.net.SocketException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * The sources on this class are thread-free. Make sure to schedule them onto the proper threads if necessary.
 */
class AvaloniaPreviewerSessionController(
    private val project: Project,
    outerLifetime: Lifetime,
    private val consoleView: ConsoleView?,
    private val xamlFile: VirtualFile,
    projectFilePathProperty: IOptPropertyView<Path>) {
    companion object {
        private val logger = Logger.getInstance(AvaloniaPreviewerSessionController::class.java)

        private val xamlEditThrottling = Duration.ofMillis(300L)
        private val projectPathRetryDelay = Duration.ofMillis(200L)
    }

    open class Status {
        /**
         * No sessions have been started so far.
         */
        object Idle : Status()

        /**
         * There's no program output assembly on disk.
         */
        data class NoOutputAssembly(val path: Path) : Status()

        /**
         * Trying to connect to a started previewer process.
         */
        object Connecting : Status()

        /**
         * Previewer process is working.
         */
        object Working : Status()

        /**
         * Previewer process has reported an XAML error.
         */
        object XamlError : Status()

        /**
         * Preview has been suspended (e.g., by an ongoing build session).
         */
        object Suspended : Status()

        /**
         * The preview process has been terminated, and no other process is present.
         */
        object Terminated : Status()
    }

    private val workspaceModel = WorkspaceModel.getInstance(project)

    private val statusProperty = Property<Status>(Status.Idle)

    val status: IPropertyView<Status> = statusProperty
    private val controllerLifetime = outerLifetime.createNested()

    private val htmlTransportStartedSignal = Signal<HtmlTransportStartedMessage>()
    private val frameSignal = Signal<FrameMessage>()
    private val updateXamlResultProperty = OptProperty<UpdateXamlResultMessage>()
    private val criticalErrorSignal = Signal<Throwable>()

    val htmlTransportStarted: ISource<HtmlTransportStartedMessage> = htmlTransportStartedSignal
    val frame: ISource<FrameMessage> = frameSignal
    val updateXamlResult: IOptPropertyView<UpdateXamlResultMessage> = updateXamlResultProperty
    val criticalError: ISource<Throwable> = criticalErrorSignal

    val dpi = OptProperty<Double>()
    val zoomFactor = Property(1.0)

    private val inFlightUpdate = Property(false)

    @Volatile
    private var session: AvaloniaPreviewerSession? = null
    @Volatile
    private var lastKnownProjectRelativePath: String? = null
    @Volatile
    private var lastProjectFilePath: Path? = null
    @Volatile
    private var pendingRestart = false

    private val sessionLifetimeSource = SequentialLifetimes(controllerLifetime)
    private var currentSessionLifetime: LifetimeDefinition? = null
    private val restartLifetime = controllerLifetime.createNestedDisposable(
        "AvaloniaPreviewerSessionController.restartLifetime"
    )
    private val restartAlarm = Alarm(restartLifetime)

    private val baseDocument: Document? =
        application.runReadAction<Document?> { FileDocumentManager.getInstance().getDocument(xamlFile) }

    init {
        val isBuildingProperty = BuildHost.getInstance(project).building
        compose(isBuildingProperty, projectFilePathProperty)
            .advise(controllerLifetime) { (isBuilding, projectFilePath) ->
                logger.info("Controller state change: isBuilding = $isBuilding, projectFilePath = $projectFilePath")
                if (isBuilding || projectFilePath == null) {
                    logger.info("Suspending preview for $xamlFile")
                    suspend()
                } else {
                    logger.info("Force start preview for $xamlFile")
                    start(projectFilePath, true)
                }
            }

        compose(status, inFlightUpdate, dpi, zoomFactor)
            .advise(controllerLifetime) { (status, inFlightUpdate, dpi, zoomFactor) ->
                if (status != Status.Working) return@advise
                if (inFlightUpdate) return@advise

                // Try to guess DPI if we got called without a signal from the control. After the control tells us the
                // right DPI, we'll apply it later.
                val effectiveDpi = dpi ?: (JBUIScale.sysScale().toDouble() * 96.0)
                session?.sendDpi(effectiveDpi * zoomFactor)
            }

        status.advise(controllerLifetime) { status ->
            logger.info("${xamlFile.name}: $status")
        }

        baseDocument?.documentChanged()
            ?.throttleLast(xamlEditThrottling, SwingScheduler)
            ?.advise(controllerLifetime) {
                if (session != null) return@advise
                if (!pendingRestart) return@advise
                val projectPath = lastProjectFilePath ?: return@advise
                scheduleRestart(projectPath)
            }
    }

    private suspend fun getProjectContainingFile(virtualFile: VirtualFile): ProjectModelEntity? {
        application.assertIsDispatchThread()

        val result = CompletableDeferred<ProjectModelEntity?>()

        project.solution.riderSolutionLifecycle.isProjectModelReady.adviseUntil(controllerLifetime) { isReady ->
            if (!isReady) return@adviseUntil false
            try {
                logger.debug { "Project model view synchronized" }
                val projectModelEntities = workspaceModel.getProjectModelEntities(virtualFile, project)
                logger.debug {
                    "Project model nodes for file $xamlFile: " + projectModelEntities.joinToString(", ")
                }
                val containingProject = projectModelEntities.asSequence()
                    .mapNotNull { it.containingProjectEntity() }
                    .firstOrNull()
                if (containingProject != null) {
                    result.complete(containingProject)
                } else {
                    logger.warn("Workspace model doesn't contain project entity for $virtualFile")
                    result.complete(null)
                }
            } catch (t: Throwable) {
                result.completeExceptionally(t)
            }

            return@adviseUntil true
        }

        return result.await()
    }

    private fun createSession(
        lifetime: Lifetime,
        socket: ServerSocket,
        parameters: AvaloniaPreviewerParameters,
        xamlFile: VirtualFile
    ) = AvaloniaPreviewerSession(
        lifetime,
        socket,
        parameters.xamlContainingAssemblyPath
    ).apply {
        sessionStarted.advise(lifetime) {
            statusProperty.value = Status.Working
            pendingRestart = false
            updateXamlResultProperty.set(UpdateXamlResultMessage())

            sendClientSupportedPixelFormat()

            var document: Document? = null
            application.runReadAction {
                document = FileDocumentManager.getInstance().getDocument(xamlFile)
            }
            if (document == null) {
                logger.warn("Unable to obtain document for $xamlFile")
                return@advise
            }
            val currentDocument = document!!

            val pathRetryDisposable = lifetime.createNestedDisposable(
                "AvaloniaPreviewerSessionController.projectPathRetry"
            )
            val pathRetryAlarm = Alarm(pathRetryDisposable)
            var pendingPathRetry = false

            fun cancelScheduledRetry() {
                if (!pendingPathRetry) return
                pendingPathRetry = false
                pathRetryAlarm.cancelAllRequests()
            }

            fun computeDocumentPathInProject(): String? {
                val projectModelItems = workspaceModel.getProjectModelEntities(xamlFile, project)
                val item = projectModelItems.firstOrNull()
                return item?.projectRelativeVirtualPath?.let { "/$it" }
            }

            lateinit var schedulePathRetry: () -> Unit

            fun dispatchXamlUpdate() {
                application.runReadAction {
                    val projectPath = computeDocumentPathInProject()
                    if (projectPath == null) {
                        logger.debug { "Project relative path is not yet available for $xamlFile" }
                    }

                    val effectivePath = projectPath ?: lastKnownProjectRelativePath
                    if (effectivePath == null) {
                        val message = "Skipping XAML update for ${xamlFile.name}: project path is unknown"
                        if (lastKnownProjectRelativePath == null) {
                            logger.warn(message)
                        } else {
                            logger.debug { message }
                        }
                        inFlightUpdate.value = false
                        schedulePathRetry()
                        return@runReadAction
                    }

                    lastKnownProjectRelativePath = effectivePath
                    cancelScheduledRetry()

                    inFlightUpdate.value = true
                    sendXamlUpdate(currentDocument.text, effectivePath)
                }
            }

            schedulePathRetry = retry@{
                if (pendingPathRetry) return@retry
                pendingPathRetry = true
                pathRetryAlarm.addRequest({
                    pendingPathRetry = false
                    if (!lifetime.isAlive) return@addRequest
                    dispatchXamlUpdate()
                }, projectPathRetryDelay.toMillis().toInt())
            }

            currentDocument.documentChanged()
                .throttleLast(xamlEditThrottling, SwingScheduler)
                .advise(lifetime) {
                    dispatchXamlUpdate()
                }
            dispatchXamlUpdate()
        }

        htmlTransportStarted.flowInto(lifetime, htmlTransportStartedSignal)
        updateXamlResult.adviseOnUiThread(lifetime) { message ->
            if (message.error != null) {
                statusProperty.value = Status.XamlError
                inFlightUpdate.value = false
            }
            updateXamlResultProperty.set(message)
        }
        frame.adviseOnUiThread(lifetime) { frame ->
            statusProperty.value = Status.Working // reset to the good status after a possible error
            frameSignal.fire(frame)
            inFlightUpdate.value = false
        }
    }

    private suspend fun executePreviewerAsync(
        lifetime: Lifetime,
        executionMode: ProcessExecutionMode,
        projectFilePath: Path
    ) {
        val settings = AvaloniaProjectSettings.getInstance(project).state

        statusProperty.set(Status.Connecting)

        logger.info("Receiving the containing project for the file $xamlFile")
        val xamlContainingProject = withContext(Dispatchers.EDT) { getProjectContainingFile(xamlFile) }
            ?: throw AvaloniaPreviewerInitializationException(
                AvaloniaRiderBundle.message("previewer.error.project-not-found", xamlFile.presentableUrl)
            )

        logger.info("Calculating a project output for the project $projectFilePath")
        val riderProjectOutputHost = AvaloniaRiderProjectModelHost.getInstance(project)
        val projectOutput = riderProjectOutputHost.getProjectOutput(lifetime, projectFilePath)

        val outputPath = Path.of(projectOutput.outputPath)
        val isOutputExists = withContext(Dispatchers.IO) { Files.exists(outputPath) }
        if (!isOutputExists) {
            logger.info("File \"${projectOutput.outputPath}\" not found.")
            statusProperty.value = Status.NoOutputAssembly(outputPath)
            return
        }

        logger.info("Calculating previewer start parameters for the project $projectFilePath, output $projectOutput")
        val msBuild = MsBuildParameterCollector.getInstance(project)
        val parameters = msBuild.getAvaloniaPreviewerParameters(
            projectFilePath,
            projectOutput,
            xamlContainingProject
        )

        val socket = withContext(Dispatchers.IO) {
            ServerSocket(0).apply {
                lifetime.onTermination { close() }
            }
        }
        val transport = PreviewerBsonTransport(socket.localPort)
        val method = when (settings.previewerMethod) {
            AvaloniaPreviewerMethod.AvaloniaRemote -> AvaloniaRemoteMethod
            AvaloniaPreviewerMethod.Html -> HtmlMethod
        }
        val sessionNestedLifetime = lifetime.createNested() // to terminate later than the process
        val newSession = createSession(sessionNestedLifetime, socket, parameters, xamlFile)
        val processTitle = "${xamlFile.name} (port ${socket.localPort})"
        val process = AvaloniaPreviewerProcess(project, lifetime, parameters)
        session = newSession

        val sessionJob = lifetime.async(Dispatchers.IO) {
            logger.info("Starting socket listener")
            try {
                newSession.processSocketMessages()
            } catch (ex: Exception) {
                when (ex) {
                    is SocketException, is EOFException -> {
                        // Log socket errors only if the session is still alive.
                        if (lifetime.isAlive) {
                            logger.info("Socket closed while session is still alive", ex)
                        }
                    }
                    else -> throw ex
                }
            }
        }
        val processJob = lifetime.async {
            logger.info("Starting previewer process")
            process.run(executionMode, consoleView, transport, method, processTitle)
        }

        val processResult = processJob.await()
        sessionJob.await()
        val exitCode = processResult.exitCode
        if (exitCode != null && exitCode != 0) {
            throw AvaloniaPreviewerExecutionException(exitCode, processResult.outputSnippet)
        }
    }

    fun start(
        projectFilePath: Path,
        force: Boolean = false,
        executionMode: ProcessExecutionMode = ProcessExecutionMode.Run
    ) {
        lastProjectFilePath = projectFilePath
        if (status.value == Status.Suspended && !force) return

        lastKnownProjectRelativePath = null
        pendingRestart = false

        val lt = sessionLifetimeSource.next()
        currentSessionLifetime = lt
        lt.launch {
            try {
                executePreviewerAsync(currentSessionLifetime!!, executionMode, projectFilePath)
            } catch (ex: AvaloniaPreviewerExecutionException) {
                val errorText = ex.processOutput?.ifBlank { null } ?: ex.message
                if (errorText != null) {
                    updateXamlResultProperty.set(UpdateXamlResultMessage(error = errorText))
                }
                statusProperty.value = Status.XamlError
                inFlightUpdate.value = false
                pendingRestart = true
                logger.warn(ex)
            } catch (ex: AvaloniaPreviewerInitializationException) {
                criticalErrorSignal.fire(ex)
                logger.warn(ex)
            } catch (x: CancellationException) {
                logger.info("${xamlFile.name}: previewer session has been cancelled")
                throw x
            } catch (t: Throwable) {
                logger.error(t)
            } finally {
                session = null
                lt.terminate()

                logger.info("Previewer session is terminated.")
                when (statusProperty.value) {
                    Status.Suspended,
                    Status.XamlError,
                    is Status.NoOutputAssembly -> {}
                    else -> statusProperty.set(Status.Terminated)
                }
            }
        }
    }

    private fun suspend() {
        application.assertIsDispatchThread()

        statusProperty.set(Status.Suspended)
        currentSessionLifetime?.terminate()
    }

    fun acknowledgeFrame(frame: FrameMessage) {
        session?.sendFrameAcknowledgement(frame)
    }

    fun sendInputEventMessage(event: AvaloniaInputEventMessage) {
        session?.sendInputEventMessage(event)
    }

    private fun scheduleRestart(projectFilePath: Path) {
        restartAlarm.cancelAllRequests()
        restartAlarm.addRequest({
            if (session != null) return@addRequest
            logger.info("Restarting previewer for $xamlFile after document change")
            start(projectFilePath, force = true)
        }, projectPathRetryDelay.toMillis().toInt())
    }
}
