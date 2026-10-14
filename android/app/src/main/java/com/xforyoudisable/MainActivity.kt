package com.xforyoudisable

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView

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

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(status)
            addView(openSettings)
            addView(hint)
        })
    }

    override fun onResume() {
        super.onResume()
        status.text = if (isServiceEnabled()) "Blocker is ON" else "Blocker is OFF"
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val self = ComponentName(this, ForYouBlockerService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == self }
    }
}
