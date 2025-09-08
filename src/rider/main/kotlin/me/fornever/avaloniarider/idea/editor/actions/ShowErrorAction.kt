package me.fornever.avaloniarider.idea.editor.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.Html
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.*
import com.jetbrains.rd.util.threading.coroutines.launch
import kotlinx.coroutines.Dispatchers
import me.fornever.avaloniarider.controlmessages.UpdateXamlResultMessage
import me.fornever.avaloniarider.idea.concurrency.adviseOnUiThread
import java.awt.Point

class ShowErrorAction(
    lifetime: Lifetime,
    private val errorMessage: IPropertyView<String?>,
    private val edtUpdater: () -> Unit
) : AnAction(AllIcons.General.Error) {

    init {
        val hasError = errorMessage.map { !it.isNullOrEmpty() }
        hasError.adviseOnUiThread(lifetime) {
            lifetime.launch(Dispatchers.EDT) {
                edtUpdater()
            }
        }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        when (val message = errorMessage.value) {
            null -> e.presentation.isEnabledAndVisible = false
            else -> {
                e.presentation.isEnabledAndVisible = true
                e.presentation.text = message.lineSequence().first()
                e.presentation.description = message
            }
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val message = errorMessage.value ?: return
        val balloon = JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder(HtmlBuilder().appendRaw(message.replaceLeadingSpace("&nbsp;")).toString(), MessageType.ERROR, null)
            .createBalloon()
        val component = e.inputEvent?.component ?: return
        val location = component.locationOnScreen
        val size = component.size
        val point = Point(location.x + size.width / 2, location.y + size.height)
        balloon.show(RelativePoint(point), Balloon.Position.below)
    }
}

internal fun getErrorMessageProperty(updateXamlResult: IOptPropertyView<UpdateXamlResultMessage>): IPropertyView<String?> {
    return updateXamlResult.asNullable().map(::getErrorText)
}

private fun getErrorText(message: UpdateXamlResultMessage?): String? {
    if (message?.exception == null && message?.error == null) return null

    val buffer = StringBuilder()
    val exception = message.exception
    if (exception != null) {
        val exceptionType = exception.exceptionType ?: "Error"
        buffer.append(exceptionType)
        if (exception.lineNumber != null || exception.linePosition != null) {
            buffer.append(" at ").append(when {
                exception.lineNumber != null && exception.linePosition != null ->
                    "${exception.lineNumber}:${exception.linePosition}"
                exception.lineNumber != null -> "${exception.lineNumber}"
                else -> "position ${exception.linePosition}"
            })
        }

        if (exception.message != null) {
            buffer.append(": ").append(exception.message)
        }
    }
    if (message.error != null) {
        if (message.exception != null) buffer.append("\n")
        buffer.append(message.error)
    }
    return buffer.toString()
}

private fun String.replaceLeadingSpace(replacement: String) =
    replace("(^|\\n)( +)".toRegex()) { result ->
        result.groups[1]!!.value + replacement.repeat(result.groups[2]!!.value.length)
    }
