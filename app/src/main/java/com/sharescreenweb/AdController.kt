package com.sharescreenweb

import android.app.Activity
import android.content.Context
import android.view.ViewGroup

/**
 * Ads abstraction. The "foss" flavor provides a no-op implementation, the "play" flavor
 * an AdMob one (see AdsFactory in the flavor source sets).
 */
interface AdController {
    /** False in builds without ads; hides the ads setting entirely. */
    val available: Boolean

    /** Call from Activity.onResume. Shows/loads the bottom banner, or removes it when ads are off. */
    fun onResume(activity: Activity, bannerContainer: ViewGroup)

    fun onPause()

    fun destroy()

    /** Shows the full-screen ad if one is ready and ads are on, then runs [action]. */
    fun runAfterInterstitial(activity: Activity, action: () -> Unit)

    /** Whether the user must be offered a way to change their ad consent (e.g. GDPR). */
    fun isPrivacyOptionsRequired(activity: Activity): Boolean

    fun showPrivacyOptions(activity: Activity)
}

/** User preference for showing ads. */
object AdPrefs {
    private const val FILE = "settings"
    private const val KEY_ADS = "ads_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_ADS, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_ADS, enabled).apply()
    }
}
