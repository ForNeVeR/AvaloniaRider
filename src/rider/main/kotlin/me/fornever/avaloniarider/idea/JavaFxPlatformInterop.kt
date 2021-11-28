package me.fornever.avaloniarider.idea

import com.intellij.openapi.components.Service
import com.intellij.util.application
import javafx.application.Platform

// TODO[F]: Drop this class after implementation of #74
@Service
class JavaFxPlatformInterop {
    companion object {
        fun getInstance() = application.getService(JavaFxPlatformInterop::class.java)
        fun initialize() = getInstance().initialize()
    }

    fun initialize() {
        Platform.setImplicitExit(false)
    }
}
