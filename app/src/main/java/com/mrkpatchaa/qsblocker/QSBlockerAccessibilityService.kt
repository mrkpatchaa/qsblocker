package com.mrkpatchaa.qsblocker

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class QSBlockerAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "QSBlockerService"

        // View IDs that exist ONLY when the QS Edit/Customize screen is inflated
        private val QS_EDIT_UNIQUE_IDS = listOf(
            "com.android.systemui:id/customize_custom_action_bar_title",
            "com.android.systemui:id/customize_custom_action_bar_hide",
            "com.android.systemui:id/customize_container",
            "com.android.systemui:id/qs_customize_grid",
            "com.android.systemui:id/customize_custom_action_bar",
        )

        // View IDs of the Edit button container in Quick Settings footer
        private val EDIT_BUTTON_IDS = listOf(
            "android:id/edit",
            "com.android.systemui:id/edit",
        )

        // Text patterns present ONLY on the QS Edit screen instructions
        private val EDIT_INSTRUCTION_PATTERNS = listOf(
            "hold and drag",
            "hold & drag",
            "drag tiles",
            "drag to add",
            "drag to rearrange",
            "faites glisser les blocs",
            "glisser les blocs",
            "arrastrar les",
            "ziehen sie",
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        val isLocked = prefs.getBoolean(MainActivity.KEY_IS_LOCKED, true)
        if (!isLocked) return

        // 1. Intercept explicit click on the Edit button (or its child icon)
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val source = event.source
            if (source != null && isEditButtonNode(source)) {
                Log.d(TAG, "Edit button click intercepted. Blocking edit.")
                blockQuickSettingsEditing()
                return
            }
        }

        // 2. Check interactive windows to see if the Edit screen itself is present
        val activeWindows = windows
        if (!activeWindows.isNullOrEmpty()) {
            for (window in activeWindows) {
                val root = window.root ?: continue
                if (checkNodeForQsEditScreen(root)) {
                    Log.d(TAG, "QS Edit screen detected in window: ${window.title}")
                    blockQuickSettingsEditing()
                    return
                }
            }
        } else {
            // Fallback to rootInActiveWindow
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                if (checkNodeForQsEditScreen(rootNode)) {
                    Log.d(TAG, "QS Edit screen detected in rootInActiveWindow")
                    blockQuickSettingsEditing()
                    return
                }
            }
        }
    }

    private fun isEditButtonNode(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        var depth = 0
        while ((current != null) && (depth < 3)) {
            val id = current.viewIdResourceName
            if ((id != null) && EDIT_BUTTON_IDS.any { id.equals(it, ignoreCase = true) }) {
                return true
            }
            current = current.parent
            depth++
        }
        return false
    }

    private fun checkNodeForQsEditScreen(rootNode: AccessibilityNodeInfo): Boolean {
        // A. Check for View IDs unique to the Edit screen
        for (id in QS_EDIT_UNIQUE_IDS) {
            val matchingNodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            if (!matchingNodes.isNullOrEmpty()) {
                return true
            }
        }

        // B. Check for instruction text patterns unique to the Edit screen
        return scanNodeTreeForEditInstructions(rootNode)
    }

    private fun scanNodeTreeForEditInstructions(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        // Check Class Name specifically for QSCustomizer class
        val className = node.className?.toString() ?: ""
        if (className.endsWith("QSCustomizer", ignoreCase = true)) {
            return true
        }

        // Check Text / Content Description for instruction patterns
        val text = node.text?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""

        for (pattern in EDIT_INSTRUCTION_PATTERNS) {
            if (text.contains(pattern) || contentDesc.contains(pattern)) {
                return true
            }
        }

        // Recursively search children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val found = scanNodeTreeForEditInstructions(child)
                if (found) return true
            }
        }

        return false
    }

    private fun blockQuickSettingsEditing() {
        // Press BACK to exit the edit screen and return to normal Quick Settings panel
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    override fun onInterrupt() {}
}
