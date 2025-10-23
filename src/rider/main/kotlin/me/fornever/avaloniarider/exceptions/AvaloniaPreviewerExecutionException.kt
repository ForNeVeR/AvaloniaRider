package me.fornever.avaloniarider.exceptions

import org.jetbrains.annotations.Nls

class AvaloniaPreviewerExecutionException(
    val exitCode: Int?,
    val processOutput: String?
) : Exception(buildMessage(exitCode, processOutput))

private fun buildMessage(exitCode: Int?, output: String?): @Nls String {
    val codePart = exitCode?.let { "Previewer process exited with code $it." }
        ?: "Previewer process exited unexpectedly."
    val outputPart = output
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { "\n\n$it" }
        ?: ""
    return codePart + outputPart
}
