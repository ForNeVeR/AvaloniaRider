package me.fornever.avaloniarider.previewer

import com.intellij.application.ApplicationThreadPool
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.onTermination
import com.jetbrains.rd.util.reactive.*
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.ui.components.utils.documentChanged
import com.jetbrains.rider.util.idea.application
import kotlinx.coroutines.*
import me.fornever.avaloniarider.controlmessages.ClientViewportAllocatedMessage
import me.fornever.avaloniarider.controlmessages.FrameMessage
import me.fornever.avaloniarider.controlmessages.RequestViewportResizeMessage
import me.fornever.avaloniarider.idea.concurrency.ApplicationAnyModality
import java.net.ServerSocket

/**
 * The sources on this class are thread-free. Make sure to schedule them onto the proper threads if necessary.
 */
class AvaloniaPreviewerSessionController(private val project: Project, outerLifetime: Lifetime) {
    companion object {
        private val logger = Logger.getInstance(AvaloniaPreviewerSessionController::class.java)
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

    private suspend fun getRunnableProject(xamlFile: VirtualFile): RunnableProject {
        val result = CompletableDeferred<RunnableProject>()
        project.solution.runnableProjectsModel.projects.adviseNotNullOnce(lifetime) {
            result.complete(it.first()) // TODO[F]: Actually extract the runnable project for the current file
        }
        return result.await()
    }

    private fun createSession(
        socket: ServerSocket,
        parameters: AvaloniaPreviewerParameters,
        xamlFile: VirtualFile
    ) = AvaloniaPreviewerSession(
        lifetime,
        socket,
        parameters.targetPath
    ).apply {
        sessionStarted.advise(lifetime) {
            sendClientSupportedPixelFormat()
            sendDpi(96.0) // TODO[F]: Properly acquire from the UI side

            application.runReadAction {
                val document = FileDocumentManager.getInstance().getDocument(xamlFile)!!
                document.documentChanged().advise(lifetime) {
                    sendXamlUpdate(document.text) // TODO[F]: Add throttling
                }
                sendXamlUpdate(document.text)
            }
        }

        requestViewportResize.flowInto(lifetime, requestViewportResizeSignal)
        frame.flowInto(lifetime, frameSignal)
    }

    private suspend fun executePreviewerAsync(xamlFile: VirtualFile) {
        statusProperty.set(Status.Connecting)
        val runnableProject = withContext(Dispatchers.ApplicationAnyModality) { getRunnableProject(xamlFile) }
        val projectOutput = runnableProject.projectOutputs.first() // TODO[F]: Get the project output for the current solution configuration/scope, not just the first one

        val msBuild = MsBuildParameterCollector.getInstance(project)
        val parameters = msBuild.getAvaloniaPreviewerParameters(runnableProject, projectOutput)

        val socket = withContext(Dispatchers.IO) { ServerSocket(0) }
        val process = AvaloniaPreviewerProcess(lifetime, parameters, socket.localPort)
        session = createSession(socket, parameters, xamlFile)

        statusProperty.set(Status.Working) // TODO[F]: Should be set after starting session and process, not before
        session.start() // TODO[F]: Session should run asynchronously as a suspend fun; await for either session or process termination

        process.run(project)
    }

    fun start(xamlFile: VirtualFile) {
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
