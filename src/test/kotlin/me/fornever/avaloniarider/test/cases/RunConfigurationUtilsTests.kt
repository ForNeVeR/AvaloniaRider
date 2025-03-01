package me.fornever.avaloniarider.test.cases

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.ProjectManager
import com.jetbrains.rider.test.annotations.Solution
import com.jetbrains.rider.test.asserts.shouldBe
import me.fornever.avaloniarider.rider.createExeConfiguration
import me.fornever.avaloniarider.test.framework.AvaloniaIntegrationTest
import org.testng.annotations.Test

@Solution("MSBuildParameters")
class RunConfigurationUtilsTests : AvaloniaIntegrationTest() {

    @Test
    fun testCreateExeConfiguration() {
        val project = ProjectManager.getInstance().defaultProject
        val commandLine = GeneralCommandLine("dotnet", "AvaloniaPreviewer")
            .withEnvironment("TEST", "123")
        val configuration = createExeConfiguration(project, commandLine)
        configuration.project.shouldBe(project)
        configuration.parameters.apply {
            exePath.shouldBe("dotnet")
            programParameters.shouldBe("AvaloniaPreviewer")
            envs.shouldBe(mapOf("TEST" to "123"))
            isPassParentEnvs.shouldBe(true)
        }
    }
}
