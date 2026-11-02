package me.fornever.avaloniarider.test.cases

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.util.application
import com.jetbrains.rd.platform.diagnostics.RdLogTraceScenarios
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.OptProperty
import com.jetbrains.rd.util.reactive.Property
import com.jetbrains.rdclient.util.idea.pumpMessages
import com.jetbrains.rider.model.BuildResultKind
import com.jetbrains.rider.model.PreviewPlatformKind
import com.jetbrains.rider.test.OpenSolutionParams
import com.jetbrains.rider.test.annotations.Solution
import com.jetbrains.rider.test.annotations.TestSettings
import com.jetbrains.rider.test.asserts.shouldBeTrue
import com.jetbrains.rider.test.junit5.base.PerTestSolutionTestBase
import com.jetbrains.rider.test.enums.BuildTool
import com.jetbrains.rider.test.enums.sdk.SdkVersion
import com.jetbrains.rider.test.framework.frameworkLogger
import com.jetbrains.rider.test.scriptingApi.buildSolutionWithConsoleBuild
import com.jetbrains.rider.test.scriptingApi.getVirtualFileFromPath
import com.jetbrains.rider.xaml.core.XamlPreviewEditorExtension
import me.fornever.avaloniarider.controlmessages.FrameMessage
import me.fornever.avaloniarider.idea.editor.AvaloniaPreviewerXamlEditorExtension
import me.fornever.avaloniarider.idea.settings.AvaloniaPreviewerInteractionMode
import me.fornever.avaloniarider.idea.settings.AvaloniaPreviewerTheme
import me.fornever.avaloniarider.idea.settings.AvaloniaProjectSettings
import me.fornever.avaloniarider.previewer.AvaloniaPreviewerSessionController
import me.fornever.avaloniarider.rider.AvaloniaRiderProjectModelHost
import me.fornever.avaloniarider.test.framework.correctTestSolutionDirectory
import me.fornever.avaloniarider.test.framework.runPumping
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.div
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestSettings(sdkVersion = SdkVersion.AUTODETECT, buildTool = BuildTool.AUTODETECT)
@Solution("AvaloniaMvvm")
@Tag("episode/Plugins/AvaloniaRider")
class PreviewTests : PerTestSolutionTestBase() {

    override fun modifyOpenSolutionParams(params: OpenSolutionParams) {
        params.restoreNuGetPackages = true
        params.backendLoadedTimeout = Duration.ofMinutes(2L)
        super.modifyOpenSolutionParams(params)
    }

    override val traceScenarios = setOf(RdLogTraceScenarios.Commands)

    private val mainWindowFile
        get() = getVirtualFileFromPath("Views/MainWindow.axaml", correctTestSolutionDirectory)

    @Test
    fun previewEditorProviderShouldHandleTheXamlFile() {
        val provider = XamlPreviewEditorExtension.EP_NAME
            .extensionList
            .filterIsInstance<AvaloniaPreviewerXamlEditorExtension>()
            .single()
        provider.accepts(mainWindowFile, PreviewPlatformKind.AVALONIA).shouldBeTrue()
    }

    @Test
    fun previewControllerShouldRenderTheFrame() {
        val buildResult = buildSolutionWithConsoleBuild(timeout = Duration.ofMinutes(1L))
        assertTrue(
            buildResult.buildResultKind == BuildResultKind.Successful
                || buildResult.buildResultKind == BuildResultKind.HasWarnings,
            "Build should be successful."
        )
        var frameMsg: FrameMessage? = null
        Lifetime.using { lt ->
            // not init the property, so that the session doesn't start before we handle the frame
            val projectFilePathProperty = OptProperty<Path>()
            val document = application.runReadAction<Document?> {
                FileDocumentManager.getInstance().getDocument(mainWindowFile)
            }
            val selectedTheme = Property(AvaloniaPreviewerTheme.None)

            AvaloniaPreviewerSessionController(
                project,
                lt,
                consoleView = null,
                mainWindowFile,
                projectFilePathProperty,
                document,
                selectedTheme,
                Property(AvaloniaPreviewerInteractionMode.Interactive)
            ).apply {
                frame.advise(lt) {
                    frameMsg = it
                }
            }
            frameworkLogger.info("Listener initialized!")
            projectFilePathProperty.set(correctTestSolutionDirectory / "AvaloniaMvvm.csproj") // now the session starts

            pumpMessages(Duration.ofMinutes(1L)) {
                frameMsg != null
            }.shouldBeTrue()

            frameworkLogger.info("We are done!")
        }
    }

