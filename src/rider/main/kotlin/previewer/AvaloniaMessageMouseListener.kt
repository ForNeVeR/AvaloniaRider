package me.fornever.avaloniarider.previewer

import me.fornever.avaloniarider.controlmessages.InputModifiers
import me.fornever.avaloniarider.controlmessages.MouseButton
import me.fornever.avaloniarider.controlmessages.PointerMovedEventMessage
import me.fornever.avaloniarider.controlmessages.PointerPressedEventMessage
import me.fornever.avaloniarider.controlmessages.PointerReleasedEventMessage
import java.awt.event.MouseEvent
import javax.swing.event.MouseInputAdapter


internal class AvaloniaMessageMouseListener(
    private val controller: AvaloniaPreviewerSessionController
) : MouseInputAdapter() {

    override fun mousePressed(e: MouseEvent) {
        val message = PointerPressedEventMessage(
            e.avaloniaModifiers(),
            e.x.toDouble(),
            e.y.toDouble(),
            e.avaloniaMouseButton())
        controller.sendInputEventMessage(message)
    }

    override fun mouseReleased(e: MouseEvent) {
        val message = PointerReleasedEventMessage(
            e.avaloniaModifiers(),
            e.x.toDouble(),
            e.y.toDouble(),
            e.avaloniaMouseButton())
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

    private fun MouseEvent.avaloniaModifiers() : Array<Int> {
        val result = mutableListOf<Int>()

        val m = this.modifiersEx
        if ((m and MouseEvent.ALT_DOWN_MASK) != 0)
            result.add(InputModifiers.Alt.ordinal)
        if ((m and MouseEvent.CTRL_DOWN_MASK) != 0)
            result.add(InputModifiers.Control.ordinal)
        if ((m and MouseEvent.SHIFT_DOWN_MASK) != 0)
            result.add(InputModifiers.Shift.ordinal)
        if ((m and MouseEvent.META_DOWN_MASK) != 0)
            result.add(InputModifiers.Windows.ordinal)
        if ((m and MouseEvent.BUTTON1_DOWN_MASK) != 0)
            result.add(InputModifiers.LeftMouseButton.ordinal)
        if ((m and MouseEvent.BUTTON2_DOWN_MASK) != 0)
            result.add(InputModifiers.MiddleMouseButton.ordinal)
        if ((m and MouseEvent.BUTTON3_DOWN_MASK) != 0)
            result.add(InputModifiers.RightMouseButton.ordinal)

        return result.toTypedArray()
    }

    private fun MouseEvent.avaloniaMouseButton() : Int {
        return when (this.button) {
            MouseEvent.BUTTON1 -> MouseButton.Left.ordinal
            MouseEvent.BUTTON2 -> MouseButton.Middle.ordinal
            MouseEvent.BUTTON3 -> MouseButton.Right.ordinal
            else -> MouseButton.None.ordinal
        }
    }
}
