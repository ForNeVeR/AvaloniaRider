package me.fornever.avaloniarider.previewer

import me.fornever.avaloniarider.controlmessages.InputModifiers
import me.fornever.avaloniarider.controlmessages.MouseButton
import me.fornever.avaloniarider.controlmessages.PointerMovedEventMessage
import me.fornever.avaloniarider.controlmessages.PointerPressedEventMessage
import me.fornever.avaloniarider.controlmessages.PointerReleasedEventMessage
import me.fornever.avaloniarider.controlmessages.ScrollEventMessage
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JLabel
import javax.swing.SwingConstants
import javax.swing.event.MouseInputAdapter


internal class AvaloniaMessageMouseListener(
    private val frameView: JLabel,
    private val controller: AvaloniaPreviewerSessionController
) : MouseInputAdapter() {

    override fun mousePressed(e: MouseEvent?) {
        e ?: return
        val coordinates = e.relCoordinatesOrNull() ?: return
        val message = PointerPressedEventMessage(
            e.avaloniaModifiers(),
            coordinates.first,
            coordinates.second,
            e.avaloniaMouseButton())
        controller.sendInputEventMessage(message)
    }

    override fun mouseReleased(e: MouseEvent?) {
        e ?: return
        val coordinates = e.relCoordinatesOrNull()?: return
        val message = PointerReleasedEventMessage(
            e.avaloniaModifiers(),
            coordinates.first,
            coordinates.second,
            e.avaloniaMouseButton())
        controller.sendInputEventMessage(message)
    }

    override fun mouseWheelMoved(e: MouseWheelEvent?) {
        e ?: return
        if (e.scrollType != MouseWheelEvent.WHEEL_UNIT_SCROLL) return
        val coordinates = e.relCoordinatesOrNull()?: return
        val message = ScrollEventMessage(
            e.avaloniaModifiers(),
            coordinates.first,
            coordinates.second,
            0.0,
            -e.preciseUnitsToScroll())
        controller.sendInputEventMessage(message)
    }

    override fun mouseMoved(e: MouseEvent?) {
        sendPointerMovedEventMessage(e)
    }

    override fun mouseDragged(e: MouseEvent?) {
        sendPointerMovedEventMessage(e)
    }

    private fun sendPointerMovedEventMessage(e: MouseEvent?) {
        e ?: return
        val coordinates = e.relCoordinatesOrNull()?: return
        val message = PointerMovedEventMessage(
            e.avaloniaModifiers(),
            coordinates.first,
            coordinates.second)
        controller.sendInputEventMessage(message)
    }

    private fun MouseWheelEvent.preciseUnitsToScroll(): Double {
        return this.preciseWheelRotation * this.scrollAmount
    }

    private fun MouseEvent.relCoordinatesOrNull(): Pair<Double, Double>? {
        frameView.icon ?: return null
        val relFirst = frameView.relFirstOrNull(this.x) ?: return null
        val relSecond = frameView.relSecondOrNull(this.y) ?: return null
        return Pair(relFirst, relSecond)
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

    private fun JLabel.relFirstOrNull(first: Int): Double? {
        var relFirst = first.toDouble()

        if (frameView.horizontalAlignment == SwingConstants.CENTER) {
            relFirst -= (this.width - this.icon.iconWidth) / 2.0
        } else if (frameView.horizontalAlignment == SwingConstants.RIGHT) {
            relFirst -= (this.width - this.icon.iconWidth).toDouble()
        }

        val isContains = 0 <= relFirst || relFirst <= frameView.icon.iconWidth

        return if (isContains) relFirst else null
    }

    private fun JLabel.relSecondOrNull(second: Int): Double? {
        var relSecond = second.toDouble()

        if (frameView.verticalAlignment == SwingConstants.CENTER) {
            relSecond -= (this.height - this.icon.iconHeight) / 2.0
        } else if (frameView.verticalAlignment == SwingConstants.BOTTOM) {
            relSecond -= (this.height - this.icon.iconHeight).toDouble()
        }

        val isContains = 0 <= relSecond || relSecond <= frameView.icon.iconHeight

        return if (isContains) relSecond else null
    }
}
