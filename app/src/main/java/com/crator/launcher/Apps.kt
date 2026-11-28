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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
) {
    /** Για αναζήτηση/ταξινόμηση: υπολογίζεται μία φορά, όχι σε κάθε πλήκτρο */
    val normLabel: String = label.norm()
}

private val MARKS = Regex("\\p{Mn}+")
fun String.norm(): String = MARKS.replace(Normalizer.normalize(lowercase(), Normalizer.Form.NFD), "")

// 168px αρκεί για εικονίδια ως ~60dp· κάθε bitmap = ~110KB, οπότε φτιάχνουμε ΜΟΝΟ όσα χρειάζονται
private const val S = 168

class AppRepository(private val context: Context, private val prefs: Prefs) {
    val apps = mutableStateOf<List<AppInfo>>(emptyList())
    val launcherApps: LauncherApps = context.getSystemService(LauncherApps::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var cacheJob: Job? = null
    private val mutex = Mutex()
    var pack: IconPack? = null
        private set
    var onLoaded: (() -> Unit)? = null

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = refresh(listOfNotNull(packageName))
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = refresh(listOfNotNull(packageName))
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = refresh(listOfNotNull(packageName))
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = refresh(packageNames?.toList().orEmpty())
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = refresh(packageNames?.toList().orEmpty())
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
        job = scope.launch { mutex.withLock {
            // Ψυχρή εκκίνηση (π.χ. το σύστημα έκλεισε τον launcher για μνήμη): τα εικονίδια
            // εμφανίζονται αμέσως από τον δίσκο, μετά ανανεώνονται στο παρασκήνιο
            if (apps.value.isEmpty()) {
                val cached = withContext(Dispatchers.IO) { runCatching { readCache() }.getOrNull() }
                if (!cached.isNullOrEmpty()) apps.value = cached
            }
            val list = withContext(Dispatchers.Default) { query() }
            apps.value = list
            onLoaded?.invoke()
            scheduleCacheWrite(0)
        } }
    }

    /** Μόνο τα πακέτα που άλλαξαν ξαναφτιάχνουν εικονίδια, όχι όλη η λίστα */
    fun refresh(packages: Collection<String>) {
        if (packages.isEmpty() || job?.isActive == true || prefs.iconPack in packages) { load(); return }
        scope.launch { mutex.withLock {
            val fresh = withContext(Dispatchers.Default) { packages.flatMap { queryPackage(it) } }
            apps.value = (apps.value.filter { it.component.packageName !in packages } + fresh).sortedBy { it.normLabel }
            onLoaded?.invoke()
            scheduleCacheWrite(3000)
        } }
    }

    // Η εγγραφή όλων των PNG είναι βαριά: γίνεται μία φορά αφού ηρεμήσουν οι αλλαγές
    private fun scheduleCacheWrite(waitMs: Long) {
        cacheJob?.cancel()
        cacheJob = scope.launch {
            delay(waitMs)
            val list = apps.value
            withContext(Dispatchers.IO) { runCatching { writeCache(list) } }
        }
    }

    private fun cacheSig() = "v1|$S|${prefs.iconPack}|${prefs.iconShape == IconShape.Original}|${prefs.themedIcons}"
    private val cacheDir get() = java.io.File(context.cacheDir, "icons")

    private fun readCache(): List<AppInfo> {
        val idx = java.io.File(cacheDir, "index.tsv")
        if (!idx.exists()) return emptyList()
        val lines = idx.readLines()
        if (lines.firstOrNull() != cacheSig()) return emptyList()
        val user = Process.myUserHandle()
        val labels = prefs.labels
        return lines.drop(1).mapIndexedNotNull { i, line ->
            val p = line.split('\t')
            if (p.size < 4) return@mapIndexedNotNull null
            val cn = ComponentName.unflattenFromString(p[0]) ?: return@mapIndexedNotNull null
            val bmp = android.graphics.BitmapFactory.decodeFile(java.io.File(cacheDir, "$i.png").path)?.asImageBitmap()
                ?: return@mapIndexedNotNull null
            val mono = if ('m' in p[3]) android.graphics.BitmapFactory.decodeFile(java.io.File(cacheDir, "${i}m.png").path)?.asImageBitmap() else null
            AppInfo(cn, labels[p[0]] ?: p[2], p[2], p[0], IconData(bmp, if ('f' in p[3]) bmp else null, mono), user)
        }
    }

    private fun writeCache(list: List<AppInfo>) {
        val dir = cacheDir
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val sb = StringBuilder(cacheSig()).append('\n')
        list.forEachIndexed { i, a ->
            java.io.File(dir, "$i.png").outputStream().use { a.icon.raw.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            a.icon.mono?.let { m -> java.io.File(dir, "${i}m.png").outputStream().use { m.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) } }
            val flags = (if (a.icon.full != null) "f" else "") + (if (a.icon.mono != null) "m" else "") + "-"
            sb.append(a.key).append('\t').append(a.label.replace('\t', ' ')).append('\t')
                .append(a.originalLabel.replace('\t', ' ')).append('\t').append(flags).append('\n')
        }
        java.io.File(dir, "index.tsv").writeText(sb.toString())
    }

    private fun query(): List<AppInfo> {
        val packName = prefs.iconPack
        pack = if (packName.isBlank()) null else IconPack.load(context, packName)
        return activities(null).sortedBy { it.normLabel }
    }

    private fun queryPackage(pkg: String): List<AppInfo> {
        if (pack == null && prefs.iconPack.isNotBlank()) pack = IconPack.load(context, prefs.iconPack)
        return activities(pkg)
    }

    private fun activities(pkg: String?): List<AppInfo> {
        val user = Process.myUserHandle()
        val labels = prefs.labels
        return launcherApps.getActivityList(pkg, user).mapNotNull { ai ->
            if (ai.componentName.packageName == context.packageName) return@mapNotNull null
            val key = ai.componentName.flattenToString()
            val orig = ai.label?.toString() ?: ai.componentName.packageName
            val icon = runCatching { buildIcon(ai, key) }.getOrNull() ?: return@mapNotNull null
            AppInfo(ai.componentName, labels[key] ?: orig, orig, key, icon, user)
        }
    }

    private fun buildIcon(ai: LauncherActivityInfo, key: String): IconData {
        val p = pack
        if (p != null) {
            val over = prefs.iconOverrides[key]
            val d = (over?.let { p.drawableByName(it) }) ?: p.drawableFor(ai.componentName)
            if (d != null) return IconData(d.toBitmap(S, S).asImageBitmap(), null, null)
        }
        val d = ai.getIcon(0)
        // Εξοικονόμηση RAM: ένα bitmap ανά εφαρμογή (το raw και το full μοιράζονται το ίδιο όταν γίνεται),
        // το μονόχρωμο μόνο αν είναι ανοιχτά τα themed εικονίδια
        val original = prefs.iconShape == IconShape.Original
        var mono: ImageBitmap? = null
        if (Build.VERSION.SDK_INT >= 33 && prefs.themedIcons && d is AdaptiveIconDrawable) d.monochrome?.let { mono = renderLayers(null, it) }
        if (d is AdaptiveIconDrawable && !original) {
            val full = renderLayers(d.background, d.foreground)
            return IconData(full, full, mono)
        }
        return IconData(d.toBitmap(S, S).asImageBitmap(), null, mono)
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
