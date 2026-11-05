package com.kevinbatdorf.plugins.launcher

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Display
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

    /** An app in a removed task reports isFinishing only once its destroy arrives. */
    val dying: MutableSet<Activity> =
        java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(java.util.WeakHashMap<Activity, Boolean>()))

    @Volatile
    var mainHomeTask = -1

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

    fun watch(application: Application) {
        if (!watching.compareAndSet(false, true)) return

        watchDefaultHome(application)
        watchHomeKey(application)
    }

    /** Any change of default, by dialog, settings or elsewhere, ends with the app resuming. */
    private fun watchDefaultHome(application: Application) {
        wasDefault = application.isDefaultHome()
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity !is MainActivity) return

                val isDefault = activity.isDefaultHome()
                if (isDefault == wasDefault) return

                // A failed cast must not flip wasDefault, or the change is never reported.
                val host = activity as? FragmentActivity ?: return

                wasDefault = isDefault
                LauncherEvents.defaultHomeChanged(host, isDefault)
            }

            /** A destroyed instance would hold its whole view tree until the next boot replaced it. */
            override fun onActivityDestroyed(activity: Activity) {
                if (activity === current) current = null
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })
    }

    /** HOME on a home task already in front with the app on top reaches no activity, only this broadcast. */
    @Suppress("DEPRECATION")
    private fun watchHomeKey(application: Application) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getStringExtra("reason") == "homekey") homeKeyPressed(context)
            }
        }
        val filter = IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            application.registerReceiver(receiver, filter)
        }
    }

    private fun homeKeyPressed(context: Context) {
        val app = live() ?: return

        // Outside the home task, or while another app is the default, HOME starts a home activity itself.
        if (app.isFinishing || app in dying || app.taskId != mainHomeTask || !context.isDefaultHome()) return
        // Without focus, HOME was pressed on another display.
        if (!app.hasWindowFocus()) return

        LauncherEvents.homeRequested(app.display?.displayId ?: Display.DEFAULT_DISPLAY, wasInFront = true)
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
