package com.kevinbatdorf.plugins.launcher

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display

private const val TAG = "LauncherHome"

/**
 * Resident and never finished: a finished home hands the display to whatever home is
 * still resident, the stock launcher. The app runs inside this task on the main display;
 * recents never lists a home task, so neither can be swiped away.
 */
open class LauncherHomeActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    private var resumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LauncherApp.homes.add(displayId)
        Log.d(TAG, "resident on d$displayId")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing || !isTaskRoot) return
        LauncherApp.homes.remove(displayId)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        Log.d(TAG, "home requested on d$displayId")
        requestHome()
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun requestHome() {
        val app = LauncherApp.live()

        // Recents never lists home tasks: anything listed here is a swipeable card started from outside.
        val strays = appTasks().filter { it.taskInfo.baseIntent.component?.className == MAIN_ACTIVITY }
        strays.forEach { runCatching { it.finishAndRemoveTask() } }

        when {
            app == null -> boot()
            // Let a dying instance finish first: two app instances at once break the runtime.
            app.isFinishing || strays.any { it.taskInfo.taskId == app.taskId } ->
                handler.postDelayed({ if (resumed) requestHome() }, RETRY_MS)
            else -> {
                if (app.display?.displayId == displayId) bringForward()
                LauncherEvents.homeRequested(displayId)
            }
        }

        if (displayId == Display.DEFAULT_DISPLAY) ensureSiblingHomes()

        // A HOME press while the app is on top stacks a second instance; only the root stays.
        if (!isTaskRoot) finish()
    }

    /** Whatever closes on another display then lands on our home, not on a lingering stock one. */
    private fun ensureSiblingHomes() {
        val displays = (getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager).displays

        for (display in displays) {
            if (display.displayId == Display.DEFAULT_DISPLAY || display.displayId in LauncherApp.homes) continue

            val options = ActivityOptions.makeBasic().apply { launchDisplayId = display.displayId }
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_SECONDARY_HOME)
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            runCatching { startActivity(intent, options.toBundle()) }
                .onFailure { Log.w(TAG, "no home for d${display.displayId}: ${it.message}") }
        }
    }

    private val displayId: Int
        get() = display?.displayId ?: Display.DEFAULT_DISPLAY

    private fun appTasks(): List<ActivityManager.AppTask> =
        (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).appTasks

    private fun bringForward() {
        startActivity(mainIntent().addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }

    /** Booted on another display, a non-resizeable app is letterboxed to the main display's shape. */
    private fun boot() {
        val options = ActivityOptions.makeBasic().apply { launchDisplayId = Display.DEFAULT_DISPLAY }
        val intent = if (displayId == Display.DEFAULT_DISPLAY) mainIntent() else homeIntent()

        startActivity(intent, options.toBundle())
    }

    private fun mainIntent(): Intent = Intent().apply {
        setClassName(packageName, MAIN_ACTIVITY)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Only a home activity may start another as home; that is what makes the new one resident. */
    private fun homeIntent(): Intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
        setPackage(packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    companion object {
        const val MAIN_ACTIVITY = "com.nativephp.mobile.ui.MainActivity"

        private const val RETRY_MS = 150L
    }
}
