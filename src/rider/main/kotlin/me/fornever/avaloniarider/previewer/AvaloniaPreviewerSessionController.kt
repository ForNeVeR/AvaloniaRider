package me.fornever.avaloniarider.previewer

import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.createNestedDisposable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.application
import com.intellij.workspaceModel.ide.toPath
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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
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
import java.nio.file.StandardCopyOption
import java.time.Duration
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The sources on this class are thread-free. Make sure to schedule them onto the proper threads if necessary.
 */
class AvaloniaPreviewerSessionController(
    private val project: Project,
    outerLifetime: Lifetime,
    private val consoleView: ConsoleView?,
    private val xamlFile: VirtualFile,
    private val projectFilePathProperty: IOptPropertyView<Path>,
    private val baseDocument: Document?) {
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
    private var pendingRestart = false

    private val sessionLifetimeSource = SequentialLifetimes(controllerLifetime)
    private var currentSessionLifetime: LifetimeDefinition? = null
    private var restartJob: Job? = null

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
                scheduleRestart()
            }
    }

    private suspend fun getProjectContainingFile(virtualFile: VirtualFile): ProjectModelEntity?  =
        suspendCancellableCoroutine { cont ->
            Lifetime.using { lt ->
                application.assertIsDispatchThread()

                project.solution.riderSolutionLifecycle.isProjectModelReady.adviseUntil(lt) { isReady ->
                    if (!isReady) return@adviseUntil false
                    try {
                        logger.debug { "Project model view synchronized" }
                        val projectModelEntities = workspaceModel.getProjectModelEntities(virtualFile, project)
                        logger.debug {
                            "Project model nodes for file $xamlFile: " + projectModelEntities.joinToString(", ")
                        }
                        val containingProject =
                            projectModelEntities.firstNotNullOfOrNull { it.containingProjectEntity() }
                        if (containingProject != null) {
                            cont.resume(containingProject)
                        } else {
                            logger.warn("Workspace model doesn't contain project entity for $virtualFile")
                            cont.resume(null)
                        }
                    } catch (t: Throwable) {
                        cont.resumeWithException(t)
                    }

                    return@adviseUntil true
                }
            }
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

            val document = baseDocument
            if (document == null) {
                logger.warn("Unable to obtain document for $xamlFile")
                return@advise
            }
            val documentUpdates = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

            suspend fun dispatchXamlUpdate() {
                while (lifetime.isAlive) {
                    val (text, projectRelativePath) = readAction {
                        document.text to computeDocumentPathInProject(projectFilePathProperty.valueOrNull)
                    }
                    val effectivePath = projectRelativePath ?: lastKnownProjectRelativePath
                    if (effectivePath == null) {
                        val message = "Skipping XAML update for ${xamlFile.name}: project path is unknown"
                        if (lastKnownProjectRelativePath == null) {
                            logger.warn(message)
                        } else {
                            logger.info(message)
                        }
                        inFlightUpdate.value = false
                        delay(projectPathRetryDelay.toMillis())
                        continue
                    }

                    lastKnownProjectRelativePath = effectivePath
                    inFlightUpdate.value = true
                    sendXamlUpdate(text, effectivePath)
                    break
                }
            }

            lifetime.launch {
                documentUpdates.collectLatest {
                    dispatchXamlUpdate()
                }
            }

            document.documentChanged()
                .throttleLast(xamlEditThrottling, SwingScheduler)
                .advise(lifetime) {
                    documentUpdates.tryEmit(Unit)
                }
            documentUpdates.tryEmit(Unit)
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
        var parameters = msBuild.getAvaloniaPreviewerParameters(
            projectFilePath,
            projectOutput,
            xamlContainingProject
        )

        var shadowDirToDelete: Path? = null
        val sessionNestedLifetime = lifetime.createNested() // to terminate later than the process

        try {
            if (settings.useShadowCopy) {
                // When using shadow copy, we need to watch the original file for changes
                // because the previewer will be running from a copy.
                // If the original file changes (e.g. from an external build), we need to restart the session
                // to pick up the new changes.
                setupFileSystemWatcher(sessionNestedLifetime, parameters.targetPath)

                parameters = withContext(Dispatchers.IO) {
                    createShadowCopy(parameters).also {
                        shadowDirToDelete = it.targetDir
                    }
                }
            }

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
            // Only treat as error if lifetime is still alive; otherwise process was intentionally terminated
            if (lifetime.isAlive && exitCode != null && exitCode != 0) {
                throw AvaloniaPreviewerExecutionException(exitCode, processResult.outputSnippet)
            }
        } finally {
            shadowDirToDelete?.let { dir ->
                withContext(Dispatchers.IO) {
                    try {
                        FileUtil.delete(dir.toFile())
                    } catch (e: Exception) {
                        logger.warn("Failed to delete shadow directory $dir", e)
                    }
                }
            }
        }
    }

    fun start(
        projectFilePath: Path,
        force: Boolean = false,
        executionMode: ProcessExecutionMode = ProcessExecutionMode.Run
    ) {
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

    private fun scheduleRestart() {
        val projectFilePath = projectFilePathProperty.valueOrNull ?: return
        restartJob?.cancel()
        restartJob = controllerLifetime.launch {
            delay(projectPathRetryDelay.toMillis())
            if (session != null) return@launch
            logger.info("Restarting previewer for $xamlFile after document change")
            start(projectFilePath, force = true)
        }
    }

    private fun computeDocumentPathInProject(projectFilePath: Path?): String? {
        val projectModelItems = workspaceModel.getProjectModelEntities(xamlFile, project)
        val candidate = projectFilePath?.let { desiredPath ->
            projectModelItems.firstOrNull { entity ->
                val containingProject = entity.containingProjectEntity() ?: return@firstOrNull false
                val containingPath = containingProject.url?.toPath() ?: return@firstOrNull false
                FileUtil.pathsEqual(containingPath.toString(), desiredPath.toString())
            }
        } ?: projectModelItems.firstOrNull()

        return candidate?.projectRelativeVirtualPath?.let { "/$it" }
    }

    private fun createShadowCopy(parameters: AvaloniaPreviewerParameters): AvaloniaPreviewerParameters {
        val shadowDir = FileUtil.createTempDirectory("avalonia-previewer-shadow", null).toPath()
        val originalDir = parameters.targetDir

        logger.info("Creating shadow copy of $originalDir to $shadowDir")

        if (Files.exists(originalDir)) {
            Files.walk(originalDir).use { stream ->
                stream.forEach { source ->
                    try {
                        val relative = originalDir.relativize(source)
                        val destination = shadowDir.resolve(relative)
                        if (Files.isDirectory(source)) {
                            Files.createDirectories(destination)
                        } else {
                            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING)
                        }
                    } catch (e: Exception) {
                        logger.warn("Failed to copy $source", e)
                    }
                }
            }
        }

        val newTargetPath = shadowDir.resolve(parameters.targetPath.fileName)
        if (!Files.exists(newTargetPath) && Files.exists(parameters.targetPath)) {
            Files.copy(parameters.targetPath, newTargetPath, StandardCopyOption.REPLACE_EXISTING)
        }

        val newXamlContainingAssemblyPath = shadowDir.resolve(parameters.xamlContainingAssemblyPath.fileName)
        if (!Files.exists(newXamlContainingAssemblyPath) && Files.exists(parameters.xamlContainingAssemblyPath)) {
            Files.copy(parameters.xamlContainingAssemblyPath, newXamlContainingAssemblyPath, StandardCopyOption.REPLACE_EXISTING)
        }

        // We do NOT change the working directory to the shadow directory.
        // This is important to preserve the behavior of relative paths in the user application.
        // For example, if the app uses "../../src/Assets/image.png", it should still work relative to the original output directory.
        // The executables and assemblies will be loaded from the shadow directory, which prevents locking,
        // but the process context (CWD) will remain in the original location.

        var newPreviewerBinary = parameters.previewerBinary
        if (parameters.previewerBinary.startsWith(originalDir)) {
            val relativePath = originalDir.relativize(parameters.previewerBinary)
            newPreviewerBinary = shadowDir.resolve(relativePath)
        }

        return parameters.copy(
            targetDir = shadowDir,
            targetPath = newTargetPath,
            xamlContainingAssemblyPath = newXamlContainingAssemblyPath,
            previewerBinary = newPreviewerBinary
        )
    }

    private fun setupFileSystemWatcher(lifetime: Lifetime, fileToWatch: Path) {
        val disposable = lifetime.createNestedDisposable()
        val connection = application.messageBus.connect(disposable)
        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                val watchedPath = FileUtil.toSystemIndependentName(fileToWatch.toString())
                val relevantChange = events.any { event ->
                    event.path == watchedPath
                }

                if (relevantChange) {
                    logger.info("Output assembly modified: $fileToWatch. Restarting previewer.")
                    val projectFilePath = projectFilePathProperty.valueOrNull
                    if (projectFilePath != null) {
                        // We use a small delay to ensure the write is completely finished and to debounce
                        controllerLifetime.launch {
                            delay(500)
                            start(projectFilePath, force = true)
                        }
                    }
                }
            }
        })
    }
}
