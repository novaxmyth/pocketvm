package com.antidaze.pocketvm.core

import android.content.Context
import com.antidaze.pocketvm.model.VirtualInstance
import java.io.File
import java.util.Properties

class VirtualEnvironment(
    private val context: Context,
    val instance: VirtualInstance
) {
    private val vmManager = VirtualInstanceManager.get(context)
    private val systemDir = vmManager.getVmSubdir(instance.id, "system")
    private val buildPropFile = File(systemDir, "build.prop")
    private val props = Properties()

    init {
        loadProperties()
    }

    private fun loadProperties() {
        if (buildPropFile.isFile) {
            try {
                buildPropFile.inputStream().use { props.load(it) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getProp(key: String, default: String = ""): String {
        return props.getProperty(key, default)
    }

    fun setProp(key: String, value: String) {
        props.setProperty(key, value)
        saveProperties()
    }

    fun getAllProps(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (name in props.stringPropertyNames()) {
            map[name] = props.getProperty(name)
        }
        return map
    }

    private fun saveProperties() {
        try {
            buildPropFile.outputStream().use { props.store(it, "PocketVM Virtual System Properties") }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val isRootEnabled: Boolean get() = instance.rootConfig.isRootEnabled

    fun setRootEnabled(enabled: Boolean) {
        instance.rootConfig.isRootEnabled = enabled
        setProp("ro.pocketvm.root", enabled.toString())
        vmManager.saveInstance(instance)
    }
}
