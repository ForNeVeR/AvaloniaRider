package me.fornever.avaloniarider.test.cases

import com.intellij.openapi.project.Project
import com.jetbrains.rider.test.base.BaseTestWithSolution

abstract class AvaloniaIntegrationTest : BaseTestWithSolution() {

    override fun openSolution(solutionDirectoryName: String, params: OpenSolutionParams): Project {
        val ci = System.getenv("CI")
        if (ci.equals("true", ignoreCase = true) || ci == "1") {
            // This may take a long time on GitHub Actions agent.
            params.projectModelReadyTimeout = params.projectModelReadyTimeout.multipliedBy(10L)
        }

        return super.openSolution(solutionDirectoryName, params)
    }

}
