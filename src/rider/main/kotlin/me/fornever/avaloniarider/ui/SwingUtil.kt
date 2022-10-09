package me.fornever.avaloniarider.ui

import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.IPropertyView
import javax.swing.JComponent

fun JComponent.bindVisible(lifetime: Lifetime, property: IPropertyView<Boolean>) {
    property.advise(lifetime) { visible ->
        isVisible = visible
    }
}
