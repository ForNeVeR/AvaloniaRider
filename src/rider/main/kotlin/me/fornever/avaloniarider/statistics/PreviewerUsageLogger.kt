package me.fornever.avaloniarider.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

@Suppress("UnstableApiUsage")
class PreviewerUsageLogger : CounterUsagesCollector() {
    companion object {
        private val GROUP = EventLogGroup("avalonia.previewer", 1)
        private val PREVIEW_SUCCESS = GROUP.registerEvent("preview.success")
        private val PREVIEW_FAILURE = GROUP.registerEvent("preview.failure")
        private val PREVIEW_CRITICAL = GROUP.registerEvent("preview.critical_failure")

        fun logPreviewSuccess(project: Project) {
            PREVIEW_SUCCESS.log(project)
        }

        fun logPreviewError(project: Project) {
            PREVIEW_FAILURE.log(project)
        }

        fun logPreviewCriticalFailure(project: Project) {
            PREVIEW_CRITICAL.log(project)
        }
    }

    override fun getGroup() = GROUP
}
