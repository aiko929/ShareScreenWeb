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

/** User preferences for the two ad types; each can be turned off on its own. */
object AdPrefs {
    private const val FILE = "settings"
    private const val KEY_BANNER = "banner_ads_enabled"
    private const val KEY_INTERSTITIAL = "interstitial_ads_enabled"

    fun isBannerEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_BANNER, true)

    fun setBannerEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_BANNER, enabled).apply()

    fun isInterstitialEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_INTERSTITIAL, true)

    fun setInterstitialEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_INTERSTITIAL, enabled).apply()

    /** True if at least one ad type is on; only then is the ad SDK started. */
    fun anyEnabled(context: Context): Boolean = isBannerEnabled(context) || isInterstitialEnabled(context)

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
