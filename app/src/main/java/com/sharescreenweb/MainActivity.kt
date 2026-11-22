package com.sharescreenweb

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var toggleButton: Button
    private lateinit var shareButton: Button
    private lateinit var hintText: TextView
    private lateinit var addressText: TextView
    private lateinit var adContainer: ViewGroup

    private val ads: AdController = AdsFactory.create()

    private val stateListener: () -> Unit = { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        applySystemBarPadding(findViewById(R.id.root))

        toggleButton = findViewById(R.id.toggleButton)
        shareButton = findViewById(R.id.shareButton)
        hintText = findViewById(R.id.hintText)
        addressText = findViewById(R.id.addressText)
        adContainer = findViewById(R.id.adContainer)

        toggleButton.setOnClickListener {
            if (ShareState.isSharing) stopSharing()
            else ads.runAfterInterstitial(this) { requestCapture() }
        }
        shareButton.setOnClickListener { shareAddress() }

        findViewById<View>(R.id.settingsButton).apply {
            visibility = if (ads.available) View.VISIBLE else View.GONE
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        ShareState.addListener(stateListener)
        render()
        ads.onResume(this, adContainer)
    }

    override fun onPause() {
        ads.onPause()
        ShareState.removeListener(stateListener)
        super.onPause()
    }

    override fun onDestroy() {
        ads.destroy()
        super.onDestroy()
    }

    private fun currentUrl(): String? {
        val port = ShareState.port ?: return null
        val ip = NetworkUtils.localIpv4Address() ?: return null
        return "http://$ip:$port"
    }

    private fun render() {
        val sharing = ShareState.isSharing
        toggleButton.setText(if (sharing) R.string.stop_sharing else R.string.start_sharing)
        toggleButton.setBackgroundResource(if (sharing) R.drawable.btn_stop else R.drawable.btn_start)

        if (!sharing) {
            hintText.setText(R.string.idle_hint)
            addressText.visibility = View.GONE
            shareButton.visibility = View.GONE
            return
        }
        val url = currentUrl()
        if (url == null) {
            hintText.setText(R.string.no_network)
            addressText.visibility = View.GONE
            shareButton.visibility = View.GONE
        } else {
            hintText.setText(R.string.open_in_browser)
            addressText.text = url
            addressText.visibility = View.VISIBLE
            shareButton.visibility = View.VISIBLE
        }
    }

    private fun requestCapture() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_CAPTURE) return
        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, ScreenShareService::class.java)
            .setAction(ScreenShareService.ACTION_START)
            .putExtra(ScreenShareService.EXTRA_RESULT_CODE, resultCode)
            .putExtra(ScreenShareService.EXTRA_RESULT_DATA, data)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
    }

    private fun stopSharing() {
        startService(Intent(this, ScreenShareService::class.java).setAction(ScreenShareService.ACTION_STOP))
    }

    private fun shareAddress() {
        val url = currentUrl() ?: return
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, url)
        startActivity(Intent.createChooser(send, getString(R.string.share_chooser)))
    }

    companion object {
        private const val REQ_CAPTURE = 1001
        private const val REQ_NOTIFICATIONS = 1002
    }
}
