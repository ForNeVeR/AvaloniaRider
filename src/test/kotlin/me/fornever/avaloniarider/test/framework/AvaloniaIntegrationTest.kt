package me.fornever.avaloniarider.test.framework

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.jetbrains.rider.test.OpenSolutionParams
import com.jetbrains.rider.test.base.BaseTestWithSolution
import java.io.File
import java.nio.file.Path

abstract class AvaloniaIntegrationTest : BaseTestWithSolution() {

    override fun openSolution(solutionFile: File, params: OpenSolutionParams): Project {
        // This may take a long time sometimes.
        params.projectModelReadyTimeout = params.projectModelReadyTimeout.multipliedBy(10L)

        return super.openSolution(solutionFile, params)
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