    @Test
    fun designModeClickShouldNavigateToTheElement() {
        val buildResult = buildSolutionWithConsoleBuild(timeout = Duration.ofMinutes(1L))
        assertTrue(
            buildResult.buildResultKind == BuildResultKind.Successful
                || buildResult.buildResultKind == BuildResultKind.HasWarnings,
            "Build should be successful."
        )

        var frameMsg: FrameMessage? = null
        var isNavigationUnavailable = false
        Lifetime.using { lt ->
            val projectFilePathProperty = OptProperty<Path>()
            val document = application.runReadAction<Document?> {
                FileDocumentManager.getInstance().getDocument(mainWindowFile)
            }!!
            val controller = AvaloniaPreviewerSessionController(
                project,
                lt,
                null,
                mainWindowFile,
                projectFilePathProperty,
                document,
                Property(AvaloniaPreviewerTheme.None),
                Property(AvaloniaPreviewerInteractionMode.Design)
            ).apply {
                frame.advise(lt) { frameMsg = it }
            }
            projectFilePathProperty.set(correctTestSolutionDirectory / "AvaloniaMvvm.csproj")

            pumpMessages(Duration.ofMinutes(1L)) {
                frameMsg != null
            }.shouldBeTrue()

            // The text block is the only element of the window, and it is centered.
            val expectedOffset = document.text.indexOf("<TextBlock")
            val frame = frameMsg!!
            controller.navigateToElementAt(frame.width / 2.0, frame.height / 2.0) {
                isNavigationUnavailable = true
            }

            fun caretOffset() = FileEditorManager.getInstance(project).selectedTextEditor?.caretModel?.offset
            pumpMessages(Duration.ofSeconds(30L)) {
                isNavigationUnavailable || caretOffset() == expectedOffset
            }
            assertFalse(isNavigationUnavailable, "Navigation should be available.")
            assertEquals(expectedOffset, caretOffset())
        }
    }

    @Test
    fun shadowCopyShouldNotLockOriginalAssembly() {
        val buildResult = buildSolutionWithConsoleBuild(timeout = Duration.ofMinutes(1L))
        assertTrue(
            buildResult.buildResultKind == BuildResultKind.Successful
                || buildResult.buildResultKind == BuildResultKind.HasWarnings,
            "Build should be successful."
        )

        val settings = AvaloniaProjectSettings.getInstance(project)
        settings.state.useShadowCopy = true

        try {
            val projectFilePath = correctTestSolutionDirectory / "AvaloniaMvvm.csproj"
            val outputAssemblyPath = runPumping {
                val host = AvaloniaRiderProjectModelHost.getInstance(project)
                Lifetime.using { tempLt ->
                    Path.of(host.getProjectOutput(tempLt, projectFilePath).outputPath)
                }
            }
            assertTrue(Files.exists(outputAssemblyPath), "Output assembly should exist after build")

            var frameMsg: FrameMessage? = null
            Lifetime.using { lt ->
                val projectFilePathProperty = OptProperty<Path>()
                val document = application.runReadAction<Document?> {
                    FileDocumentManager.getInstance().getDocument(mainWindowFile)
                }
                AvaloniaPreviewerSessionController(
                    project,
                    lt,
                    null,
                    mainWindowFile,
                    projectFilePathProperty,
                    document,
                    Property(AvaloniaPreviewerTheme.None),
                    Property(AvaloniaPreviewerInteractionMode.Interactive)
                ).apply {
                    frame.advise(lt) { frameMsg = it }
                }
                projectFilePathProperty.set(projectFilePath)

                pumpMessages(Duration.ofMinutes(1L)) {
                    frameMsg != null
                }.shouldBeTrue()

                assertTrue(Files.exists(outputAssemblyPath), "Output assembly should still exist")
                Files.delete(outputAssemblyPath)
                assertFalse(Files.exists(outputAssemblyPath), "Output assembly should have been deleted")
            }
        } finally {
            settings.state.useShadowCopy = false
        }
    }
}
