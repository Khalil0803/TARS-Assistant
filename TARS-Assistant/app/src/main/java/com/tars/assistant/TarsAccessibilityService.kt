package com.tars.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Starter accessibility service. Actions are intentionally limited to explicit user requests. */
class TarsAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* UI state can be observed here. */ }
    override fun onInterrupt() {}

    override fun onServiceConnected() {
        instance = this
        super.onServiceConnected()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun clickText(target: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return clickMatching(root, target.trim())
    }

    private fun clickMatching(node: AccessibilityNodeInfo, target: String): Boolean {
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        if ((text.equals(target, true) || desc.equals(target, true)) && node.isEnabled) {
            if (node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                parent = parent.parent
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (clickMatching(child, target)) return true
        }
        return false
    }

    companion object { @Volatile var instance: TarsAccessibilityService? = null }
}
