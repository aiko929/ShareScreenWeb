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

        findViewById<Switch>(R.id.bannerSwitch).apply {
            isChecked = AdPrefs.isBannerEnabled(this@SettingsActivity)
            setOnCheckedChangeListener { _, checked ->
                AdPrefs.setBannerEnabled(this@SettingsActivity, checked)
                renderPrivacyButton()
            }
        }
        findViewById<Switch>(R.id.interstitialSwitch).apply {
            isChecked = AdPrefs.isInterstitialEnabled(this@SettingsActivity)
            setOnCheckedChangeListener { _, checked ->
                AdPrefs.setInterstitialEnabled(this@SettingsActivity, checked)
                renderPrivacyButton()
            }
        }

        privacyButton = findViewById(R.id.privacyButton)
        privacyButton.setOnClickListener { ads.showPrivacyOptions(this) }
    }

    override fun onResume() {
        super.onResume()
        renderPrivacyButton()
    }

    private fun renderPrivacyButton() {
        val show = AdPrefs.anyEnabled(this) && ads.isPrivacyOptionsRequired(this)
        privacyButton.visibility = if (show) View.VISIBLE else View.GONE
    }
}
