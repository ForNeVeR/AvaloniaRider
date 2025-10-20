package me.fornever.avaloniarider.test.framework

import com.jetbrains.rider.test.tooling.manager.PublicTestToolsManager
import com.jetbrains.rider.test.tooling.manager.TestToolsManager
import com.jetbrains.rider.test.tooling.manager.TestToolsManagerFactory

class AvaloniaRiderTestToolsManagerFactory : TestToolsManagerFactory {

    override fun create(): TestToolsManager {
        val base = PublicTestToolsManager()
        return object : TestToolsManager by base {
        }
    }
}
