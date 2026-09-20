package com.kevinbatdorf.plugins.launcher

import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.nativephp.mobile.utils.NativeActionCoordinator
import org.json.JSONObject

private const val TAG = "LauncherEvents"

internal object LauncherEvents {

    const val HOME_REQUESTED = "KevinBatdorf\\Launcher\\Events\\HomeRequested"

    /** False when no app instance is alive to hear it; the caller then boots one. */
    fun homeRequested(displayId: Int): Boolean {
        val activity = LauncherApp.live() as? FragmentActivity ?: return false

        val payload = JSONObject().put("displayId", displayId).toString()

        return runCatching { NativeActionCoordinator.dispatchEvent(activity, HOME_REQUESTED, payload) }
            .onFailure { Log.w(TAG, "home d$displayId not delivered: ${it.message}") }
            .isSuccess
    }
}
