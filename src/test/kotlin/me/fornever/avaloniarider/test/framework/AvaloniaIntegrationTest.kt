package me.fornever.avaloniarider.test.framework

import com.intellij.openapi.util.SystemInfo
import com.jetbrains.rider.test.OpenSolutionParams
import com.jetbrains.rider.test.base.BaseTestWithSolution
import com.jetbrains.rider.test.facades.RiderSolutionApiFacade
import com.jetbrains.rider.test.facades.solution.SolutionApiFacade
import java.nio.file.Path

abstract class AvaloniaIntegrationTest : BaseTestWithSolution() {

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
val BaseTestWithSolution.correctTestSolutionDirectory: Path
    get() =
        if (SystemInfo.isWindows)
            activeSolutionDirectory.canonicalFile.toPath()
        else
            activeSolutionDirectory.toPath()
