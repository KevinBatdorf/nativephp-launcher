package com.kevinbatdorf.plugins.launcher

import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.nativephp.mobile.bridge.BridgeFunction
import com.nativephp.mobile.bridge.BridgeResponse
import java.io.File

object LauncherFunctions {

    private const val TAG = "LauncherFunctions"

    private const val ICON_PX = 192

    class Apps(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val pm = activity.packageManager
            val launchable = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

            @Suppress("DEPRECATION")
            val resolved: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(launchable, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                pm.queryIntentActivities(launchable, 0)
            }

            val iconDir = File(activity.filesDir, "launcher-icons").also { it.mkdirs() }

            val apps = resolved
                .filter { it.activityInfo.packageName != activity.packageName }
                .map { info ->
                    val pkg = info.activityInfo.packageName
                    mapOf(
                        "label" to info.loadLabel(pm).toString(),
                        "package" to pkg,
                        "activity" to info.activityInfo.name,
                        "icon" to iconPath(iconDir, pkg, info, pm),
                    )
                }
                .sortedBy { (it["label"] as String).lowercase() }

            return BridgeResponse.success(mapOf("apps" to apps))
        }

        private fun iconPath(dir: File, pkg: String, info: ResolveInfo, pm: PackageManager): String {
            val file = File(dir, "$pkg.png")
            if (file.exists()) return file.absolutePath

            return runCatching {
                file.outputStream().use { toBitmap(info.loadIcon(pm)).compress(Bitmap.CompressFormat.PNG, 100, it) }
                file.absolutePath
            }.getOrElse {
                Log.w(TAG, "no icon for $pkg: ${it.message}")
                ""
            }
        }

        private fun toBitmap(drawable: Drawable): Bitmap {
            if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap

            val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, ICON_PX, ICON_PX)
            drawable.draw(canvas)

            return bitmap
        }
    }

    /** A package can list several entry points; without [activity] the launch intent picks one. */
    class Open(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val pkg = parameters["package"]?.toString().orEmpty()
            if (pkg.isEmpty()) return BridgeResponse.error("INVALID_PARAMETERS", "package is required")

            val entry = parameters["activity"]?.toString().orEmpty()
            val intent = if (entry.isNotEmpty()) {
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(pkg, entry)
            } else {
                activity.packageManager.getLaunchIntentForPackage(pkg)
                    ?: return BridgeResponse.error("NOT_LAUNCHABLE", "No launch intent for $pkg")
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

            val display = (parameters["display"] as? Number)?.toInt()
            val options = ActivityOptions.makeBasic().apply {
                if (display != null) launchDisplayId = display
            }

            return runCatching { activity.startActivity(intent, options.toBundle()) }
                .fold(
                    { BridgeResponse.success(mapOf("ok" to true)) },
                    { BridgeResponse.error("OPEN_FAILED", it.message ?: "Could not open $pkg") },
                )
        }
    }

    class IsDefaultHome(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

            @Suppress("DEPRECATION")
            val current = activity.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)

            return BridgeResponse.success(mapOf(
                "default" to (current?.activityInfo?.packageName == activity.packageName),
                "current" to (current?.activityInfo?.packageName ?: ""),
            ))
        }
    }

    /** Android has no API to make an app the home screen; only the user can, on this settings screen. */
    class OpenHomeSettings(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val intents = listOf(
                Intent(Settings.ACTION_HOME_SETTINGS),
                Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            )

            for (intent in intents) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (runCatching { activity.startActivity(intent) }.isSuccess) {
                    return BridgeResponse.success(mapOf("ok" to true))
                }
            }

            return BridgeResponse.error("NO_SETTINGS", "This device has no default-home settings screen")
        }
    }
}
