package me.fornever.avaloniarider.idea.concurrency

import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.IScheduler
import com.jetbrains.rd.util.reactive.ISource
import com.jetbrains.rider.ui.SwingScheduler

fun <T> ISource<T>.adviseOn(lifetime: Lifetime, scheduler: IScheduler, handler: (T) -> Unit) {
    advise(lifetime) {
        scheduler.queue {
            lifetime.executeIfAlive {
                handler(it)
            }
        }
    }
}

fun <T> ISource<T>.adviseOnUiThread(lifetime: Lifetime, handler: (T) -> Unit) {
    adviseOn(lifetime, SwingScheduler, handler)
}
