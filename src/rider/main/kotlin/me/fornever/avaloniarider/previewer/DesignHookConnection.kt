package me.fornever.avaloniarider.previewer

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.onTermination
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path

internal data class DesignElementPosition(val line: Int, val column: Int)

/**
 * The connection to the design hook, which runs inside the previewer process and resolves points of the preview to XAML
 * elements.
 */
internal class DesignHookConnection(lifetime: Lifetime) {

    companion object {
        private const val pluginId = "avalonia-rider"
        private const val hookAssemblyPath = "design-hook/AvaloniaRider.DesignHook.dll"
        private const val portVariableName = "AVALONIARIDER_DESIGN_HOOK_PORT"
        private const val startupHooksVariableName = "DOTNET_STARTUP_HOOKS"

        /** The hook connects while the previewer starts, so by the time of a click it should have already connected. */
        private const val connectionTimeoutMs = 1000
        private const val responseTimeoutMs = 5000

        fun findHookAssembly(): Path? =
            PluginManagerCore.getPlugin(PluginId.getId(pluginId))
                ?.pluginPath
                ?.resolve(hookAssemblyPath)
                ?.takeIf { Files.isRegularFile(it) }

        internal fun parseResponse(response: String): DesignElementPosition? {
            if (response == "-") return null
            val parts = response.split(' ')
            return DesignElementPosition(parts[0].toInt(), parts[1].toInt())
        }
    }

    private val serverSocket = ServerSocket(0, 1, InetAddress.getLoopbackAddress())

    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null

    init {
        lifetime.onTermination {
            serverSocket.close()
            synchronized(this) { socket?.close() }
        }
    }

    fun getEnvironment(hookAssembly: Path): Map<String, String> {
        val existingHooks = System.getenv(startupHooksVariableName)
        return mapOf(
            startupHooksVariableName to listOfNotNull(existingHooks, hookAssembly.toString()).joinToString(File.pathSeparator),
            portVariableName to serverSocket.localPort.toString()
        )
    }

    /**
     * Finds the element at the point, and selects the tab there, if any.
     *
     * @return the element position, or `null` if there's no element at the point.
     * @throws IOException if the hook isn't available, e.g., because it doesn't support this version of Avalonia.
     */
    @Synchronized
    fun click(x: Double, y: Double): DesignElementPosition? {
        connectIfNeeded()
        writer!!.apply {
            write("click $x $y\n")
            flush()
        }
        val response = reader!!.readLine() ?: throw IOException("The design hook has closed the connection.")
        return parseResponse(response)
    }

    private fun connectIfNeeded() {
        if (socket != null) return

        serverSocket.soTimeout = connectionTimeoutMs
        val accepted = serverSocket.accept().apply { soTimeout = responseTimeoutMs }
        reader = accepted.getInputStream().bufferedReader(Charsets.UTF_8)
        writer = accepted.getOutputStream().bufferedWriter(Charsets.UTF_8)
        socket = accepted
    }
}
