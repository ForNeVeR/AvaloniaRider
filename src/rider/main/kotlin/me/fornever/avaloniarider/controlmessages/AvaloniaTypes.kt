package me.fornever.avaloniarider.controlmessages

import com.fasterxml.jackson.annotation.JsonProperty

data class ExceptionDetails(
    @JsonProperty("ExceptionType") val exceptionType: String? = "",
    @JsonProperty("Message") val message: String? = "",
    @JsonProperty("LineNumber") val lineNumber: Int? = null,
    @JsonProperty("LinePosition") val linePosition: Int? = null
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
