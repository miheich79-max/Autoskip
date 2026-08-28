package com.example.autoskip5

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class SkipAccessibilityService : AccessibilityService() {
    companion object {
        private const val CHROME_PACKAGE = "com.android.chrome"
        private const val DEBOUNCE_MS = 1_200L
        private const val MAX_PARENT_DEPTH = 10
        private const val MAX_TREE_DEPTH = 80
    }

    private val skipTerms = listOf(
        "skip ad",
        "skip ads",
        "skip",
        "skip video",
        "пропустить рекламу",
        "пропустить",
        "דלג על המודעה",
        "דלג",
    )

    private var lastAttemptTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != CHROME_PACKAGE) return

        val source = event.source
        if (source?.packageName?.toString() == CHROME_PACKAGE && inspect(source)) return

        val visitedWindowIds = HashSet<Int>(2)
        val activeRoot = rootInActiveWindow
        if (activeRoot?.packageName?.toString() == CHROME_PACKAGE) {
            visitedWindowIds += activeRoot.windowId
            if (activeRoot != source && inspect(activeRoot)) return
        }

        for (window in windows) {
            val root = window.root ?: continue
            if (root.packageName?.toString() != CHROME_PACKAGE) continue
            if (!visitedWindowIds.add(root.windowId)) continue
            if (inspect(root)) return
        }
    }

    private fun inspect(root: AccessibilityNodeInfo): Boolean {
        val candidate = findSkipNode(root) ?: return false
        val now = SystemClock.elapsedRealtime()
        if (now - lastAttemptTime < DEBOUNCE_MS) return true

        lastAttemptTime = now
        if (clickNodeOrParent(candidate)) return true
        dispatchTap(candidate)
        return true
    }

    private fun findSkipNode(node: AccessibilityNodeInfo, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth > MAX_TREE_DEPTH) return null

        if (node.isVisibleToUser) {
            val text = normalize(node.text)
            val description = normalize(node.contentDescription)
            if (matchesSkip(text) || matchesSkip(description)) return node
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            findSkipNode(child, depth + 1)?.let { return it }
        }
        return null
    }

    private fun normalize(value: CharSequence?): String {
        return value?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()
    }

    private fun matchesSkip(value: String): Boolean {
        if (value.isBlank()) return false
        return skipTerms.any { term ->
            value == term ||
                value.startsWith("$term ") ||
                value.startsWith("$term,") ||
                value.startsWith("$term.") ||
                value.startsWith("$term:")
        }
    }

    private fun clickNodeOrParent(candidate: AccessibilityNodeInfo): Boolean {
        if (candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true

        var parent = candidate.parent
        repeat(MAX_PARENT_DEPTH) {
            val current = parent ?: return false
            if (
                current.isVisibleToUser &&
                current.isClickable &&
                current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ) {
                return true
            }
            parent = current.parent
        }
        return false
    }

    private fun dispatchTap(candidate: AccessibilityNodeInfo): Boolean {
        val bounds = Rect().also(candidate::getBoundsInScreen)
        if (bounds.isEmpty) return false

        val path = Path().apply {
            moveTo(bounds.exactCenterX(), bounds.exactCenterY())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 100L))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    override fun onInterrupt() = Unit
}
