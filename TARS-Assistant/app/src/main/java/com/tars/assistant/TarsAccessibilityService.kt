package com.tars.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Only runs actions explicitly requested by the user inside TARS.
 * Screen text is not logged or transmitted by this service.
 */
class TarsAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun clickText(target: String): Boolean {
        val cleanTarget = target.trim()
        if (cleanTarget.isEmpty()) return false
        val root = rootInActiveWindow ?: return false
        return clickMatching(root, cleanTarget)
    }

    fun pressBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun goHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun openNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    private fun clickMatching(node: AccessibilityNodeInfo, target: String): Boolean {
        val text = node.text?.toString().orEmpty()
        val description = node.contentDescription?.toString().orEmpty()
        val matches = text.equals(target, true) ||
            description.equals(target, true) ||
            text.contains(target, true) ||
            description.contains(target, true)

        if (matches && node.isEnabled) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            var parent = node.parent
            var depth = 0
            while (parent != null && depth < 8) {
                if (parent.isEnabled && parent.isClickable &&
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                ) return true
                val next = parent.parent
                parent.recycle()
                parent = next
                depth++
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val clicked = clickMatching(child, target)
            child.recycle()
            if (clicked) return true
        }
        return false
    }

    companion object {
        @Volatile
        var instance: TarsAccessibilityService? = null
            private set
    }
}
