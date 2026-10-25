package me.fornever.avaloniarider

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

object AvaloniaRiderBundle {

    private const val BUNDLE = "messages.AvaloniaRiderBundle"

    private val bundle = DynamicBundle(AvaloniaRiderBundle::class.java, BUNDLE)

    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
        bundle.getMessage(key, *params)

    fun messagePointer(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): Supplier<String> =
        bundle.getLazyMessage(key, *params)
}
