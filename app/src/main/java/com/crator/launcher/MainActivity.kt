package com.crator.launcher

import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    lateinit var prefs: Prefs
    lateinit var repo: AppRepository
    lateinit var widgets: WidgetController
    val homeEvents = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        prefs = Prefs(this)
        repo = AppRepository(this, prefs)
        widgets = WidgetController(this, prefs)
        repo.onLoaded = {
            if (prefs.firstRun && repo.apps.value.isNotEmpty()) {
                prefs.firstRun = false
                val dock = repo.defaultDock()
                prefs.dock = dock
                // Πρώτη σελίδα: μερικές χρήσιμες εφαρμογές
                val extra = listOf("com.android.settings", "com.google.android.youtube", "com.google.android.apps.maps",
                    "com.google.android.gm", "com.spotify.music", "com.whatsapp", "com.instagram.android", "com.android.vending")
                extra.mapNotNull { repo.firstAppOf(it) }.filter { it !in dock }.forEach { prefs.addToHome(it, 0) }
            }
        }
        repo.load()

        setContent {
            val ctx = LocalContext.current
            val scheme = if (prefs.dynamicColors) dynamicDarkColorScheme(ctx)
            else darkColorScheme(primary = Color(prefs.accent), secondary = Color(prefs.accent))
            MaterialTheme(colorScheme = scheme) {
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    // Επαναφόρτωση εικονιδίων όταν αλλάζει το pack
                    val first = remember { booleanArrayOf(true) }
                    LaunchedEffect(prefs.iconPack) { if (first[0]) first[0] = false else repo.load() }
                    CratorRoot(this, prefs, repo)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) homeEvents.intValue++
    }

    override fun onStart() {
        super.onStart()
        widgets.startListening()
    }

    override fun onStop() {
        super.onStop()
        widgets.stopListening()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!widgets.onResult(requestCode, resultCode)) {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onDestroy() {
        repo.dispose()
        super.onDestroy()
    }

    /** Θόλωμα του φόντου (ταπετσαρίας) πίσω από το παράθυρο, όπου υποστηρίζεται */
    fun setBlurBehind(radius: Int) {
        runCatching {
            if (!windowManager.isCrossWindowBlurEnabled) return
            if (radius > 0) window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = radius }
        }
    }

    fun requestHomeRole() {
        val rm = getSystemService(RoleManager::class.java)
        try {
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                @Suppress("DEPRECATION")
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 7)
            } else {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            }
        } catch (e: Exception) {
            runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
        }
    }
}
