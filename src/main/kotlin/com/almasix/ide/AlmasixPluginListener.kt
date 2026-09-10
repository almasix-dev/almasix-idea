package com.almasix.ide

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.ProjectManager
import com.redhat.devtools.lsp4ij.LanguageServerManager

/**
 * Stops ``almasix-lsp`` before the plugin is unloaded / disabled.
 *
 * Without this, LSP4IJ keeps wrappers that pin the Almasix classloader, the
 * IDE hangs on "Unloading Almasix", and a failed unload leaves the plugin
 * installed. Paired with ``require-restart="true"`` on the plugin root.
 */
class AlmasixPluginListener : DynamicPluginListener {
    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        if (pluginDescriptor.pluginId.idString != PLUGIN_ID) return
        stopEverywhere("beforePluginUnload")
    }

    override fun checkUnloadPlugin(pluginDescriptor: IdeaPluginDescriptor) {
        if (pluginDescriptor.pluginId.idString != PLUGIN_ID) return
        // Prefer a clean stop even when the IDE will still require a restart.
        stopEverywhere("checkUnloadPlugin")
    }

    companion object {
        const val PLUGIN_ID: String = "com.almasix.ide"
        const val SERVER_ID: String = "almasixLsp"
        private val LOG = logger<AlmasixPluginListener>()

        fun stopEverywhere(reason: String) {
            for (project in ProjectManager.getInstance().openProjects) {
                if (project.isDisposed) continue
                try {
                    LanguageServerManager.getInstance(project).stop(SERVER_ID)
                    LOG.info("Stopped $SERVER_ID in ${project.name} ($reason)")
                } catch (t: Throwable) {
                    LOG.warn("Failed to stop $SERVER_ID in ${project.name} ($reason)", t)
                }
            }
        }
    }
}
