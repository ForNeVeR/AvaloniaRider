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
