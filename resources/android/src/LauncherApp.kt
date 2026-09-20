package com.kevinbatdorf.plugins.launcher

import android.app.Activity
import android.content.Context
import com.nativephp.mobile.ui.MainActivity

/** Upstream nulls its own holder when a replaced instance dies, after its successor started. */
internal object LauncherApp {

    @Volatile
    private var current: Activity? = null

    val homes: MutableSet<Int> = java.util.Collections.synchronizedSet(mutableSetOf<Int>())

    fun track() {
        MainActivity.instance?.let { current = it }
    }

    fun live(): Activity? =
        current?.takeUnless { it.isDestroyed } ?: MainActivity.instance?.takeUnless { it.isDestroyed }
}

/** Plugin init: the generated registration calls this on every app Activity creation. */
@Suppress("UNUSED_PARAMETER")
fun registerLauncher(context: Context) {
    LauncherApp.track()
}
