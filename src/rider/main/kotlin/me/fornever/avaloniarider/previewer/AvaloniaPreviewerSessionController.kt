package me.fornever.avaloniarider.previewer

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.util.launchLongBackground
import com.intellij.openapi.rd.util.startIOBackgroundAsync
import com.intellij.openapi.rd.util.withIOBackgroundContext
import com.intellij.openapi.rd.util.withUiContext
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.application
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.jetbrains.rd.util.lifetime.*
import com.jetbrains.rd.util.reactive.*
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
import kotlinx.coroutines.selects.select
import me.fornever.avaloniarider.controlmessages.*
import me.fornever.avaloniarider.exceptions.AvaloniaPreviewerInitializationException
import me.fornever.avaloniarider.idea.concurrency.adviseOnUiThread
import me.fornever.avaloniarider.idea.settings.AvaloniaPreviewerMethod
import me.fornever.avaloniarider.idea.settings.AvaloniaSettings
import me.fornever.avaloniarider.rd.compose
import me.fornever.avaloniarider.rider.AvaloniaRiderProjectModelHost
import me.fornever.avaloniarider.rider.projectRelativeVirtualPath
import me.fornever.avaloniarider.statistics.PreviewerUsageLogger
import java.io.EOFException
import java.net.ServerSocket
import java.net.SocketException
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The sources on this class are thread-free. Make sure to schedule them onto the proper threads if necessary.
 */
