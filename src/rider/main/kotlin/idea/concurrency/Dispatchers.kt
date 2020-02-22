package me.fornever.avaloniarider.idea.concurrency

import com.intellij.util.concurrency.EdtExecutorService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher

private val ApplicationAnyModalityDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()

val Dispatchers.ApplicationAnyModality: CoroutineDispatcher
    get() = ApplicationAnyModalityDispatcher
