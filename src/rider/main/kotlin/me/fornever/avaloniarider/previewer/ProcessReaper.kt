package me.fornever.avaloniarider.previewer

import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.util.launchNonUrgentBackground
import com.jetbrains.rd.platform.util.idea.LifetimedService
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.intersect
import com.jetbrains.rd.util.lifetime.onTermination
import com.jetbrains.rider.util.idea.getService
import kotlinx.coroutines.time.delay
import java.time.Duration

@Service
class ProcessReaper : LifetimedService() {

    companion object {
        fun getInstance(project: Project): ProcessReaper = project.getService()

        private val logger = logger<ProcessReaper>()
    }

    fun registerProcess(
        externalLifetime: Lifetime,
        processHandler: ProcessHandler,
        timeout: Duration = Duration.ofSeconds(5L)
    ) {
        fun destroy() {
            if (!processHandler.isProcessTerminated) {
                logger.info("Forcefully terminating process $processHandler after timeout of $timeout")
                processHandler.destroyProcess()
            }
        }

        externalLifetime.onTermination {
            val actualProcessLifetime = processHandler.lifetime
            val timeoutLifetime = createTimeoutLifetime(timeout)
            if (!serviceLifetime.intersect(actualProcessLifetime).intersect(timeoutLifetime).onTerminationIfAlive {
                destroy()
            }) {
                destroy()
            }
        }
    }

    private val ProcessHandler.lifetime: Lifetime
        get() {
            val actualProcessLifetime = LifetimeDefinition()
            addProcessListener(object : ProcessAdapter() {
                override fun processTerminated(event: ProcessEvent) {
                    actualProcessLifetime.terminate()
                }
            }, this@ProcessReaper)
            if (isProcessTerminated) {
                actualProcessLifetime.terminate()
            }

            return actualProcessLifetime
        }

    private fun createTimeoutLifetime(timeout: Duration): Lifetime {
        val ld = LifetimeDefinition()
        ld.launchNonUrgentBackground {
            delay(timeout)
        }.invokeOnCompletion { ld.terminate() }
        return ld.lifetime
    }
}
