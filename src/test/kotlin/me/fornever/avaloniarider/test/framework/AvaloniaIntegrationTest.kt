package me.fornever.avaloniarider.test.framework

import com.intellij.openapi.project.Project
import com.jetbrains.rider.test.OpenSolutionParams
import com.jetbrains.rider.test.base.BaseTestWithSolution
import java.io.File

abstract class AvaloniaIntegrationTest : BaseTestWithSolution() {

    override fun openSolution(solutionFile: File, params: OpenSolutionParams): Project {
        // This may take a long time sometimes.
        params.projectModelReadyTimeout = params.projectModelReadyTimeout.multipliedBy(10L)

        return super.openSolution(solutionFile, params)
    }
}
