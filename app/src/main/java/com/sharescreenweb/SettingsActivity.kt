package com.sharescreenweb

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Switch

class SettingsActivity : Activity() {

    private val ads: AdController = AdsFactory.create()
    private lateinit var privacyButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        applySystemBarPadding(findViewById(R.id.root))

        val adsSwitch = findViewById<Switch>(R.id.adsSwitch)
        adsSwitch.isChecked = AdPrefs.isEnabled(this)
        adsSwitch.setOnCheckedChangeListener { _, checked ->
            AdPrefs.setEnabled(this, checked)
            renderPrivacyButton()
        }

        privacyButton = findViewById(R.id.privacyButton)
        privacyButton.setOnClickListener { ads.showPrivacyOptions(this) }
    }

    override fun onResume() {
        super.onResume()
        renderPrivacyButton()
    }

    private fun renderPrivacyButton() {
        val show = AdPrefs.isEnabled(this) && ads.isPrivacyOptionsRequired(this)
        privacyButton.visibility = if (show) View.VISIBLE else View.GONE
    }
}
