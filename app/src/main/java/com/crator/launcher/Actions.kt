package com.crator.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast

object Actions {

    fun launch(activity: Activity, app: AppInfo, bounds: Rect?, anim: OpenAnim) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        intent.sourceBounds = bounds
        val v = activity.window.decorView
        val opts: ActivityOptions? = when (anim) {
            OpenAnim.ZoomIcon -> bounds?.let { ActivityOptions.makeScaleUpAnimation(v, it.left, it.top, it.width(), it.height()) }
            OpenAnim.Reveal -> bounds?.let { ActivityOptions.makeClipRevealAnimation(v, it.left, it.top, it.width(), it.height()) }
            OpenAnim.Fade -> ActivityOptions.makeCustomAnimation(activity, R.anim.crator_fade_in, R.anim.crator_hold)
            OpenAnim.SlideUp -> ActivityOptions.makeCustomAnimation(activity, R.anim.crator_slide_up, R.anim.crator_hold)
            OpenAnim.SlideSide -> ActivityOptions.makeCustomAnimation(activity, R.anim.crator_slide_side, R.anim.crator_slide_side_out)
            OpenAnim.Bounce -> ActivityOptions.makeCustomAnimation(activity, R.anim.crator_bounce_in, R.anim.crator_hold)
            OpenAnim.ZoomCenter -> ActivityOptions.makeCustomAnimation(activity, R.anim.crator_zoom_in, R.anim.crator_zoom_out)
            OpenAnim.None -> ActivityOptions.makeCustomAnimation(activity, 0, 0)
            OpenAnim.System -> null
        }
        try {
            activity.startActivity(intent, opts?.toBundle())
        } catch (e: Exception) {
            Toast.makeText(activity, "Δεν ανοίγει: ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    fun start(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    fun settings(ctx: Context, action: String) {
        if (!start(ctx, Intent(action))) start(ctx, Intent(Settings.ACTION_SETTINGS))
    }

    fun openFiles(ctx: Context) {
        val pkgs = listOf(
            "com.sec.android.app.myfiles", "com.google.android.apps.nbu.files",
            "com.google.android.documentsui", "com.android.documentsui", "com.mi.android.globalFileexplorer"
        )
        for (p in pkgs) {
            val i = ctx.packageManager.getLaunchIntentForPackage(p)
            if (i != null && start(ctx, i)) return
        }
        val root = DocumentsContract.buildRootUri("com.android.externalstorage.documents", "primary")
        if (!start(ctx, Intent(Intent.ACTION_VIEW).setDataAndType(root, DocumentsContract.Root.MIME_TYPE_ITEM))) {
            Toast.makeText(ctx, "Δεν βρέθηκε διαχείριση αρχείων", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("WrongConstant")
    fun expandStatusBar(ctx: Context, quick: Boolean) {
        runCatching {
            val sb = ctx.getSystemService("statusbar")
            val cls = Class.forName("android.app.StatusBarManager")
            cls.getMethod(if (quick) "expandSettingsPanel" else "expandNotificationsPanel").invoke(sb)
        }
    }

    private var torchOn = false
    fun toggleTorch(ctx: Context): Boolean {
        return runCatching {
            val cm = ctx.getSystemService(CameraManager::class.java)
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false
            torchOn = !torchOn
            cm.setTorchMode(id, torchOn)
            torchOn
        }.getOrDefault(false)
    }

    fun appInfo(ctx: Context, app: AppInfo) {
        val la = ctx.getSystemService(android.content.pm.LauncherApps::class.java)
        runCatching { la.startAppDetailsActivity(app.component, app.user, null, null) }
    }

    fun uninstall(ctx: Context, app: AppInfo) {
        start(ctx, Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.component.packageName}")))
    }

    fun openUrl(ctx: Context, url: String) {
        start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
