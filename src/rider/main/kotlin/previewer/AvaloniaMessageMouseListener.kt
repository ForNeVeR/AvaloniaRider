package me.fornever.avaloniarider.previewer

import com.jetbrains.rd.util.reactive.ISource
import com.jetbrains.rd.util.reactive.Signal
import me.fornever.avaloniarider.controlmessages.*
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JLabel
import javax.swing.SwingConstants
import javax.swing.event.MouseInputAdapter

/**
 * Pass the JLabel instance in order to find out the position of the pointer to the Icon
 */
internal class AvaloniaMessageMouseListener(
    private val frameView: JLabel
) : MouseInputAdapter() {

    private val avaloniaInputEventSignal = Signal<AvaloniaInputEventMessage>()

    val avaloniaInputEvent: ISource<AvaloniaInputEventMessage> = avaloniaInputEventSignal

    override fun mousePressed(e: MouseEvent?) {
        e ?: return
        val coordinates = e.pointerPositionOrNull() ?: return
        val message = PointerPressedEventMessage(
            e.avaloniaModifiers(),
            coordinates.first,
            coordinates.second,
            e.avaloniaMouseButton())
        avaloniaInputEventSignal.fire(message)
    }

    override fun mouseReleased(e: MouseEvent?) {
        e ?: return
        val coordinates = e.pointerPositionOrNull()?: return
        val message = PointerReleasedEventMessage(
            e.avaloniaModifiers(),
            coordinates.first,
            coordinates.second,
            e.avaloniaMouseButton())
        avaloniaInputEventSignal.fire(message)
    }

    override fun mouseWheelMoved(e: MouseWheelEvent?) {
        e ?: return
        if (e.scrollType != MouseWheelEvent.WHEEL_UNIT_SCROLL) return
        val coordinates = e.pointerPositionOrNull()?: return
        val message = ScrollEventMessage(
            e.avaloniaModifiers(),
            coordinates.first,
            coordinates.second,
            0.0,
            -e.preciseUnitsToScroll())
        avaloniaInputEventSignal.fire(message)
    }

    override fun mouseMoved(e: MouseEvent?) = sendPointerMovedEventMessage(e)

    override fun mouseDragged(e: MouseEvent?) = sendPointerMovedEventMessage(e)

    private fun sendPointerMovedEventMessage(e: MouseEvent?) {
        e ?: return
        val coordinates = e.pointerPositionOrNull()?: return
        val message = PointerMovedEventMessage(
            e.avaloniaModifiers(),
            coordinates.first,
            coordinates.second)
        avaloniaInputEventSignal.fire(message)
    }

    private fun MouseWheelEvent.preciseUnitsToScroll(): Double =
        this.preciseWheelRotation * this.scrollAmount

    private fun MouseEvent.pointerPositionOrNull(): Pair<Double, Double>? {
        frameView.icon ?: return null

        val iconX = this.x - frameView.shiftIconX()
        val isContainsX = 0 <= iconX && iconX <= frameView.icon.iconWidth
        if (!isContainsX) return null

        val iconY = this.y - frameView.shiftIconY()
        val isContainsY = 0 <= iconY && iconY <= frameView.icon.iconHeight
        if (!isContainsY) return null

        return iconX to iconY
    }

    private fun MouseEvent.avaloniaModifiers(): Array<Int> {
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

    private fun MouseEvent.avaloniaMouseButton(): Int = when (this.button) {
        MouseEvent.BUTTON1 -> MouseButton.Left.ordinal
        MouseEvent.BUTTON2 -> MouseButton.Middle.ordinal
        MouseEvent.BUTTON3 -> MouseButton.Right.ordinal
        else -> MouseButton.None.ordinal
    }

    private fun JLabel.shiftIconX(): Double = when (this.horizontalAlignment) {
        SwingConstants.CENTER -> (this.width - this.icon.iconWidth) / 2.0
        SwingConstants.RIGHT -> (this.width - this.icon.iconWidth).toDouble()
        else -> 0.0
    }

    private fun JLabel.shiftIconY(): Double = when (this.verticalAlignment) {
        SwingConstants.CENTER -> (this.height - this.icon.iconHeight) / 2.0
        SwingConstants.BOTTOM -> (this.height - this.icon.iconHeight).toDouble()
        else -> 0.0
    }
}
