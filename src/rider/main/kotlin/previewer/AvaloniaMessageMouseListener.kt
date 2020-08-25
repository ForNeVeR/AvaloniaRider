package me.fornever.avaloniarider.previewer

import com.intellij.openapi.diagnostic.Logger
import me.fornever.avaloniarider.controlmessages.*
import java.awt.event.MouseEvent
import javax.swing.event.MouseInputAdapter


internal class AvaloniaMessageMouseListener(
    private val controller: AvaloniaPreviewerSessionController,
    private val logger: Logger
) : MouseInputAdapter() {

    override fun mousePressed(e: MouseEvent) {
        val message = PointerPressedEventMessage(
            e.avaloniaModifiers(),
            e.x.toDouble(),
            e.y.toDouble(),
            e.avaloniaMouseButton().ordinal)
        controller.sendInputEventMessage(message)
    }

    override fun mouseReleased(e: MouseEvent) {
        val message = PointerReleasedEventMessage(
            e.avaloniaModifiers(),
            e.x.toDouble(),
            e.y.toDouble(),
            e.avaloniaMouseButton().ordinal)
        controller.sendInputEventMessage(message)
    }

    override fun mouseMoved(e: MouseEvent) {
        sendPointerMovedEventMessage(e)
    }

    override fun mouseDragged(e: MouseEvent) {
        sendPointerMovedEventMessage(e)
    }

    private fun sendPointerMovedEventMessage(e: MouseEvent) {
        val message = PointerMovedEventMessage(
            e.avaloniaModifiers(),
            e.x.toDouble(),
            e.y.toDouble())
        controller.sendInputEventMessage(message)
    }

    private fun MouseEvent.avaloniaModifiers() : Array<InputModifiers> {
        val result = mutableListOf<InputModifiers>()

        val m = this.modifiersEx
        if ((m and MouseEvent.ALT_DOWN_MASK) != 0)
            result.add(InputModifiers.Alt)
        if ((m and MouseEvent.CTRL_DOWN_MASK) != 0)
            result.add(InputModifiers.Control)
        if ((m and MouseEvent.SHIFT_DOWN_MASK) != 0)
            result.add(InputModifiers.Shift)
        if ((m and MouseEvent.META_DOWN_MASK) != 0)
            result.add(InputModifiers.Windows)
        if ((m and MouseEvent.BUTTON1_DOWN_MASK) != 0)
            result.add(InputModifiers.LeftMouseButton)
        if ((m and MouseEvent.BUTTON2_DOWN_MASK) != 0)
            result.add(InputModifiers.MiddleMouseButton)
        if ((m and MouseEvent.BUTTON3_DOWN_MASK) != 0)
            result.add(InputModifiers.RightMouseButton)

        return result.toTypedArray()
    }

    private fun MouseEvent.avaloniaMouseButton() : MouseButton {
        return when (this.button) {
            MouseEvent.BUTTON1 -> MouseButton.Left
            MouseEvent.BUTTON2 -> MouseButton.Middle
            MouseEvent.BUTTON3 -> MouseButton.Right
            else -> MouseButton.None
        }
    }
}
