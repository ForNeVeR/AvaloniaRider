package me.fornever.avaloniarider

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ui.UIUtil
import java.util.*

fun String.toUUID() = UUID.fromString(this)
fun String.plainTextToHtml(): String {
    val htmlBody = StringUtil.replace(this, listOf("<", ">", "\r\n", "\n"), listOf("&lt;", "&gt;", "<br/>", "<br/>"))
    return UIUtil.toHtml(htmlBody)
}
