package me.fornever.avaloniarider.idea

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

fun Project.getCoroutineScope(): CoroutineScope {
    return service<ProjectService>().scope
}

@Service(Service.Level.PROJECT)
private class ProjectService(val scope: CoroutineScope)
