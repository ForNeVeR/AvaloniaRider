package me.fornever.avaloniarider.rider

import com.google.common.collect.Queues
import com.jetbrains.rider.model.RdProjectDescriptor
import com.jetbrains.rider.projectView.nodes.ProjectModelNode

val ProjectModelNode.projectRelativeVirtualPath: String
    get() {
        val names = Queues.newArrayDeque<String>()
        var current: ProjectModelNode? = this
        while (current != null && current.descriptor !is RdProjectDescriptor) {
            names.push(current.name)
            current = current.parent
        }
        return names.joinToString("/")
    }
