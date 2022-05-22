package me.fornever.avaloniarider.previewer

import com.jetbrains.rd.util.reactive.IPropertyView
import com.jetbrains.rd.util.reactive.ISource
import com.jetbrains.rd.util.reactive.Property
import com.jetbrains.rd.util.reactive.Signal
import me.fornever.avaloniarider.controlmessages.*
import me.fornever.avaloniarider.idea.editor.PreviewImageView
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.event.MouseInputAdapter

/**
 * Pass the PreviewImageView instance in order to find out the position of the pointer to the image.
 */
internal class AvaloniaMessageMouseListener(
    private val frameView: PreviewImageView
) : MouseInputAdapter() {

    private val avaloniaInputEventSignal = Signal<AvaloniaInputEventMessage>()

    val avaloniaInputEvent: ISource<AvaloniaInputEventMessage> = avaloniaInputEventSignal

    private val zoomProperty = Property(1.0)
    val zoom: IPropertyView<Double> = zoomProperty

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

        if (e.isControlDown) {
            val oldValue = zoom.value
            val change = e.preciseUnitsToScroll().coerceIn(-1.0, 1.0)
            var newValue = (oldValue - change).coerceIn(0.4, 10.0) // scroll down means zoom out
            if (oldValue < 1.0 && newValue > 1.0 || oldValue > 1.0 && newValue < 1.0) {
                newValue = 1.0 // always make a stop at 1.0 scale
            }

            zoomProperty.value = newValue
            return
        }

        val coordinates = e.pointerPositionOrNull() ?: return
        val message = ScrollEventMessage(
            e.avaloniaModifiers(),
            coordinates.first,
            coordinates.second,
            0.0,
            -e.preciseUnitsToScroll()
        )
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
            coordinates.second
        )
        avaloniaInputEventSignal.fire(message)
    }

    private fun MouseWheelEvent.preciseUnitsToScroll(): Double =
        this.preciseWheelRotation * this.scrollAmount

    private fun MouseEvent.pointerPositionOrNull(): Pair<Double, Double>? {
        frameView.buffer ?: return null

        val iconX = this.x - frameView.shiftImageX
        val isContainsX = 0 <= iconX && iconX <= (frameView.buffer?.width ?: 0)
        if (!isContainsX) return null

        val iconY = this.y - frameView.shiftImageY
        val isContainsY = 0 <= iconY && iconY <= (frameView.buffer?.height ?: 0)
        if (!isContainsY) return null

        return iconX.toDouble() / zoom.value to iconY.toDouble() / zoom.value
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
}
