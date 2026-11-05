package com.kevinbatdorf.plugins.launcher

import android.os.SystemClock
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.nativephp.mobile.utils.NativeActionCoordinator
import org.json.JSONObject

private const val TAG = "LauncherEvents"

internal object LauncherEvents {

    const val HOME_REQUESTED = "KevinBatdorf\\Launcher\\Events\\HomeRequested"

    const val DEFAULT_HOME_CHANGED = "KevinBatdorf\\Launcher\\Events\\DefaultHomeChanged"

    private const val SAME_PRESS_MS = 500L

    private var lastInFront = -1 to 0L

    /** False when no app instance is alive to hear it; the caller then boots one. */
    fun homeRequested(displayId: Int, wasInFront: Boolean): Boolean {
        val activity = LauncherApp.live() as? FragmentActivity ?: return false

        // Where Android also stacks a home instance, it and the homekey broadcast report the same press.
        if (wasInFront) {
            val now = SystemClock.uptimeMillis()
            if (lastInFront.first == displayId && now - lastInFront.second < SAME_PRESS_MS) return true
            lastInFront = displayId to now
        }

        val payload = JSONObject().put("displayId", displayId).put("wasInFront", wasInFront).toString()

        return dispatch(activity, HOME_REQUESTED, payload)
    }

    fun defaultHomeChanged(activity: FragmentActivity, isDefault: Boolean): Boolean {
        val payload = JSONObject().put("isDefault", isDefault).toString()

        return dispatch(activity, DEFAULT_HOME_CHANGED, payload)
    }

    private fun dispatch(activity: FragmentActivity, event: String, payload: String): Boolean =
        runCatching { NativeActionCoordinator.dispatchEvent(activity, event, payload) }
            .onFailure { Log.w(TAG, "$event not delivered: ${it.message}") }
            .isSuccess
}
