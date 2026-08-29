package com.example.autoskip5

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    companion object {
        private const val SETUP_PREFERENCES = "setup_wizard"
        private const val RESTRICTED_CONFIRMED = "restricted_confirmed"
        private const val BATTERY_CONFIRMED = "battery_confirmed"
        private const val AUTOSTART_CONFIRMED = "autostart_confirmed"

        private val READY_COLOR = Color.rgb(24, 130, 66)
        private val ACTION_COLOR = Color.rgb(190, 45, 45)
    }

    private lateinit var readyView: TextView
    private lateinit var accessibilityStatus: TextView
    private lateinit var restrictedStatus: TextView
    private lateinit var batteryStatus: TextView
    private lateinit var autostartStatus: TextView
    private lateinit var restrictedConfirmButton: Button
    private lateinit var batteryConfirmButton: Button
    private lateinit var autostartSettingsButton: Button
    private lateinit var autostartConfirmButton: Button

    private val setupPreferences by lazy {
        getSharedPreferences(SETUP_PREFERENCES, Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val padding = dp(24)
        val halfPadding = dp(12)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        container.addView(TextView(this).apply {
            text = "AutoSkip 1.1"
            textSize = 28f
        })
        container.addView(TextView(this).apply {
            text = "Complete the checklist once. Android requires you to approve each system setting yourself."
            textSize = 16f
            setPadding(0, halfPadding, 0, halfPadding)
        })

        readyView = TextView(this).apply {
            textSize = 21f
            setPadding(0, halfPadding, 0, halfPadding)
        }
        container.addView(readyView)

        container.addView(sectionTitle("Setup checklist"))

        accessibilityStatus = checklistRow()
        container.addView(accessibilityStatus)
        container.addView(actionButton("Open Accessibility Settings") {
            openAccessibilitySettings()
        })

        restrictedStatus = checklistRow()
        container.addView(restrictedStatus)
        container.addView(actionButton("Open AutoSkip App Info") {
            openAppInfo()
        })
        restrictedConfirmButton = actionButton("I allowed Restricted settings") {
            setupPreferences.edit().putBoolean(RESTRICTED_CONFIRMED, true).apply()
            refreshSetupStatus()
        }
        container.addView(restrictedConfirmButton)

        batteryStatus = checklistRow()
        container.addView(batteryStatus)
        container.addView(actionButton("Open Battery Settings") {
            openBatterySettings()
        })
        batteryConfirmButton = actionButton("I selected No restrictions") {
            setupPreferences.edit().putBoolean(BATTERY_CONFIRMED, true).apply()
            refreshSetupStatus()
        }
        container.addView(batteryConfirmButton)

        autostartStatus = checklistRow()
        container.addView(autostartStatus)
        autostartSettingsButton = actionButton("Open Background Autostart") {
            openXiaomiAutostartSettings()
        }
        container.addView(autostartSettingsButton)
        autostartConfirmButton = actionButton("I enabled Background autostart") {
            setupPreferences.edit().putBoolean(AUTOSTART_CONFIRMED, true).apply()
            refreshSetupStatus()
        }
        container.addView(autostartConfirmButton)

        container.addView(actionButton("Check setup") {
            refreshSetupStatus()
        }.apply {
            setPadding(0, halfPadding, 0, 0)
        })

        setContentView(ScrollView(this).apply { addView(container) })
    }

    override fun onResume() {
        super.onResume()
        refreshSetupStatus()
    }

    private fun refreshSetupStatus() {
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        if (accessibilityEnabled) {
            setupPreferences.edit().putBoolean(RESTRICTED_CONFIRMED, true).apply()
        }

        val restrictedRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val restrictedReady = !restrictedRequired ||
            accessibilityEnabled ||
            setupPreferences.getBoolean(RESTRICTED_CONFIRMED, false)

        val systemBatteryExempt = isIgnoringBatteryOptimizations()
        val batteryReady = systemBatteryExempt ||
            setupPreferences.getBoolean(BATTERY_CONFIRMED, false)

        val xiaomiDevice = isXiaomiDevice()
        val autostartReady = !xiaomiDevice ||
            setupPreferences.getBoolean(AUTOSTART_CONFIRMED, false)

        setChecklistStatus(
            accessibilityStatus,
            "Accessibility",
            accessibilityEnabled,
            if (accessibilityEnabled) "Enabled" else "Disabled",
        )
        setChecklistStatus(
            restrictedStatus,
            "Restricted settings",
            restrictedReady,
            when {
                !restrictedRequired -> "Not required on this Android version"
                accessibilityEnabled -> "Verified by enabled Accessibility service"
                restrictedReady -> "Confirmed by you"
                else -> "Allow from App Info, then confirm"
            },
        )
        setChecklistStatus(
            batteryStatus,
            "Battery unrestricted",
            batteryReady,
            when {
                systemBatteryExempt -> "Detected as unrestricted by Android"
                batteryReady -> "No restrictions confirmed by you"
                else -> "Select No restrictions, then confirm"
            },
        )
        setChecklistStatus(
            autostartStatus,
            "Background autostart",
            autostartReady,
            when {
                !xiaomiDevice -> "Not required on this device"
                autostartReady -> "Enabled status confirmed by you"
                else -> "Enable in HyperOS, then confirm"
            },
        )

        restrictedConfirmButton.visibility =
            if (restrictedRequired && !restrictedReady) View.VISIBLE else View.GONE
        batteryConfirmButton.visibility = if (batteryReady) View.GONE else View.VISIBLE
        autostartSettingsButton.visibility = if (xiaomiDevice) View.VISIBLE else View.GONE
        autostartConfirmButton.visibility =
            if (xiaomiDevice && !autostartReady) View.VISIBLE else View.GONE

        val ready = accessibilityEnabled && restrictedReady && batteryReady && autostartReady
        readyView.text = if (ready) {
            "✓ AutoSkip is ready"
        } else {
            "Setup needs attention"
        }
        readyView.setTextColor(if (ready) READY_COLOR else ACTION_COLOR)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { service ->
                val info = service.resolveInfo.serviceInfo
                info.packageName == packageName &&
                    info.name == SkipAccessibilityService::class.java.name
            }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun isXiaomiDevice(): Boolean {
        val identity = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
        return identity.contains("xiaomi") ||
            identity.contains("redmi") ||
            identity.contains("poco")
    }

    private fun openAccessibilitySettings() {
        openFirstAvailable(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
    }

    private fun openBatterySettings() {
        val xiaomiAppBattery = Intent().apply {
            component = ComponentName(
                "com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
            )
            putExtra("package_name", packageName)
            putExtra("package_label", applicationInfo.loadLabel(packageManager).toString())
        }
        openFirstAvailable(
            xiaomiAppBattery,
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            appInfoIntent(),
        )
    }

    private fun openXiaomiAutostartSettings() {
        val xiaomiAutostart = Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            )
        }
        val xiaomiAutostartAction = Intent("miui.intent.action.OP_AUTO_START").apply {
            setPackage("com.miui.securitycenter")
        }
        openFirstAvailable(xiaomiAutostart, xiaomiAutostartAction, appInfoIntent())
    }

    private fun openAppInfo() {
        openFirstAvailable(appInfoIntent(), Intent(Settings.ACTION_APPLICATION_SETTINGS))
    }

    private fun appInfoIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
    }

    private fun openFirstAvailable(vararg intents: Intent) {
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // Try the next safe system-settings fallback.
            } catch (_: SecurityException) {
                // Some HyperOS builds block internal settings components.
            }
        }
        Toast.makeText(this, "This settings page is unavailable on this device", Toast.LENGTH_LONG)
            .show()
    }

    private fun setChecklistStatus(
        view: TextView,
        label: String,
        ready: Boolean,
        detail: String,
    ) {
        view.text = "${if (ready) "✓" else "✕"} $label\n$detail"
        view.setTextColor(if (ready) READY_COLOR else ACTION_COLOR)
    }

    private fun checklistRow(): TextView {
        return TextView(this).apply {
            textSize = 17f
            setPadding(0, dp(12), 0, dp(6))
        }
    }

    private fun sectionTitle(textValue: String): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = 20f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(12), 0, dp(4))
        }
    }

    private fun actionButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
