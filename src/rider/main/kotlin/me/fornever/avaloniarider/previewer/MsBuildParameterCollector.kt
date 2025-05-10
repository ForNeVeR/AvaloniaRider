package me.fornever.avaloniarider.previewer

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.workspaceModel.ide.toPath
import com.jetbrains.rd.util.threading.coroutines.asCoroutineDispatcher
import com.jetbrains.rider.model.RdNuGetFolderKind
import com.jetbrains.rider.model.nuGetHost
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import com.jetbrains.rider.protocol.protocol
import com.jetbrains.rider.run.environment.MSBuildEvaluator
import com.jetbrains.rider.runtime.DotNetRuntime
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import me.fornever.avaloniarider.AvaloniaRiderBundle
import me.fornever.avaloniarider.exceptions.AvaloniaPreviewerInitializationException
import me.fornever.avaloniarider.idea.settings.AvaloniaWorkspaceSettings
import me.fornever.avaloniarider.idea.settings.getCorrectWorkingDirectory
import me.fornever.avaloniarider.idea.settings.getPath
import me.fornever.avaloniarider.model.RdProjectOutput
import me.fornever.avaloniarider.rider.AvaloniaRiderProjectModelHost
import org.jetbrains.annotations.Nls
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*

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

        val previewerPath = Path(
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

        return coroutineScope {
            val runnableProjectProperties = async {
                val result = msBuildEvaluator.evaluatePropertiesSuspending(
                    MSBuildEvaluator.PropertyRequest(
                        runnableProjectFilePath.toString(),
                        tfm,
                        listOf(avaloniaPreviewerPathKey, "TargetDir", "TargetName", "TargetPath")
                    )
                ).toMutableMap()
                val previewerPath = result[avaloniaPreviewerPathKey] ?: return@async result
                result[avaloniaPreviewerPathKey] = correctPreviewerExecutablePath(
                    runnableProjectFilePath,
                    Path(previewerPath)
                ).pathString
                result
            }
            val xamlProjectProperties = async {
                msBuildEvaluator.evaluatePropertiesSuspending(
                    MSBuildEvaluator.PropertyRequest(
                        xamlContainingProjectPath,
                        tfm,
                        listOf("TargetPath")
                    )
                )
            }

            createParameters(
                runtime,
                runnableProjectFilePath,
                avaloniaPreviewerPathKey,
                runnableProjectProperties.await(),
                xamlProjectProperties.await(),
                projectOutput.getCorrectWorkingDirectory()
            )
        }
    }

    private suspend fun correctPreviewerExecutablePath(projectFilePath: Path, previewerPath: Path): Path {
        // TODO[#513]: See #511 and RIDER-125656: on Rider 2025.1.2, the path MSBuild property is incorrectly evaluated to
        //             <Project directory>/../tools/netstandard2.0/designer/Avalonia.Designer.HostApp.dll
        //             This method tries to apply a workaround and restore the correct path.
        val projectDir = projectFilePath.parent
        if (previewerPath.startsWith(projectDir)) {
            if (previewerPath.elementAtOrNull(projectDir.nameCount)?.name == "..") {
                logger.info("RIDER-125656 workaround mode activated: looking for Avalonia package…")
                val nuGetPackagePath = withContext(project.protocol.scheduler.asCoroutineDispatcher) {
                    val nuGet = project.solution.nuGetHost
                    val avaloniaVersions = nuGet.nuGetProjectModel.projects.values
                        .asSequence()
                        .flatMap { nuGetProject ->
                            val explicitlyInstalledVersions = nuGetProject.explicitPackages
                                .filter { it.id == "Avalonia" }
                                .map { it.version }
                            val otherVersions = nuGetProject.integratedPackages
                                .filter { it.identity.id == "Avalonia" }
                                .map { it.identity.version }
                            explicitlyInstalledVersions + otherVersions
                        }
                        .distinct()
                    // We might do something better, but should work as a workaround for now:
                    val version = avaloniaVersions.firstOrNull() ?: return@withContext null

                    val packageCache = nuGet.folderManager.folders.valueOrNull
                        ?.firstOrNull { it.kind == RdNuGetFolderKind.GlobalPackages }
                    packageCache?.let {
                        val nuGetFolder = Path(packageCache.path)
                        nuGetFolder / "avalonia" / version
                    }
                }

                if (nuGetPackagePath == null) {
                    logger.warn("Package Avalonia not found among the installed packages.")
                } else {
                    logger.info("Possible Avalonia package found: $nuGetPackagePath")
                    val previewerRelativePath = previewerPath.drop(projectDir.nameCount + 1)
                        .joinToString(File.separator) { it.name }
                    val result = nuGetPackagePath / previewerRelativePath

                    logger.info("Applying RIDER-125656 workaround: \"$result\".")
                    return result
                }
            }
        }

        return previewerPath
    }
}
