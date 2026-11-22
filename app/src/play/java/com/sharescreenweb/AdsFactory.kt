package com.sharescreenweb

import android.app.Activity
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

/** Google Play build: AdMob banner + interstitial, behind the user's ads setting and GDPR consent. */
object AdsFactory {
    fun create(): AdController = AdMobController()
}

private object AdIds {
    // Google's official test ad units; always used in debug builds so we never click our own ads.
    private const val TEST_BANNER = "ca-app-pub-3940256099942544/9214589741"
    private const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"

    val banner: String get() = if (BuildConfig.DEBUG) TEST_BANNER else BuildConfig.ADMOB_BANNER_ID
    val interstitial: String get() = if (BuildConfig.DEBUG) TEST_INTERSTITIAL else BuildConfig.ADMOB_INTERSTITIAL_ID
}

private class AdMobController : AdController {
    override val available = true

    private var banner: AdView? = null
    private var bannerContainer: ViewGroup? = null
    private var interstitial: InterstitialAd? = null
    private var interstitialLoading = false

    override fun onResume(activity: Activity, bannerContainer: ViewGroup) {
        this.bannerContainer = bannerContainer
        // Apply settings changes made while the user was away (e.g. in SettingsActivity).
        if (!AdPrefs.isBannerEnabled(activity)) removeBanner()
        if (!AdPrefs.isInterstitialEnabled(activity)) interstitial = null
        if (!AdPrefs.anyEnabled(activity)) return
        banner?.resume()
        gatherConsent(activity) { startAds(activity) }
    }

    override fun onPause() {
        banner?.pause()
    }

    override fun destroy() {
        removeBanner()
        bannerContainer = null
        interstitial = null
    }

    override fun runAfterInterstitial(activity: Activity, action: () -> Unit) {
        val ad = interstitial
        if (ad == null || !AdPrefs.isInterstitialEnabled(activity) || !interstitialIntervalElapsed(activity)) {
            action()
            return
        }
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                markInterstitialShown(activity)
            }

            override fun onAdDismissedFullScreenContent() {
                action()
                loadInterstitial(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                action()
                loadInterstitial(activity)
            }
        }
        ad.show(activity)
    }

    override fun isPrivacyOptionsRequired(activity: Activity): Boolean =
        UserMessagingPlatform.getConsentInformation(activity).privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    override fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) Log.w(TAG, "Privacy options form: ${error.message}")
        }
    }

    /** Requests consent status once per app start and shows the consent form if needed (EU/UK). */
    private fun gatherConsent(activity: Activity, onCanRequestAds: () -> Unit) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        if (consentRequested.compareAndSet(false, true)) {
            info.requestConsentInfoUpdate(
                activity,
                ConsentRequestParameters.Builder().build(),
                {
                    if (activity.isDestroyed) return@requestConsentInfoUpdate
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                        if (error != null) Log.w(TAG, "Consent form: ${error.message}")
                        if (!activity.isDestroyed && info.canRequestAds()) onCanRequestAds()
                    }
                },
                { error ->
                    Log.w(TAG, "Consent info update failed: ${error.message}")
                    if (!activity.isDestroyed && info.canRequestAds()) onCanRequestAds()
                }
            )
        }
        // Consent from a previous session can be used right away.
        if (info.canRequestAds()) onCanRequestAds()
    }

    private fun startAds(activity: Activity) {
        if (!AdPrefs.anyEnabled(activity)) return
        if (sdkInitialized.compareAndSet(false, true)) {
            val appContext = activity.applicationContext
            Thread { MobileAds.initialize(appContext) {} }.start()
        }
        if (AdPrefs.isBannerEnabled(activity)) showBanner(activity)
        loadInterstitial(activity)
    }

    private fun showBanner(activity: Activity) {
        val container = bannerContainer ?: return
        if (banner != null) {
            container.visibility = View.VISIBLE
            return
        }
        val density = activity.resources.displayMetrics.density
        val widthPx = if (container.width > 0) container.width else activity.resources.displayMetrics.widthPixels
        val adView = AdView(activity).apply {
            adUnitId = AdIds.banner
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, (widthPx / density).toInt()))
        }
        container.removeAllViews()
        container.addView(adView)
        container.visibility = View.VISIBLE
        adView.loadAd(AdRequest.Builder().build())
        banner = adView
    }

    private fun removeBanner() {
        banner?.destroy()
        banner = null
        bannerContainer?.apply {
            removeAllViews()
            visibility = View.GONE
        }
    }

    private fun loadInterstitial(activity: Activity) {
        if (interstitial != null || interstitialLoading || !AdPrefs.isInterstitialEnabled(activity)) return
        interstitialLoading = true
        InterstitialAd.load(
            activity.applicationContext, AdIds.interstitial, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialLoading = false
                    interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialLoading = false
                    interstitial = null
                    Log.i(TAG, "Interstitial not loaded: ${error.message}")
                }
            }
        )
    }

    /** At most one full-screen ad per [INTERSTITIAL_MIN_INTERVAL_MS], also across app restarts. */
    private fun interstitialIntervalElapsed(activity: Activity): Boolean {
        val last = activity.getSharedPreferences(STATE_FILE, Activity.MODE_PRIVATE).getLong(KEY_LAST_INTERSTITIAL, 0L)
        val elapsed = System.currentTimeMillis() - last
        // A negative value means the clock was changed; don't block ads forever because of it.
        return elapsed !in 0 until INTERSTITIAL_MIN_INTERVAL_MS
    }

    private fun markInterstitialShown(activity: Activity) {
        activity.getSharedPreferences(STATE_FILE, Activity.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_INTERSTITIAL, System.currentTimeMillis()).apply()
    }

    companion object {
        private const val TAG = "Ads"
        private const val STATE_FILE = "ad_state"
        private const val KEY_LAST_INTERSTITIAL = "last_interstitial_at"
        private const val INTERSTITIAL_MIN_INTERVAL_MS = 5 * 60 * 1000L
        private val consentRequested = AtomicBoolean(false)
        private val sdkInitialized = AtomicBoolean(false)
    }
}
