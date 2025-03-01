package me.fornever.avaloniarider.test.framework

import com.intellij.openapi.util.SystemInfo
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rider.test.OpenSolutionParams
import com.jetbrains.rider.test.base.PerClassSolutionTestBase
import com.jetbrains.rider.test.facades.solution.RiderSolutionApiFacade
import com.jetbrains.rider.test.facades.solution.SolutionApiFacade
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import java.nio.file.Path

abstract class AvaloniaIntegrationTest : PerClassSolutionTestBase() {
    protected lateinit var testLifetime: LifetimeDefinition

    @BeforeMethod
    fun beforeMethod() {
        testLifetime = LifetimeDefinition()
    }

    @AfterMethod
    fun afterMethod() {
        testLifetime.terminate()
    }

    override val solutionApiFacade: SolutionApiFacade = object : RiderSolutionApiFacade() {
        override fun waitForSolution(params: OpenSolutionParams) {
            // This may sometimes take a long time (especially on GitHub Actions).
            params.projectModelReadyTimeout = params.projectModelReadyTimeout.multipliedBy(10L)

            return super.waitForSolution(params)
        }
    }
}

/**
 * On Windows, it has to resolve the canonical path (presumably to resolve short paths), but that could break tests on
 * macOS, so we disable canonical paths on that.
 */
val SolutionApiFacade.correctTestSolutionDirectory: Path
    get() =
        if (SystemInfo.isWindows)
            activeSolutionDirectory.canonicalFile.toPath()
        else
            activeSolutionDirectory.toPath()
