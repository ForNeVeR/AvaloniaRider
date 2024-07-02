package me.fornever.avaloniarider.previewer

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.workspaceModel.ide.toPath
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import com.jetbrains.rider.run.environment.MSBuildEvaluator
import com.jetbrains.rider.runtime.DotNetRuntime
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import me.fornever.avaloniarider.AvaloniaRiderBundle
import me.fornever.avaloniarider.exceptions.AvaloniaPreviewerInitializationException
import me.fornever.avaloniarider.idea.settings.AvaloniaWorkspaceSettings
import me.fornever.avaloniarider.idea.settings.getCorrectWorkingDirectory
import me.fornever.avaloniarider.idea.settings.getPath
import me.fornever.avaloniarider.model.RdProjectOutput
import me.fornever.avaloniarider.rider.AvaloniaRiderProjectModelHost
import org.jetbrains.annotations.Nls
import org.jetbrains.concurrency.await
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.nameWithoutExtension

@Service(Service.Level.PROJECT)
class MsBuildParameterCollector(private val project: Project) {
    companion object {
        fun getInstance(project: Project): MsBuildParameterCollector =
            project.getService(MsBuildParameterCollector::class.java)

        private val logger = logger<MsBuildParameterCollector>()
    }

    private val workspaceSettings
        get() = AvaloniaWorkspaceSettings.getInstance(project)

    private fun getPathKey(runtime: DotNetRuntime): String = when (runtime) {
        is DotNetCoreRuntime -> "AvaloniaPreviewerNetCoreToolPath"
        else -> "AvaloniaPreviewerNetFullToolPath"
    }

    private fun createParameters(
        runtime: DotNetRuntime,
        runnableProjectFilePath: Path,
        avaloniaPreviewerPathKey: String,
        runnableProjectProperties: Map<String, String>,
        xamlContainingProjectProperties: Map<String, String>,
        runnableProjectWorkingDirectory: Path
    ): AvaloniaPreviewerParameters {
        fun getProperty(properties: Map<String, String>, key: String, errorMessage: @Nls String? = null): String {
            val property = properties[key]
            if (property.isNullOrEmpty()) {
                throw AvaloniaPreviewerInitializationException(
                    errorMessage ?: AvaloniaRiderBundle.message("msbuild.error.property-not-found", key)
                )
            }

            return property
        }

        val previewerPath = Paths.get(
            getProperty(
                runnableProjectProperties,
                avaloniaPreviewerPathKey,
                AvaloniaRiderBundle.message(
                    "msbuild.error.avalonia-not-found-in-project",
                    runnableProjectFilePath.nameWithoutExtension
                )
            )
        )
        val targetDir = Paths.get(getProperty(runnableProjectProperties, "TargetDir"))
        val targetName = getProperty(runnableProjectProperties, "TargetName")
        val targetPath = Paths.get(getProperty(runnableProjectProperties, "TargetPath"))

        val xamlAssemblyPath = Paths.get(getProperty(xamlContainingProjectProperties, "TargetPath"))

        return AvaloniaPreviewerParameters(
            runtime,
            previewerPath,
            targetDir,
            targetName,
            targetPath,
            xamlAssemblyPath,
            workspaceSettings.state.workingDirectorySpecification.getPath(project, runnableProjectWorkingDirectory)
        )
    }

    suspend fun getAvaloniaPreviewerParameters(
        runnableProjectFilePath: Path,
        runnableProjectOutput: RdProjectOutput,
        xamlContainingProject: ProjectModelEntity
    ): AvaloniaPreviewerParameters {
        val runtimeHost = RiderDotNetActiveRuntimeHost.getInstance(project)
        val msBuildEvaluator = MSBuildEvaluator.getInstance(project)

        val runnableProjects = AvaloniaRiderProjectModelHost.getInstance(project).filteredRunnableProjects.valueOrNull
        val runnableProject =
            runnableProjects?.singleOrNull { FileUtil.pathsEqual(it.projectFilePath, runnableProjectFilePath.toString()) }
                ?: run {
                    logger.warn(
                        "Could not find runnable project for path $runnableProjectFilePath; all runnable projects are ${
                            runnableProjects?.joinToString { it.projectFilePath }
                        }"
                    )
                    throw AvaloniaPreviewerInitializationException(
                        AvaloniaRiderBundle.message(
                            "msbuild.error.runnable-project-not-found",
                            FileUtil.getNameWithoutExtension(runnableProjectFilePath.toString())
                        )
                    )
                }
        val xamlContainingProjectPath = xamlContainingProject.url!!.toPath().toString()

        val tfm = runnableProjectOutput.tfm
        val projectOutput = runnableProject.projectOutputs.singleOrNull { it.tfm == tfm }
            ?: throw AvaloniaPreviewerInitializationException(
                AvaloniaRiderBundle.message(
                    "msbuild.error.project-output-not-found",
                    runnableProject.name,
                    tfm.presentableName
                )
            )
        val runtime = DotNetRuntime.detectRuntimeForProjectOrThrow(
            project,
            runnableProject.kind,
            runtimeHost,
            runtimeType = null,
            runnableProjectOutput.outputPath,
            tfm
        )
        val avaloniaPreviewerPathKey = getPathKey(runtime)

        val runnableProjectProperties = msBuildEvaluator.evaluateProperties(
            MSBuildEvaluator.PropertyRequest(
                runnableProjectFilePath.toString(),
                tfm,
                listOf(avaloniaPreviewerPathKey, "TargetDir", "TargetName", "TargetPath")
            )
        )
        val xamlProjectProperties = msBuildEvaluator.evaluateProperties(
            MSBuildEvaluator.PropertyRequest(
                xamlContainingProjectPath,
                tfm,
                listOf("TargetPath")
            )
        )

        return createParameters(
            runtime,
            runnableProjectFilePath,
            avaloniaPreviewerPathKey,
            runnableProjectProperties.await(),
            xamlProjectProperties.await(),
            projectOutput.getCorrectWorkingDirectory()
        )
    }
}
