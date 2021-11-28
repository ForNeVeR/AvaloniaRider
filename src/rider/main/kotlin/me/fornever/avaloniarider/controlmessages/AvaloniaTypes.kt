package me.fornever.avaloniarider.controlmessages

data class ExceptionDetails(
    val exceptionType: String? = "",
    val message: String? = "",
    val lineNumber: Int? = null,
    val linePosition: Int? = null
)

enum class InputModifiers {
    Alt,
    Control,
    Shift,
    Windows,
    LeftMouseButton,
    RightMouseButton,
    MiddleMouseButton,
}

enum class MouseButton {
    None,
    Left,
    Right,
    Middle,
}
