package com.xforyoudisable

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var diagnostics: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val padding = (24 * resources.displayMetrics.density).toInt()
        status = TextView(this).apply { textSize = 18f }
        val openSettings = Button(this).apply {
            text = "Open accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        val hint = TextView(this).apply {
            text = "Turn on \"X For You Blocker\" under Installed / Downloaded apps.\n\n" +
                "If the switch is greyed out (Android 13+): App info → ⋮ → Allow restricted settings, then try again."
        }

        diagnostics = TextView(this).apply {
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(0, padding, 0, 0)
        }
        val copy = Button(this).apply {
            text = "Copy diagnostics"
            setOnClickListener {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("X For You Blocker diagnostics", diagnostics.text))
                Toast.makeText(this@MainActivity, "Copied", Toast.LENGTH_SHORT).show()
            }
        }

        setContentView(ScrollView(this).apply {
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, padding)
                addView(status)
                addView(openSettings)
                addView(hint)
                addView(copy)
                addView(diagnostics)
            })
        })
    }

    override fun onResume() {
        super.onResume()
        status.text = if (isServiceEnabled()) "Blocker is ON" else "Blocker is OFF"
        diagnostics.text = buildString {
            append("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("X app: ${xVersion()}\n")
            append("Service last connected: ${Diagnostics.read(this@MainActivity, Diagnostics.KEY_CONNECTED) ?: "never"}\n")
            append("Last X scan: ")
            append(Diagnostics.read(this@MainActivity, Diagnostics.KEY_REPORT) ?: "never (open X's Home tab, then come back)")
        }
    }

    private fun xVersion(): String = try {
        packageManager.getPackageInfo("com.twitter.android", 0).versionName ?: "unknown version"
    } catch (e: PackageManager.NameNotFoundException) {
        "not installed (package com.twitter.android not found)"
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val self = ComponentName(this, ForYouBlockerService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == self }
    }
}
