package com.nexrtz.loader

object Config {
    const val SERVER_HOST = "172.16.22.123" // Your IP
    const val SERVER_PORT = 5000
    const val UA = "SecureClient/1.0"
    val AES_KEY = "12345678901234567890123456789012".toByteArray(Charsets.UTF_8)
    val AES_IV = "1234567890123456".toByteArray(Charsets.UTF_8)
    fun serverBase() = "http://$SERVER_HOST:$SERVER_PORT"
}