package com.example.autoskip5

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val padding = (24 * resources.displayMetrics.density).toInt()
        val title = TextView(this).apply {
            text = "AutoSkip 1.0"
            textSize = 26f
        }
        statusView = TextView(this).apply {
            textSize = 16f
            setPadding(0, padding / 2, 0, padding)
        }
        val instructions = TextView(this).apply {
            text = "Enable AutoSkip in Accessibility Settings. It runs only when Chrome sends accessibility events; this screen does not need to remain open."
            textSize = 16f
            setPadding(0, 0, 0, padding)
        }
        val settingsButton = Button(this).apply {
            text = "Open Accessibility Settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, padding)
                addView(title)
                addView(statusView)
                addView(instructions)
                addView(settingsButton)
            },
        )
    }

    override fun onResume() {
        super.onResume()
        statusView.text = if (isServiceEnabled()) {
            "Status: enabled"
        } else {
            "Status: not enabled"
        }
    }

    private fun isServiceEnabled(): Boolean {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { service ->
                val info = service.resolveInfo.serviceInfo
                info.packageName == packageName &&
                    info.name == SkipAccessibilityService::class.java.name
            }
    }
}

