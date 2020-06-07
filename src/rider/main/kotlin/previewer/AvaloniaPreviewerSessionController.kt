package me.fornever.avaloniarider.previewer

import com.intellij.application.ApplicationThreadPool
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.rd.platform.util.application
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.SequentialLifetimes
import com.jetbrains.rd.util.lifetime.isAlive
import com.jetbrains.rd.util.lifetime.onTermination
import com.jetbrains.rd.util.reactive.*
import com.jetbrains.rd.util.throttleLast
import com.jetbrains.rider.projectView.ProjectModelViewHost
import com.jetbrains.rider.projectView.nodes.ProjectModelNode
import com.jetbrains.rider.projectView.nodes.containingProject
import com.jetbrains.rider.ui.SwingScheduler
import com.jetbrains.rider.ui.components.utils.documentChanged
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import me.fornever.avaloniarider.controlmessages.FrameMessage
import me.fornever.avaloniarider.controlmessages.HtmlTransportStartedMessage
import me.fornever.avaloniarider.controlmessages.RequestViewportResizeMessage
import me.fornever.avaloniarider.idea.concurrency.ApplicationAnyModality
import me.fornever.avaloniarider.idea.settings.AvaloniaPreviewerMethod
import me.fornever.avaloniarider.idea.settings.AvaloniaSettings
import me.fornever.avaloniarider.rider.RiderProjectOutputHost
import me.fornever.avaloniarider.rider.projectRelativeVirtualPath
import java.net.ServerSocket
import java.net.SocketException
import java.time.Duration

/**
 * The sources on this class are thread-free. Make sure to schedule them onto the proper threads if necessary.
 */
class AvaloniaPreviewerSessionController(private val project: Project, outerLifetime: Lifetime) {
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

    private val statusProperty = Property(Status.Idle)

    val status: IPropertyView<Status> = statusProperty
    private val controllerLifetime = outerLifetime.createNested()

    private val htmlTransportStartedSignal = Signal<HtmlTransportStartedMessage>()
    private val requestViewportResizeSignal = Signal<RequestViewportResizeMessage>()
    private val frameSignal = Signal<FrameMessage>()
    private val errorMessageProperty = Property<String?>(null)

    val htmlTransportStarted: ISource<HtmlTransportStartedMessage> = htmlTransportStartedSignal
    val requestViewportResize: ISource<RequestViewportResizeMessage> = requestViewportResizeSignal
    val frame: ISource<FrameMessage> = frameSignal
    val errorMessage: IPropertyView<String?> = errorMessageProperty

    private var _session: AvaloniaPreviewerSession? = null
    private var session: AvaloniaPreviewerSession?
        get() = application.runReadAction(Computable { _session })
        set(value) = WriteCommandAction.runWriteCommandAction(project) { _session = value }

    private val sessionLifetimeSource = SequentialLifetimes(controllerLifetime)

    init {
        controllerLifetime.onTermination { statusProperty.set(Status.Terminated) }
    }

    private suspend fun getContainingProject(xamlFile: VirtualFile): ProjectModelNode {
        val result = CompletableDeferred<ProjectModelNode>()
        val projectModelViewHost = ProjectModelViewHost.getInstance(project)
        projectModelViewHost.view.sync.adviseNotNullOnce(controllerLifetime) {
            try {
                logger.debug { "Project model view synchronized" }
                val projectModelNodes = projectModelViewHost.getItemsByVirtualFile(xamlFile)
                logger.debug {
                    "Project model nodes for file $xamlFile: " + projectModelNodes.joinToString(", ")
                }
                val containingProject = projectModelNodes.asSequence()
                    .map { it.containingProject() }
                    .filterNotNull()
                    .first()
                result.complete(containingProject)
            } catch (t: Throwable) {
                result.completeExceptionally(t)
            }
        }
        return result.await()
    }

