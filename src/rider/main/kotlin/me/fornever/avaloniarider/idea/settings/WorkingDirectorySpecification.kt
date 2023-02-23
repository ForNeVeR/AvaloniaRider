package me.fornever.avaloniarider.idea.settings

import java.nio.file.Path

sealed interface WorkingDirectorySpecification {
    fun getPath(): Path
}

object DefinedByMsBuild : WorkingDirectorySpecification {
    override fun getPath(): Path {
        TODO("Not yet implemented")
    }
}

object SolutionDirectory : WorkingDirectorySpecification
data class CustomPath(val path: String) : WorkingDirectorySpecification
