package me.fornever.avaloniarider.previewer

import com.intellij.application.ApplicationThreadPool
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.onTermination
import com.jetbrains.rd.util.reactive.*
import com.jetbrains.rd.util.throttleLast
import com.jetbrains.rider.projectView.ProjectModelViewHost
import com.jetbrains.rider.projectView.nodes.ProjectModelNode
import com.jetbrains.rider.projectView.nodes.containingProject
import com.jetbrains.rider.ui.SwingScheduler
import com.jetbrains.rider.ui.components.utils.documentChanged
import com.jetbrains.rider.util.idea.application
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import me.fornever.avaloniarider.controlmessages.FrameMessage
import me.fornever.avaloniarider.controlmessages.RequestViewportResizeMessage
import me.fornever.avaloniarider.idea.concurrency.ApplicationAnyModality
import me.fornever.avaloniarider.rider.RiderProjectOutputHost
import me.fornever.avaloniarider.rider.projectRelativeVirtualPath
import java.net.ServerSocket
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
        Idle,
        Connecting,
        Working,
        XamlError,
        Terminated
    }

    private val statusProperty = Property(Status.Idle)

    val status: IPropertyView<Status> = statusProperty
    private val lifetime = outerLifetime.createNested()

    private val requestViewportResizeSignal = Signal<RequestViewportResizeMessage>()
    private val frameSignal = Signal<FrameMessage>()
    private val errorMessageProperty = Property<String?>(null)

    val requestViewportResize: ISource<RequestViewportResizeMessage> = requestViewportResizeSignal
    val frame: ISource<FrameMessage> = frameSignal
    val errorMessage: IPropertyView<String?> = errorMessageProperty

    lateinit var session: AvaloniaPreviewerSession

    init {
        lifetime.onTermination { statusProperty.set(Status.Terminated) }
    }

    private suspend fun getContainingProject(xamlFile: VirtualFile): ProjectModelNode {
        val result = CompletableDeferred<ProjectModelNode>()
        val projectModelViewHost = ProjectModelViewHost.getInstance(project)
        projectModelViewHost.view.sync.adviseNotNullOnce(lifetime) {
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

        requestViewportResize.flowInto(lifetime, requestViewportResizeSignal)
        frame.flowInto(lifetime, frameSignal)
    }

    private suspend fun executePreviewerAsync(xamlFile: VirtualFile) {
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
        val process = AvaloniaPreviewerProcess(lifetime, parameters, socket.localPort)
        session = createSession(socket, parameters, xamlFile)

        val sessionJob = GlobalScope.async {
            logger.info("Starting socket listener")
            session.processSocketMessages()
        }
        val processJob = GlobalScope.async {
            logger.info("Starting previewer process")
            process.run(project)
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
            try {
                executePreviewerAsync(xamlFile)
            } catch (t: Throwable) {
                logger.error(t)
            }

            lifetime.terminate()
        }
    }

    fun acknowledgeFrame(frame: FrameMessage) {
        session.sendFrameAcknowledgement(frame)
    }
}
