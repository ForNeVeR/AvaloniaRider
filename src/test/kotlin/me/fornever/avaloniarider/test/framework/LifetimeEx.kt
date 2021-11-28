package me.fornever.avaloniarider.test.framework

import com.intellij.openapi.rd.util.startOnUiAsync
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rdclient.util.idea.waitAndPump
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> runPumping(timeout: Duration = Duration.ofMinutes(1L), action: suspend CoroutineScope.() -> T): T {
    Lifetime.using { lt ->
        val deferred = lt.startOnUiAsync(action = action)
        waitAndPump(timeout, { deferred.isCompleted })
        return deferred.getCompleted()
    }
}
