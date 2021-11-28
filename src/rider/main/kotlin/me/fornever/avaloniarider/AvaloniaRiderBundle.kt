package me.fornever.avaloniarider

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

private const val BUNDLE = "messages.AvaloniaRiderBundle"

object AvaloniaRiderBundle : DynamicBundle(BUNDLE) {
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
        getMessage(key, *params)

    fun messagePointer(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): Supplier<String> =
        getLazyMessage(key, *params)
}
