package me.fornever.avaloniarider.idea.concurrency

import com.jetbrains.rd.framework.IRdTask
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.util.idea.toAsyncPromise
import org.jetbrains.concurrency.await

suspend fun <T> IRdTask<T>.await(lifetime: Lifetime): T =
    toAsyncPromise(lifetime).await()
