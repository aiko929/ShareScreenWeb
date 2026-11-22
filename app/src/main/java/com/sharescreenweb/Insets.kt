package com.sharescreenweb

import android.os.Build
import android.view.View
import android.view.WindowInsets

/** targetSdk 35+ is edge-to-edge: pad [view] so its content stays clear of the system bars. */
fun applySystemBarPadding(view: View) {
    val left = view.paddingLeft
    val top = view.paddingTop
    val right = view.paddingRight
    val bottom = view.paddingBottom
    view.setOnApplyWindowInsetsListener { v, insets ->
        if (Build.VERSION.SDK_INT >= 30) {
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            v.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom)
        }
        insets
    }
}
