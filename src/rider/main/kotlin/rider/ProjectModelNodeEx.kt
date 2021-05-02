package me.fornever.avaloniarider.rider

import com.google.common.collect.Queues
import com.jetbrains.rider.model.RdProjectDescriptor
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity

val ProjectModelEntity.projectRelativeVirtualPath: String
    get() {
        val names = Queues.newArrayDeque<String>()
        var current: ProjectModelEntity? = this
        while (current != null && current.descriptor !is RdProjectDescriptor) {
            names.push(current.name)
            current = current.parentEntity
        }
        return names.joinToString("/")
    }