    private fun createSession(
        lifetime: Lifetime,
        socket: ServerSocket,
        parameters: AvaloniaPreviewerParameters,
        xamlFile: VirtualFile
    ) = AvaloniaPreviewerSession(
        socket,
        parameters.targetPath
    ).apply {
        sessionStarted.advise(lifetime) {
            sendClientSupportedPixelFormat()
            sendDpi(96.0) // TODO[F]: Properly acquire from the UI side (#9)

            application.runReadAction {
                val document = FileDocumentManager.getInstance().getDocument(xamlFile)!!

                fun getDocumentPathInProject(): String {
                    val projectModelViewHost = ProjectModelViewHost.getInstance(project)
                    val projectModelItems = projectModelViewHost.getItemsByVirtualFile(xamlFile)
                    val item = projectModelItems.firstOrNull()
                    return item?.projectRelativeVirtualPath?.let { "/$it" } ?: ""
                }

                document.documentChanged()
                    .throttleLast(xamlEditThrottling, SwingScheduler)
                    .advise(lifetime) {
                        sendXamlUpdate(document.text, getDocumentPathInProject())
                    }
                sendXamlUpdate(document.text, getDocumentPathInProject())
            }
        }

        htmlTransportStarted.flowInto(lifetime, htmlTransportStartedSignal)
        requestViewportResize.flowInto(lifetime, requestViewportResizeSignal)
        frame.flowInto(lifetime, frameSignal)
    }

    private suspend fun executePreviewerAsync(lifetime: Lifetime, xamlFile: VirtualFile) {
        statusProperty.set(Status.Connecting)
        logger.info("Receiving a containing project for the file $xamlFile")
        val containingProject = withContext(Dispatchers.ApplicationAnyModality) { getContainingProject(xamlFile) }

        logger.info("Calculating a project output for the project ${containingProject.name}")
        val riderProjectOutputHost = RiderProjectOutputHost.getInstance(project)
        val projectOutput = riderProjectOutputHost.getProjectOutput(lifetime, containingProject)

        logger.info("Calculating previewer start parameters for the project ${containingProject.name}, output $projectOutput")
        val msBuild = MsBuildParameterCollector.getInstance(project)
        val parameters = msBuild.getAvaloniaPreviewerParameters(containingProject, projectOutput)

        val socket = withContext(Dispatchers.IO) {
            ServerSocket(0).apply {
                lifetime.onTermination { close() }
            }
        }
        val transport = PreviewerBsonTransport(socket.localPort)
        val settings = AvaloniaSettings.getInstance(project).state
        val method = when (settings.previewerMethod) {
            AvaloniaPreviewerMethod.AvaloniaRemote -> AvaloniaRemoteMethod
            AvaloniaPreviewerMethod.Html -> HtmlMethod
        }
        val process = AvaloniaPreviewerProcess(lifetime, parameters)
        val newSession = createSession(lifetime, socket, parameters, xamlFile)
        session = newSession

        val sessionJob = GlobalScope.async {
            logger.info("Starting socket listener")
            try {
                newSession.processSocketMessages()
            } catch (ex: SocketException) {
                // Log socket errors only if the session is still alive.
                if (lifetime.isAlive) {
                    logger.error("Socket error while session is still alive", ex)
                }
            }
        }
        val processJob = GlobalScope.async {
            logger.info("Starting previewer process")
            process.run(project, transport, method)
        }
        statusProperty.set(Status.Working)

        val result = select<String> {
            sessionJob.onAwait { "Socket listener" }
            processJob.onAwait { "Process" }
        }

        logger.info("$result has been terminated first")
    }

    fun start(xamlFile: VirtualFile) {
        @Suppress("UnstableApiUsage")
        GlobalScope.launch(Dispatchers.ApplicationThreadPool) {
            val sessionLifetime = sessionLifetimeSource.next()
            try {
                executePreviewerAsync(sessionLifetime, xamlFile)
            } catch (t: Throwable) {
                logger.error(t)
            } finally {
                session = null
                sessionLifetime.terminate()
            }
        }
    }

    fun acknowledgeFrame(frame: FrameMessage) {
        session?.sendFrameAcknowledgement(frame)
    }
}
