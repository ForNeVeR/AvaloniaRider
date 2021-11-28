package me.fornever.avaloniarider.previewer

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.workspaceModel.ide.toPath
import com.jetbrains.rider.model.RdTargetFrameworkId
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import com.jetbrains.rider.run.environment.MSBuildEvaluator
import com.jetbrains.rider.runtime.DotNetRuntime
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import me.fornever.avaloniarider.exceptions.AvaloniaPreviewerInitializationException
import me.fornever.avaloniarider.model.RdProjectOutput
import me.fornever.avaloniarider.rider.AvaloniaRiderProjectModelHost
import org.jetbrains.concurrency.await
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.nameWithoutExtension

@Service
class MsBuildParameterCollector(private val project: Project) {
    companion object {
        fun getInstance(project: Project): MsBuildParameterCollector =
            project.getService(MsBuildParameterCollector::class.java)

        private val logger = logger<MsBuildParameterCollector>()
    }

    private fun getPathKey(runtime: DotNetRuntime): String = when (runtime) {
        is DotNetCoreRuntime -> "AvaloniaPreviewerNetCoreToolPath"
        else -> "AvaloniaPreviewerNetFullToolPath"
    }

    @OptIn(ExperimentalPathApi::class)
    private fun createParameters(
        runtime: DotNetRuntime,
        runnableProjectFilePath: Path,
        avaloniaPreviewerPathKey: String,
        runnableProjectProperties: Map<String, String>,
        xamlContainingProjectProperties: Map<String, String>): AvaloniaPreviewerParameters {
        fun getProperty(properties: Map<String, String>, key: String, errorMessage: String? = null): String {
            val property = properties[key]
            if (property.isNullOrEmpty()) {
                throw AvaloniaPreviewerInitializationException(
                    errorMessage ?: "Cannot determine value of property \"$key\" from MSBuild")
            }

            return property
        }

        val previewerPath = Paths.get(
            getProperty(
                runnableProjectProperties,
                avaloniaPreviewerPathKey,
                "Avalonia could not be found. Please ensure project ${runnableProjectFilePath.nameWithoutExtension} includes package Avalonia version 0.7 or higher"
            )
        )
        val targetDir = Paths.get(getProperty(runnableProjectProperties, "TargetDir"))
        val targetName = getProperty(runnableProjectProperties, "TargetName")
        val targetPath = Paths.get(getProperty(runnableProjectProperties, "TargetPath"))

        val xamlAssemblyPath = Paths.get(getProperty(xamlContainingProjectProperties, "TargetPath"))

        return AvaloniaPreviewerParameters(runtime, previewerPath, targetDir, targetName, targetPath, xamlAssemblyPath)
    }

    @Suppress("UnstableApiUsage")
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
                        "Could not find runnable project for path ${FileUtil.getNameWithoutExtension(runnableProjectFilePath.toString())}"
                    )
                }
        val xamlContainingProjectPath = xamlContainingProject.url!!.toPath().toString()

        val tfm = runnableProjectOutput.tfm
        val runtime = DotNetRuntime.detectRuntimeForProjectOrThrow(
            runnableProject.kind,
            runtimeHost,
            null,
            runnableProjectOutput.outputPath,
            RdTargetFrameworkId(
                tfm.shortName,
                tfm.presentableName,
                isNetCoreApp = tfm.isNetCoreApp,
                isNetFramework = tfm.isNetFramework
            )
        )
        val avaloniaPreviewerPathKey = getPathKey(runtime)

        val runnableProjectProperties = msBuildEvaluator.evaluateProperties(
            MSBuildEvaluator.PropertyRequest(
                runnableProjectFilePath.toString(),
                null,
                listOf(avaloniaPreviewerPathKey, "TargetDir", "TargetName", "TargetPath")
            )
        )
        val xamlProjectProperties = msBuildEvaluator.evaluateProperties(
            MSBuildEvaluator.PropertyRequest(
                xamlContainingProjectPath,
                null,
                listOf("TargetPath")
            )
        )

        return createParameters(
            runtime,
            runnableProjectFilePath,
            avaloniaPreviewerPathKey,
            runnableProjectProperties.await(),
            xamlProjectProperties.await())
    }
}
