package me.fornever.avaloniarider.previewer

import com.intellij.application.ApplicationThreadPool
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.onTermination
import com.jetbrains.rd.util.reactive.*
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.util.idea.application
import kotlinx.coroutines.*
import me.fornever.avaloniarider.controlmessages.AvaloniaMessages
import me.fornever.avaloniarider.controlmessages.FrameMessage
import me.fornever.avaloniarider.controlmessages.RequestViewportResizeMessage
import me.fornever.avaloniarider.idea.concurrency.ApplicationAnyModality
import java.net.ServerSocket

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

    val status: IProperty<Status> = Property(Status.Idle)
    private val lifetime = outerLifetime.createNested()

    val requestViewportResize: ISignal<RequestViewportResizeMessage> = Signal()
    val frameReceived: ISignal<FrameMessage> = Signal()
    val errorMessage: IProperty<String?> = Property(null)

    lateinit var session: AvaloniaPreviewerSession

    init {
        lifetime.onTermination { status.set(Status.Terminated) }
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
            AvaloniaMessages.getInstance(),
            socket,
            parameters.targetPath,
            xamlFile).apply {
            frame.advise(lifetime) {
                application.invokeLater({ frameReceived.fire(it) }, ModalityState.any())
            }
            requestViewportResize.advise(lifetime) {
                application.invokeLater({ requestViewportResize.fire(it) }, ModalityState.any())
            }
        }

    private suspend fun executePreviewerAsync(xamlFile: VirtualFile) {
        status.set(Status.Connecting)
        val runnableProject = withContext(Dispatchers.ApplicationAnyModality) { getRunnableProject(xamlFile) }
        val projectOutput = runnableProject.projectOutputs.first() // TODO[F]: Get the project output for the current solution configuration/scope, not just the first one

        val msBuild = MsBuildParameterCollector.getInstance(project)
        val parameters = msBuild.getAvaloniaPreviewerParameters(runnableProject, projectOutput)

        val socket = withContext(Dispatchers.IO) { ServerSocket(0) }
        val commandLine = AvaloniaPreviewerProcess.getCommandLine(parameters, socket.localPort)
        session = createSession(socket, parameters, xamlFile)

        status.set(Status.Working) // TODO[F]: Should be set after starting session and process, not before
        session.start() // TODO[F]: Session should run asynchronously as a suspend fun; await for either session or process termination

        AvaloniaPreviewerProcess.run(project, lifetime, commandLine)
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
