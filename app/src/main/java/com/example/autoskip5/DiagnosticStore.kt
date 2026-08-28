package com.example.autoskip5

import android.content.Context
import android.content.SharedPreferences

object DiagnosticStore {
    private const val PREFERENCES = "autoskip_diagnostics_v06"

    private const val CHROME_EVENT_COUNT = "chrome_event_count"
    private const val CHROME_ROOT_FOUND_COUNT = "chrome_root_found_count"
    private const val LAST_EVENT = "last_event"
    private const val LAST_EVENT_TIME = "last_event_time"
    private const val LAST_SCAN_STATUS = "last_scan_status"
    private const val LAST_ROOT_TIME = "last_root_time"
    private const val LAST_ROOT_PACKAGE = "last_root_package"
    private const val LAST_ROOT_ROUTE = "last_root_route"
    private const val LAST_TREE = "last_tree"
    private const val LAST_SOURCE_TIME = "last_source_time"
    private const val LAST_SOURCE_SNAPSHOT = "last_source_snapshot"
    private const val LAST_SOURCE_STATUS = "last_source_status"
    private const val LAST_CANDIDATE_TIME = "last_candidate_time"
    private const val LAST_CANDIDATE_ROUTE = "last_candidate_route"
    private const val LAST_CANDIDATE = "last_candidate"
    private const val LAST_CLICK_TIME = "last_click_time"
    private const val LAST_CLICK_ATTEMPT = "last_click_attempt"
    private const val LAST_CLICK_RESULT = "last_click_result"
    private const val SERVICE_STATUS = "service_status"

    @Volatile
    private var preferences: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        return preferences ?: synchronized(this) {
            preferences ?: context.applicationContext
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .also { preferences = it }
        }
    }

    fun recordChromeEvent(context: Context, eventName: String, timestamp: Long) {
        val prefs = prefs(context)
        prefs.edit()
            .putLong(CHROME_EVENT_COUNT, prefs.getLong(CHROME_EVENT_COUNT, 0L) + 1L)
            .putString(LAST_EVENT, eventName)
            .putLong(LAST_EVENT_TIME, timestamp)
            .apply()
    }

    fun recordSource(context: Context, timestamp: Long, snapshot: String) {
        prefs(context).edit()
            .putLong(LAST_SOURCE_TIME, timestamp)
            .putString(LAST_SOURCE_SNAPSHOT, snapshot)
            .putString(LAST_SOURCE_STATUS, "Chrome event.source captured")
            .apply()
    }

    fun recordSourceStatus(context: Context, status: String) {
        prefs(context).edit().putString(LAST_SOURCE_STATUS, status).apply()
    }

    fun recordChromeRoot(
        context: Context,
        timestamp: Long,
        packageName: String,
        route: String,
        treeSnapshot: String?,
        incrementCount: Boolean,
    ) {
        val prefs = prefs(context)
        val edit = prefs.edit()
            .putLong(LAST_ROOT_TIME, timestamp)
            .putString(LAST_ROOT_PACKAGE, packageName)
            .putString(LAST_ROOT_ROUTE, route)

        if (incrementCount) {
            edit.putLong(
                CHROME_ROOT_FOUND_COUNT,
                prefs.getLong(CHROME_ROOT_FOUND_COUNT, 0L) + 1L,
            )
        }
        if (treeSnapshot != null) edit.putString(LAST_TREE, treeSnapshot)
        edit.apply()
    }

    fun recordCandidate(context: Context, timestamp: Long, route: String, description: String) {
        prefs(context).edit()
            .putLong(LAST_CANDIDATE_TIME, timestamp)
            .putString(LAST_CANDIDATE_ROUTE, route)
            .putString(LAST_CANDIDATE, description)
            .apply()
    }

    fun recordClickAttempt(context: Context, timestamp: Long, attempt: String, result: String) {
        prefs(context).edit()
            .putLong(LAST_CLICK_TIME, timestamp)
            .putString(LAST_CLICK_ATTEMPT, attempt)
            .putString(LAST_CLICK_RESULT, result)
            .apply()
    }

    fun updateClickResult(context: Context, result: String) {
        prefs(context).edit().putString(LAST_CLICK_RESULT, result).apply()
    }

    fun recordScanStatus(context: Context, status: String) {
        prefs(context).edit().putString(LAST_SCAN_STATUS, status).apply()
    }

    fun recordServiceStatus(context: Context, status: String) {
        prefs(context).edit().putString(SERVICE_STATUS, status).apply()
    }

    fun report(context: Context): String {
        val prefs = prefs(context)
        return buildString {
            appendLine("AutoSkip 0.6 diagnostics (preserved on device)")
            appendLine()
            appendLine("Service status: ${prefs.text(SERVICE_STATUS, "Not connected yet")}")
            appendLine("Total Chrome events: ${prefs.getLong(CHROME_EVENT_COUNT, 0L)}")
            appendLine("Chrome events with a valid root: ${prefs.getLong(CHROME_ROOT_FOUND_COUNT, 0L)}")
            appendLine("Last Chrome event: ${prefs.text(LAST_EVENT)}")
            appendLine("Last Chrome event time: ${formatTime(prefs.getLong(LAST_EVENT_TIME, 0L))}")
            appendLine("Latest scan status: ${prefs.text(LAST_SCAN_STATUS, "No scan yet")}")
            appendLine()
            appendLine("LAST SUCCESSFUL CHROME ROOT")
            appendLine("Time: ${formatTime(prefs.getLong(LAST_ROOT_TIME, 0L))}")
            appendLine("Package: ${prefs.text(LAST_ROOT_PACKAGE)}")
            appendLine("Route: ${prefs.text(LAST_ROOT_ROUTE)}")
            appendLine()
            appendLine("LAST NODE CONTAINING SKIP TEXT")
            appendLine("Time: ${formatTime(prefs.getLong(LAST_CANDIDATE_TIME, 0L))}")
            appendLine("Route: ${prefs.text(LAST_CANDIDATE_ROUTE)}")
            appendLine(prefs.text(LAST_CANDIDATE, "No matching node captured"))
            appendLine()
            appendLine("LAST CLICK ATTEMPT")
            appendLine("Time: ${formatTime(prefs.getLong(LAST_CLICK_TIME, 0L))}")
            appendLine(prefs.text(LAST_CLICK_ATTEMPT, "No click attempted"))
            appendLine("Result: ${prefs.text(LAST_CLICK_RESULT, "-")}")
            appendLine()
            appendLine("LAST event.source SNAPSHOT")
            appendLine("Status: ${prefs.text(LAST_SOURCE_STATUS, "No source observed")}")
            appendLine("Time: ${formatTime(prefs.getLong(LAST_SOURCE_TIME, 0L))}")
            appendLine(prefs.text(LAST_SOURCE_SNAPSHOT, "No event.source snapshot captured"))
            appendLine()
            appendLine("LAST SUCCESSFUL CHROME TREE SNAPSHOT")
            append(prefs.text(LAST_TREE, "No Chrome root tree captured"))
        }
    }

    private fun SharedPreferences.text(key: String, fallback: String = "-"): String {
        return getString(key, fallback) ?: fallback
    }

    private fun formatTime(timestamp: Long): String {
        if (timestamp <= 0L) return "-"
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }
}

