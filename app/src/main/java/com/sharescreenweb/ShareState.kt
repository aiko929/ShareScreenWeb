package com.sharescreenweb

import android.os.Handler
import android.os.Looper

/** Tiny process-wide state holder so the activity can observe the service. */
object ShareState {
    /** Port the HTTP server listens on, or null when not sharing. */
    @Volatile
    var port: Int? = null
        private set

    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<() -> Unit>()

    val isSharing: Boolean get() = port != null

    fun update(newPort: Int?) {
        port = newPort
        main.post { listeners.toList().forEach { it() } }
    }

    fun addListener(l: () -> Unit) = listeners.add(l)
    fun removeListener(l: () -> Unit) = listeners.remove(l)
}
