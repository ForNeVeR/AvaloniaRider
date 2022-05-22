package me.fornever.avaloniarider.rd

import com.jetbrains.rd.util.reactive.IOptPropertyView
import com.jetbrains.rd.util.reactive.IPropertyView
import com.jetbrains.rd.util.reactive.asNullable
import com.jetbrains.rd.util.reactive.compose

fun <T1, T2 : Any> compose(
    property1: IPropertyView<T1>,
    property2: IOptPropertyView<T2>
): IPropertyView<Pair<T1, T2?>> =
    property1.compose(property2.asNullable(), ::Pair)

fun <T1 : Any, T2> compose(
    property1: IOptPropertyView<T1>,
    property2: IPropertyView<T2>
): IPropertyView<Pair<T1?, T2>> =
    property1.asNullable().compose(property2, ::Pair)

data class Quad<T1, T2, T3, T4>(val item1: T1, val item2: T2, val item3: T3, val item4: T4)
fun <T1, T2, T3 : Any, T4> compose(
    property1: IPropertyView<T1>,
    property2: IPropertyView<T2>,
    property3: IOptPropertyView<T3>,
    property4: IPropertyView<T4>
): IPropertyView<Quad<T1, T2, T3?, T4>> =
    property1.compose(property2, ::Pair)
        .compose(property3.asNullable()) { (a, b), c -> Triple(a, b, c) }
        .compose(property4) { (a, b, c), d -> Quad(a, b, c, d) }
