package com.sharescreenweb

import android.app.Activity
import android.view.View
import android.view.ViewGroup

/** FOSS build: no ads, no ad SDK. */
object AdsFactory {
    fun create(): AdController = NoAds
}

private object NoAds : AdController {
    override val available = false

    override fun onResume(activity: Activity, bannerContainer: ViewGroup) {
        bannerContainer.visibility = View.GONE
    }

    override fun onPause() {}

    override fun destroy() {}

    override fun runAfterInterstitial(activity: Activity, action: () -> Unit) = action()

    override fun isPrivacyOptionsRequired(activity: Activity) = false

    override fun showPrivacyOptions(activity: Activity) {}
}