@Suppress("UnstableApiUsage")
class AvaloniaPreviewerSessionController(
    private val project: Project,
    outerLifetime: Lifetime,
    private val xamlFile: VirtualFile,
    projectFilePathProperty: IOptPropertyView<Path>) {
    companion object {
        private val logger = Logger.getInstance(AvaloniaPreviewerSessionController::class.java)

        private val xamlEditThrottling = Duration.ofMillis(300L)
    }

    enum class Status {
        /**
         * No sessions have been started so far.
         */
        Idle,

        /**
         * Trying to connect to a started previewer process.
         */
        Connecting,

        /**
         * Previewer process is working.
         */
        Working,

        /**
         * Previewer process has reported a XAML error.
         */
        XamlError,

        /**
         * Preview has been suspended (e.g. by an ongoing build session).
         */
        Suspended,

        /**
         * Preview process has been terminated, and no other process is present.
         */
        Terminated
    }

    private val previewReported = AtomicBoolean()
    private val workspaceModel = WorkspaceModel.getInstance(project)

    private val statusProperty = Property(Status.Idle)

    val status: IPropertyView<Status> = statusProperty
    private val controllerLifetime = outerLifetime.createNested()

    private val htmlTransportStartedSignal = Signal<HtmlTransportStartedMessage>()
    private val requestViewportResizeSignal = Signal<RequestViewportResizeMessage>()
    private val frameSignal = Signal<FrameMessage>()
    private val updateXamlResultSignal = Signal<UpdateXamlResultMessage>()
    private val criticalErrorSignal = Signal<Throwable>()

    val htmlTransportStarted: ISource<HtmlTransportStartedMessage> = htmlTransportStartedSignal
    val requestViewportResize: ISource<RequestViewportResizeMessage> = requestViewportResizeSignal
    val frame: ISource<FrameMessage> = frameSignal
    val updateXamlResult: ISource<UpdateXamlResultMessage> = updateXamlResultSignal
    val criticalError: ISource<Throwable> = criticalErrorSignal

    val dpi = OptProperty<Double>()
    val zoomFactor = Property(1.0)

    private val inFlightUpdate = Property(false)

    private var _session: AvaloniaPreviewerSession? = null
    private var session: AvaloniaPreviewerSession?
        get() = application.runReadAction(Computable { _session })
        set(value) = WriteCommandAction.runWriteCommandAction(project) { _session = value }

    private val sessionLifetimeSource = SequentialLifetimes(controllerLifetime)
    private var currentSessionLifetime: LifetimeDefinition? = null

    private fun enableStatistics() {
        val statisticsReporterLifetime = controllerLifetime.createNested()

        fun report(action: () -> Unit) {
            if (previewReported.getAndSet(true)) return
            action()
            statisticsReporterLifetime.terminate(true)
        }

        status.advise(statisticsReporterLifetime) { status ->
            when (status) {
                Status.XamlError -> report { PreviewerUsageLogger.logPreviewError(project) }
                Status.Terminated -> report { PreviewerUsageLogger.logPreviewCriticalFailure(project) }
                else -> {}
            }
        }
        frame.advise(statisticsReporterLifetime) {
            report { PreviewerUsageLogger.logPreviewSuccess(project) }
        }
    }

    init {
        controllerLifetime.onTermination { statusProperty.set(Status.Terminated) }

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
                val effectiveDpi = dpi ?: JBUIScale.sysScale().toDouble()
                session?.sendDpi(effectiveDpi * zoomFactor)
            }

        enableStatistics()
    }

    private suspend fun getProjectContainingFile(virtualFile: VirtualFile): ProjectModelEntity {
        application.assertIsDispatchThread()

        val result = CompletableDeferred<ProjectModelEntity>()

        project.solution.riderSolutionLifecycle.isProjectModelReady.adviseUntil(controllerLifetime) { isReady ->
            if (!isReady) return@adviseUntil false
            try {
                logger.debug { "Project model view synchronized" }
                val projectModelEntities = workspaceModel.getProjectModelEntities(virtualFile, project)
                logger.debug {
                    "Project model nodes for file $xamlFile: " + projectModelEntities.joinToString(", ")
                }
                val containingProject = projectModelEntities.asSequence()
                    .map { it.containingProjectEntity() }
                    .filterNotNull()
                    .first()
                result.complete(containingProject)
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

            sendClientSupportedPixelFormat()

            application.runReadAction {
                val document = FileDocumentManager.getInstance().getDocument(xamlFile)!!

                fun getDocumentPathInProject(): String {
                    val projectModelItems = workspaceModel.getProjectModelEntities(xamlFile, project)
                    val item = projectModelItems.firstOrNull()
                    return item?.projectRelativeVirtualPath?.let { "/$it" } ?: ""
                }

                fun sendXamlUpdate() {
                    inFlightUpdate.value = true
                    sendXamlUpdate(document.text, getDocumentPathInProject())
                }

                document.documentChanged()
                    .throttleLast(xamlEditThrottling, SwingScheduler)
                    .advise(lifetime) {
                        sendXamlUpdate()
                    }
                sendXamlUpdate()
            }
        }

        htmlTransportStarted.flowInto(lifetime, htmlTransportStartedSignal)
        requestViewportResize.flowInto(lifetime, requestViewportResizeSignal)
        updateXamlResult.adviseOnUiThread(lifetime) { message ->
            if (message.error != null) {
                statusProperty.value = Status.XamlError
                inFlightUpdate.value = false
            }
            updateXamlResultSignal.fire(message)
        }
        frame.adviseOnUiThread(lifetime) { frame ->
            frameSignal.fire(frame)
            inFlightUpdate.value = false
        }
    }

    private suspend fun executePreviewerAsync(lifetime: Lifetime, projectFilePath: Path) {
        val settings = AvaloniaSettings.getInstance(project).state

        statusProperty.set(Status.Connecting)

        logger.info("Receiving the containing project for the file $xamlFile")
        val xamlContainingProject = withUiContext(lifetime) { getProjectContainingFile(xamlFile) }

        logger.info("Calculating a project output for the project $projectFilePath")
        val riderProjectOutputHost = AvaloniaRiderProjectModelHost.getInstance(project)
        val projectOutput = riderProjectOutputHost.getProjectOutput(lifetime, projectFilePath)

        logger.info("Calculating previewer start parameters for the project $projectFilePath, output $projectOutput")
        val msBuild = MsBuildParameterCollector.getInstance(project)
        val parameters = msBuild.getAvaloniaPreviewerParameters(
            projectFilePath,
            projectOutput,
            xamlContainingProject
        )

        val socket = withIOBackgroundContext(lifetime) {
            @Suppress("BlockingMethodInNonBlockingContext")
            ServerSocket(0).apply {
                lifetime.onTermination { close() }
            }
        }
        val transport = PreviewerBsonTransport(socket.localPort)
        val method = when (settings.previewerMethod) {
            AvaloniaPreviewerMethod.AvaloniaRemote -> AvaloniaRemoteMethod
            AvaloniaPreviewerMethod.Html -> HtmlMethod
        }
        val processTitle = "${xamlFile.name} (port ${socket.localPort})"
        val process = AvaloniaPreviewerProcess(lifetime, parameters)
        val newSession = createSession(lifetime, socket, parameters, xamlFile)
        session = newSession

        val sessionJob = lifetime.startIOBackgroundAsync {
            logger.info("Starting socket listener")
            try {
                newSession.processSocketMessages()
            } catch (ex: Exception) {
                when (ex) {
                    is SocketException, is EOFException -> {
                        // Log socket errors only if the session is still alive.
                        if (lifetime.isAlive) {
                            logger.error("Socket error while session is still alive", ex)
                        }
                    }
                    else -> throw ex
                }
            }
        }
        val processJob = lifetime.startIOBackgroundAsync {
            logger.info("Starting previewer process")
            process.run(lifetime, project, transport, method, processTitle)
        }

        val result = select<String> {
            sessionJob.onAwait { "Socket listener" }
            processJob.onAwait { "Process" }
        }

        logger.info("$result has been terminated first")
    }

    fun start(projectFilePath: Path, force: Boolean = false) {
        if (status.value == Status.Suspended && !force) return

        val lt = sessionLifetimeSource.next()
        currentSessionLifetime = lt
        lt.launchLongBackground {
            try {
                executePreviewerAsync(currentSessionLifetime!!, projectFilePath)
            } catch (ex: AvaloniaPreviewerInitializationException) {
                criticalErrorSignal.fire(ex)
                logger.warn(ex)
            } catch (ex: CancellationException) {
                logger.info("${xamlFile.name}: previewer session has been cancelled")
            } catch (t: Throwable) {
                logger.error(t)
            } finally {
                session = null
                lt.terminate()
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
}
