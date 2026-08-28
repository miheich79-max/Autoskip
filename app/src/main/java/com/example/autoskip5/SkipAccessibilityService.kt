package com.example.autoskip5

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque
import java.util.Locale

class SkipAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "AutoSkip"
        private const val CHROME_PACKAGE = "com.android.chrome"
        private const val DEBOUNCE_MS = 1_200L
        private const val TREE_CAPTURE_INTERVAL_MS = 750L
        private const val MAX_TREE_NODES = 180
        private const val MAX_PARENT_DEPTH = 10
    }

    private val skipTerms = listOf(
        "skip ad",
        "skip ads",
        "skip video",
        "skip",
        "пропустить рекламу",
        "пропустить",
        "דלג על המודעה",
        "דלג",
    )

    private var lastClickTime = 0L
    private var lastTreeCaptureTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        DiagnosticStore.recordServiceStatus(this, "Accessibility service connected")
        Log.i(TAG, "Accessibility service connected; package scope=$CHROME_PACKAGE")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != CHROME_PACKAGE) return

        val now = System.currentTimeMillis()
        val eventName = AccessibilityEvent.eventTypeToString(event.eventType)
        DiagnosticStore.recordChromeEvent(this, eventName, now)

        var candidateFound = false
        var clickAttempted = false
        var validRootCounted = false
        var validRootFound = false

        fun inspect(node: AccessibilityNodeInfo, route: String) {
            val candidate = findSkipNode(node) ?: return
            candidateFound = true
            val description = describeNode(candidate)
            DiagnosticStore.recordCandidate(this, now, route, description)
            Log.i(TAG, "Skip candidate via $route: $description")

            if (!clickAttempted && now - lastClickTime >= DEBOUNCE_MS) {
                clickAttempted = true
                lastClickTime = now
                attemptClick(candidate, route, now)
            }
        }

        // The event source is often available even when Chrome is not the active window.
        val source = event.source
        if (source == null) {
            DiagnosticStore.recordSourceStatus(this, "Latest Chrome event.source was null; preserved prior snapshot")
        } else {
            val sourceSnapshot = dumpTree(source, "event.source")
            DiagnosticStore.recordSource(this, now, sourceSnapshot)
            logSnapshot("event.source", sourceSnapshot)
            if (source.packageName?.toString() == CHROME_PACKAGE) inspect(source, "event.source")
        }

        val activeRoot = rootInActiveWindow
        if (activeRoot?.packageName?.toString() == CHROME_PACKAGE) {
            validRootFound = true
            val tree = captureTreeIfDue(activeRoot, "rootInActiveWindow", now)
            DiagnosticStore.recordChromeRoot(
                this,
                now,
                CHROME_PACKAGE,
                "rootInActiveWindow",
                tree,
                incrementCount = !validRootCounted,
            )
            validRootCounted = true
            inspect(activeRoot, "rootInActiveWindow")
        }

        windows.forEachIndexed { index, window ->
            val root = window.root ?: return@forEachIndexed
            if (root.packageName?.toString() != CHROME_PACKAGE) return@forEachIndexed

            validRootFound = true
            val route = "windows[$index] type=${window.type}"
            val tree = captureTreeIfDue(root, route, now)
            DiagnosticStore.recordChromeRoot(
                this,
                now,
                CHROME_PACKAGE,
                route,
                tree,
                incrementCount = !validRootCounted,
            )
            validRootCounted = true
            inspect(root, route)
        }

        val status = when {
            candidateFound && clickAttempted -> "Candidate found; click attempted"
            candidateFound -> "Candidate found; debounce active"
            validRootFound -> "Chrome root found; no skip candidate in this event"
            source?.packageName?.toString() == CHROME_PACKAGE ->
                "Chrome event.source found; no full Chrome root and no skip candidate"
            else -> "No Chrome source/root in this event; preserved prior successful diagnostics"
        }
        DiagnosticStore.recordScanStatus(this, status)
        Log.d(TAG, "$eventName: $status")
    }

    private fun captureTreeIfDue(
        root: AccessibilityNodeInfo,
        route: String,
        now: Long,
    ): String? {
        if (now - lastTreeCaptureTime < TREE_CAPTURE_INTERVAL_MS) return null
        lastTreeCaptureTime = now
        return dumpTree(root, route).also { logSnapshot(route, it) }
    }

    private fun findSkipNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val text = normalize(node.text)
            val description = normalize(node.contentDescription)
            if (node.isVisibleToUser && (matchesSkip(text) || matchesSkip(description))) {
                return node
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
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

    private fun attemptClick(candidate: AccessibilityNodeInfo, route: String, timestamp: Long) {
        val actionResults = mutableListOf<String>()
        var node: AccessibilityNodeInfo? = candidate

        repeat(MAX_PARENT_DEPTH) { depth ->
            val current = node ?: return@repeat
            val result = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            actionResults += "ACTION_CLICK depth=$depth clickable=${current.isClickable} result=$result"
            if (result) {
                val attempt = "Route=$route\n${actionResults.joinToString("\n")}"
                DiagnosticStore.recordClickAttempt(this, timestamp, attempt, "ACTION_CLICK succeeded")
                Log.i(TAG, "ACTION_CLICK succeeded via $route at parent depth $depth")
                return
            }
            node = current.parent
        }

        val bounds = Rect().also(candidate::getBoundsInScreen)
        val attempt = buildString {
            appendLine("Route=$route")
            appendLine(actionResults.joinToString("\n"))
            append("Gesture center=(${bounds.centerX()}, ${bounds.centerY()}) bounds=$bounds")
        }

        if (bounds.isEmpty) {
            DiagnosticStore.recordClickAttempt(this, timestamp, attempt, "Gesture not dispatched: empty bounds")
            Log.w(TAG, "Click failed and candidate bounds were empty")
            return
        }

        val path = Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 120L))
            .build()
        val accepted = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    DiagnosticStore.updateClickResult(this@SkipAccessibilityService, "Gesture completed")
                    Log.i(TAG, "Fallback gesture completed")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    DiagnosticStore.updateClickResult(this@SkipAccessibilityService, "Gesture cancelled")
                    Log.w(TAG, "Fallback gesture cancelled")
                }
            },
            null,
        )
        val result = if (accepted) "Gesture dispatch accepted; awaiting callback" else "Gesture dispatch rejected"
        DiagnosticStore.recordClickAttempt(this, timestamp, attempt, result)
        Log.i(TAG, result)
    }

    private fun dumpTree(root: AccessibilityNodeInfo, route: String): String {
        data class PendingNode(val node: AccessibilityNodeInfo, val depth: Int)

        val queue = ArrayDeque<PendingNode>()
        val lines = mutableListOf<String>()
        queue.add(PendingNode(root, 0))

        while (queue.isNotEmpty() && lines.size < MAX_TREE_NODES) {
            val (node, depth) = queue.removeFirst()
            lines += "#${lines.size + 1} depth=$depth ${describeNode(node)}"
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { queue.addLast(PendingNode(it, depth + 1)) }
            }
        }

        return buildString {
            appendLine("route=$route nodes=${lines.size}${if (queue.isNotEmpty()) " (truncated)" else ""}")
            append(lines.joinToString("\n"))
        }
    }

    private fun describeNode(node: AccessibilityNodeInfo): String {
        val bounds = Rect().also(node::getBoundsInScreen)
        return "text=\"${node.text?.toString().orEmpty()}\" " +
            "contentDescription=\"${node.contentDescription?.toString().orEmpty()}\" " +
            "className=${node.className?.toString().orEmpty()} " +
            "clickable=${node.isClickable} " +
            "viewIdResourceName=${node.viewIdResourceName.orEmpty()} " +
            "bounds=$bounds"
    }

    private fun logSnapshot(label: String, snapshot: String) {
        snapshot.chunked(3_000).forEachIndexed { index, chunk ->
            Log.d(TAG, "$label snapshot part ${index + 1}: $chunk")
        }
    }

    override fun onInterrupt() {
        DiagnosticStore.recordServiceStatus(this, "Accessibility service interrupted")
        Log.w(TAG, "Accessibility service interrupted")
    }
}
