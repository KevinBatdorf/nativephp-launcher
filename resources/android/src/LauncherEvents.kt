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

    private var lastPress = -1 to 0L

    /** False when no app instance is alive to hear it; the caller then boots one. */
    fun homeRequested(displayId: Int, wasInFront: Boolean): Boolean {
        val activity = LauncherApp.live() as? FragmentActivity
        if (activity == null) {
            Log.d(TAG, "home d$displayId: live() is ${LauncherApp.live()?.javaClass?.simpleName}, not a FragmentActivity")

            return false
        }

        // One press reaches both a home instance Android stacks and the homekey broadcast.
        val now = SystemClock.uptimeMillis()
        if (lastPress.first == displayId && now - lastPress.second < SAME_PRESS_MS) {
            Log.d(TAG, "home d$displayId dropped: same press as ${now - lastPress.second} ms ago")

            return true
        }
        lastPress = displayId to now

        val payload = JSONObject().put("displayId", displayId).put("wasInFront", wasInFront).toString()

        return dispatch(activity, HOME_REQUESTED, payload)
    }

    fun defaultHomeChanged(activity: FragmentActivity, isDefault: Boolean): Boolean {
        val payload = JSONObject().put("isDefault", isDefault).toString()

        return dispatch(activity, DEFAULT_HOME_CHANGED, payload)
    }

    private fun dispatch(activity: FragmentActivity, event: String, payload: String): Boolean =
        runCatching { NativeActionCoordinator.dispatchEvent(activity, event, payload) }
            .onSuccess { Log.d(TAG, "$event handed to PHP: $payload") }
            .onFailure { Log.w(TAG, "$event not delivered: ${it.message}") }
            .isSuccess
}
