package com.crator.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.text.Normalizer

class IconData(val raw: ImageBitmap, val full: ImageBitmap?, val mono: ImageBitmap?)

class AppInfo(
    val component: ComponentName,
    val label: String,
    val originalLabel: String,
    val key: String,
    val icon: IconData,
    val user: UserHandle
)

private val MARKS = Regex("\\p{Mn}+")
fun String.norm(): String = MARKS.replace(Normalizer.normalize(lowercase(), Normalizer.Form.NFD), "")

private const val S = 192

class AppRepository(private val context: Context, private val prefs: Prefs) {
    val apps = mutableStateOf<List<AppInfo>>(emptyList())
    val launcherApps: LauncherApps = context.getSystemService(LauncherApps::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    var pack: IconPack? = null
        private set
    var onLoaded: (() -> Unit)? = null

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = load()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = load()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = load()
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = load()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = load()
    }

    init {
        launcherApps.registerCallback(callback)
    }

    fun dispose() {
        runCatching { launcherApps.unregisterCallback(callback) }
        scope.cancel()
    }

    fun load() {
        job?.cancel()
        job = scope.launch {
            val list = withContext(Dispatchers.Default) { query() }
            apps.value = list
            onLoaded?.invoke()
        }
    }

    private fun query(): List<AppInfo> {
        val packName = prefs.iconPack
        pack = if (packName.isBlank()) null else IconPack.load(context, packName)
        val user = Process.myUserHandle()
        val out = ArrayList<AppInfo>()
        val labels = prefs.labels
        for (ai in launcherApps.getActivityList(null, user)) {
            if (ai.componentName.packageName == context.packageName) continue
            val key = ai.componentName.flattenToString()
            val orig = ai.label?.toString() ?: ai.componentName.packageName
            val icon = runCatching { buildIcon(ai, key) }.getOrNull() ?: continue
            out += AppInfo(ai.componentName, labels[key] ?: orig, orig, key, icon, user)
        }
        out.sortBy { it.label.norm() }
        return out
    }

    private fun buildIcon(ai: LauncherActivityInfo, key: String): IconData {
        val p = pack
        if (p != null) {
            val over = prefs.iconOverrides[key]
            val d = (over?.let { p.drawableByName(it) }) ?: p.drawableFor(ai.componentName)
            if (d != null) return IconData(d.toBitmap(S, S).asImageBitmap(), null, null)
        }
        val d = ai.getIcon(0)
        val raw = d.toBitmap(S, S).asImageBitmap()
        var full: ImageBitmap? = null
        var mono: ImageBitmap? = null
        if (d is AdaptiveIconDrawable) {
            full = renderLayers(d.background, d.foreground)
            if (Build.VERSION.SDK_INT >= 33) d.monochrome?.let { mono = renderLayers(null, it) }
        }
        return IconData(raw, full, mono)
    }

    private fun renderLayers(bg: Drawable?, fg: Drawable?): ImageBitmap {
        val bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val inset = S / 4
        listOf(bg, fg).forEach { layer ->
            layer?.let { it.setBounds(-inset, -inset, S + inset, S + inset); it.draw(c) }
        }
        return bmp.asImageBitmap()
    }

    fun firstAppOf(pkg: String?): String? {
        if (pkg == null) return null
        return launcherApps.getActivityList(pkg, Process.myUserHandle()).firstOrNull()?.componentName?.flattenToString()
    }

    /** Προεπιλεγμένες εφαρμογές για το dock στην πρώτη εκκίνηση */
    fun defaultDock(): List<String> {
        val pm = context.packageManager
        fun pkgOf(i: Intent) = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            ?.takeIf { it != "android" }
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY)
        )
        return intents.mapNotNull { firstAppOf(pkgOf(it)) }.distinct()
    }

    // ---------- Shortcuts εφαρμογών ----------
    fun shortcuts(app: AppInfo): List<ShortcutInfo> = try {
        if (!launcherApps.hasShortcutHostPermission()) emptyList()
        else launcherApps.getShortcuts(
            LauncherApps.ShortcutQuery()
                .setPackage(app.component.packageName)
                .setActivity(app.component)
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                ), app.user
        )?.take(5) ?: emptyList()
    } catch (e: Exception) { emptyList() }

    fun shortcutIcon(si: ShortcutInfo): ImageBitmap? = runCatching {
        launcherApps.getShortcutIconDrawable(si, context.resources.displayMetrics.densityDpi)
            ?.toBitmap(96, 96)?.asImageBitmap()
    }.getOrNull()

    fun startShortcut(si: ShortcutInfo) {
        runCatching { launcherApps.startShortcut(si, null, null) }
    }
}

class IconPack(private val res: Resources, private val pkg: String, private val map: Map<String, String>) {
    fun drawableFor(cn: ComponentName): Drawable? = map[cn.flattenToString()]?.let { drawableByName(it) }

    fun drawableByName(name: String): Drawable? {
        val id = res.getIdentifier(name, "drawable", pkg)
        return if (id != 0) runCatching { res.getDrawable(id, null) }.getOrNull() else null
    }

    val drawableNames: List<String> by lazy { map.values.distinct().sorted() }

    companion object {
        private val ACTIONS = listOf(
            "org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.teslacoilsw.launcher.THEME",
            "com.gau.go.launcherex.theme", "com.anddoes.launcher.THEME", "com.fede.launcher.THEME_ICONPACK"
        )

        fun list(ctx: Context): List<Pair<String, String>> {
            val pm = ctx.packageManager
            val out = LinkedHashMap<String, String>()
            ACTIONS.forEach { a ->
                runCatching {
                    pm.queryIntentActivities(Intent(a), 0).forEach { ri ->
                        val p = ri.activityInfo.packageName
                        out[p] = ri.loadLabel(pm).toString()
                    }
                }
            }
            return out.map { it.key to it.value }.sortedBy { it.second.lowercase() }
        }

        fun load(ctx: Context, pkg: String): IconPack? = runCatching {
            val res = ctx.packageManager.getResourcesForApplication(pkg)
            val map = HashMap<String, String>()
            val id = res.getIdentifier("appfilter", "xml", pkg)
            val parser: XmlPullParser = if (id != 0) res.getXml(id) else {
                XmlPullParserFactory.newInstance().newPullParser().apply {
                    setInput(res.assets.open("appfilter.xml"), "utf-8")
                }
            }
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component")
                    val dr = parser.getAttributeValue(null, "drawable")
                    if (comp != null && dr != null && comp.startsWith("ComponentInfo{")) {
                        val inner = comp.removePrefix("ComponentInfo{").removeSuffix("}")
                        val parts = inner.split("/")
                        if (parts.size == 2) {
                            val cls = if (parts[1].startsWith(".")) parts[0] + parts[1] else parts[1]
                            map["${parts[0]}/$cls"] = dr
                        }
                    }
                }
                ev = parser.next()
            }
            IconPack(res, pkg, map)
        }.getOrNull()
    }
}
