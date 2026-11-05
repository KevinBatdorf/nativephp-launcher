package com.kevinbatdorf.plugins.launcher

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.nativephp.mobile.ui.MainActivity
import java.util.concurrent.atomic.AtomicBoolean

/** Upstream nulls its own holder when a replaced instance dies, after its successor started. */
internal object LauncherApp {

    @Volatile
    private var current: Activity? = null

    val homes: MutableSet<Int> = java.util.Collections.synchronizedSet(mutableSetOf<Int>())

    private val watching = AtomicBoolean(false)

    @Volatile
    private var wasDefault = false

    fun track() {
        MainActivity.instance?.let { current = it }
    }

    fun live(): Activity? =
        current?.takeUnless { it.isDestroyed } ?: MainActivity.instance?.takeUnless { it.isDestroyed }

    /** A HOME press pauses the app first and stops it only after the home shows. */
    fun inFrontOn(displayId: Int): Boolean {
        val app = live() ?: return false
        val started = (app as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) ?: false

        return started && app.display?.displayId == displayId
    }

    /** Any change of default, by dialog, settings or elsewhere, ends with the app resuming. */
    fun watch(application: Application) {
        if (!watching.compareAndSet(false, true)) return

        wasDefault = application.isDefaultHome()
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity !is MainActivity) return

                val isDefault = activity.isDefaultHome()
                if (isDefault == wasDefault) return

                wasDefault = isDefault
                LauncherEvents.defaultHomeChanged(activity as FragmentActivity, isDefault)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}

/** Names Android's own resolver, not null, while no default home is set. */
internal fun Context.defaultHomePackage(): String? {
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    @Suppress("DEPRECATION")
    return packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
}

internal fun Context.isDefaultHome(): Boolean = defaultHomePackage() == packageName

/** Plugin init: the generated registration calls this on every app Activity creation. */
fun registerLauncher(context: Context) {
    LauncherApp.track()
    (context.applicationContext as? Application)?.let { LauncherApp.watch(it) }
}
