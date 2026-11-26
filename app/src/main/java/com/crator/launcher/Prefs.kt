package com.crator.launcher

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class PrefState<T>(initial: T, private val save: (T) -> Unit) : ReadWriteProperty<Any?, T> {
    private val st = mutableStateOf(initial)
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = st.value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        st.value = value
        save(value)
    }
}

enum class IconShape(val label: String) {
    Original("Συστήματος"), Circle("Κύκλος"), Squircle("Squircle"), Rounded("Στρογγυλεμένο"),
    Square("Τετράγωνο"), Teardrop("Δάκρυ"), Leaf("Φύλλο"), Pebble("Βότσαλο"),
    Hexagon("Εξάγωνο"), Octagon("Οκτάγωνο"), Diamond("Ρόμβος")
}

enum class PageEffect(val label: String) {
    Slide("Κύλιση"), Fade("Σβήσιμο"), Zoom("Ζουμ"), Depth("Βάθος"), Stack("Στοίβα"),
    Cube("Κύβος"), CubeIn("Κύβος μέσα"), Flip("Αναποδογύρισμα"), Windmill("Ανεμόμυλος"),
    Carousel("Καρουζέλ"), Accordion("Ακορντεόν"), Tilt("Κλίση")
}

enum class OpenAnim(val label: String) {
    ZoomIcon("Ζουμ από εικονίδιο"), Reveal("Αποκάλυψη"), Fade("Σβήσιμο"), SlideUp("Από κάτω"),
    SlideSide("Από πλάι"), Bounce("Αναπήδηση"), ZoomCenter("Ζουμ κέντρου"), System("Συστήματος"), None("Χωρίς")
}

enum class PressEffect(val label: String) { Bounce("Ελατήριο"), Shrink("Σμίκρυνση"), Tilt("Κλίση"), None("Χωρίς") }

enum class DockStyle(val label: String) {
    Smoke("Φιμέ γυαλί"), Floating("Αιωρούμενο"), Glass("Γυαλί"), Island("Νησί"), Classic("Κλασικό"),
    Minimal("Χωρίς φόντο"), Carousel("Καρουζέλ"), Arc("Τόξο")
}

enum class DockAlign(val label: String) { Spread("Ομοιόμορφα"), Center("Κέντρο"), Start("Αριστερά"), End("Δεξιά") }

enum class SearchPos(val label: String) { AboveDock("Πάνω από dock"), InDock("Μέσα στο dock"), Top("Πάνω"), Hidden("Κρυφή") }

enum class GestureAction(val label: String) {
    None("Τίποτα"), Claude("Αναζήτηση"), Drawer("Συρτάρι εφαρμογών"), Notifications("Ειδοποιήσεις"),
    QuickSettings("Γρήγορες ρυθμίσεις"), Dashboard("Ταμπλό"), LauncherSettings("Ρυθμίσεις Crator")
}

enum class ClockStyle(val label: String) { Big("Μεγάλο"), Minimal("Μίνιμαλ"), Stacked("Στοίβα"), Hidden("Κρυφό") }

enum class DrawerStyle(val label: String) { Grid("Πλέγμα"), List("Λίστα") }

enum class FolderStyle(val label: String) { Grid("Πλέγμα 2×2"), Stack("Στοίβα"), Single("Πρώτο εικονίδιο") }

enum class BadgeStyle(val label: String) { Count("Αριθμός"), Dot("Τελεία") }

enum class ClaudeMode(val label: String) { Auto("Αυτόματο"), Api("API key"), Web("claude.ai"), App("Εφαρμογή Claude") }

enum class PanelStyle(val label: String) { Glass("Γυαλί"), Aurora("Aurora"), Solid("Συμπαγές") }

class Prefs(private val context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("crator", Context.MODE_PRIVATE)

    private val secure: SharedPreferences by lazy {
        try {
            val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                context, "crator_secure", key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("crator_secure_fb", Context.MODE_PRIVATE)
        }
    }

    private fun int(key: String, def: Int) = PrefState(sp.getInt(key, def)) { sp.edit().putInt(key, it).apply() }
    private fun bool(key: String, def: Boolean) = PrefState(sp.getBoolean(key, def)) { sp.edit().putBoolean(key, it).apply() }
    private fun str(key: String, def: String) = PrefState(sp.getString(key, def) ?: def) { sp.edit().putString(key, it).apply() }
    private inline fun <reified E : Enum<E>> enm(key: String, def: E): PrefState<E> {
        val v = runCatching { enumValueOf<E>(sp.getString(key, def.name) ?: def.name) }.getOrDefault(def)
        return PrefState(v) { sp.edit().putString(key, it.name).apply() }
    }

    // ---------- Αρχική ----------
    var cols by int("cols", 4)
    var rows by int("rows", 5)
    var homePages by int("home_pages", 2)
    var clockStyle by enm("clock_style", ClockStyle.Big)
    var pageIndicator by bool("page_indicator", true)
    var searchPos by enm("search_pos", SearchPos.AboveDock)
    var wallpaperDim by int("wall_dim", 0)
    var dynamicColors by bool("dynamic_colors", true)
    var accent by int("accent", 0xFF8C9BFF.toInt())

    // ---------- Εικονίδια ----------
    var iconSize by int("icon_size", 56)
    var iconShape by enm("icon_shape", IconShape.Squircle)
    var iconCorner by int("icon_corner", 28)
    var iconScale by int("icon_scale", 100)
    var iconOpacity by int("icon_opacity", 100)
    var iconSaturation by int("icon_sat", 100)
    var iconShadow by bool("icon_shadow", true)
    var iconBorder by int("icon_border", 0)
    var iconBorderColor by int("icon_border_color", 0x66FFFFFF)
    var iconPlate by bool("icon_plate", true)
    var plateColor by int("plate_color", 0xFFFFFFFF.toInt())
    var themedIcons by bool("themed", false)
    var themedFg by int("themed_fg", 0xFFD6DCFF.toInt())
    var themedBg by int("themed_bg", 0xFF262A40.toInt())
    var iconPack by str("icon_pack", "")
    var showLabels by bool("labels", true)
    var labelSize by int("label_size", 12)
    var labelColor by int("label_color", 0xFFFFFFFF.toInt())
    var labelBold by bool("label_bold", false)
    var labelShadow by bool("label_shadow", true)
    var labelLines by int("label_lines", 1)
    var pressEffect by enm("press_effect", PressEffect.Bounce)

    // ---------- Dock ----------
    var dockStyle by enm("dock_style", DockStyle.Smoke)
    var dockAlign by enm("dock_align", DockAlign.Spread)
    var dockCount by int("dock_count", 5)
    var dockIconSize by int("dock_icon_size", 54)
    var dockColor by int("dock_color", 0xFF15161F.toInt())
    var dockOpacity by int("dock_opacity", 45)
    var dockCorner by int("dock_corner", 30)
    var dockMargin by int("dock_margin", 12)
    var dockLabels by bool("dock_labels", false)
    var dockShadow by bool("dock_shadow", false)

    // ---------- Εφέ ----------
    var pageEffect by enm("page_effect", PageEffect.Depth)
    var openAnim by enm("open_anim", OpenAnim.ZoomIcon)
    var animSpeed by int("anim_speed", 100)
    var blurStrength by int("blur", 24)

    // ---------- Χειρονομίες ----------
    var swipeDown by enm("g_down", GestureAction.Claude)
    var swipeUp by enm("g_up", GestureAction.Drawer)
    var doubleTap by enm("g_double", GestureAction.None)

    // ---------- Συρτάρι ----------
    var drawerStyle by enm("drawer_style", DrawerStyle.Grid)
    var drawerCols by int("drawer_cols", 4)
    var drawerColor by int("drawer_color", 0xFF0B0C14.toInt())
    var drawerOpacity by int("drawer_opacity", 82)
    var drawerKeyboard by bool("drawer_kb", false)

    // ---------- Claude ----------
    var claudeModel by str("claude_model", "claude-sonnet-5-5")
    var maxTokens by int("max_tokens", 1024)
    var claudeOpenApps by bool("claude_open", true)
    var claudeInstructions by str("claude_instr", "")
    var panelStyle by enm("panel_style", PanelStyle.Glass)
    var claudeMode by enm("claude_mode", ClaudeMode.Auto)
    fun effectiveClaudeMode(): ClaudeMode = when (claudeMode) {
        ClaudeMode.Auto -> if (apiKey.isBlank()) ClaudeMode.Web else ClaudeMode.Api
        else -> claudeMode
    }
    var claudeKeyboard by bool("claude_kb", true)
    var apiKey by PrefState(secure.getString("api_key", "") ?: "") { secure.edit().putString("api_key", it).apply() }

    // ---------- Ταμπλό ----------
    var dashboardEnabled by bool("dash", true)
    var dashRefresh by int("dash_refresh", 1000)
    var dashHistory by int("dash_hist", 60)

    // ---------- Διάταξη ----------
    var firstRun by bool("first_run", true)
    var dock by PrefState(loadList("dock")) { saveList("dock", it) }
    var pages by PrefState(loadPages()) { savePages(it) }
    var hidden by PrefState(loadList("hidden").toSet()) { saveList("hidden", it.toList()) }
    var labels by PrefState(loadMap("labels_map")) { saveMap("labels_map", it) }
    var iconOverrides by PrefState(loadMap("icon_over")) { saveMap("icon_over", it) }
    var folders by PrefState(loadFolders()) { saveFolders(it) }
    var lockLayout by bool("lock_layout", false)

    // ---------- Φάκελοι ----------
    var folderStyle by enm("folder_style", FolderStyle.Grid)
    var folderColor by int("folder_color", 0xFF2A2D40.toInt())
    var folderOpacity by int("folder_opacity", 75)
    var folderCols by int("folder_cols", 4)

    // ---------- Badges ----------
    var badges by bool("badges", true)
    var badgeStyle by enm("badge_style", BadgeStyle.Count)
    var badgeColor by int("badge_color", 0xFFFF5A5F.toInt())

    // ---------- Αναζήτηση ----------
    var searchHeight by int("search_h", 40)
    var searchColor by int("search_color", 0xFF15161F.toInt())
    var searchOpacity by int("search_opacity", 55)
    var searchTextColor by int("search_text", 0xFFA9ACBD.toInt())
    var drawerSearch by bool("drawer_search", true)
    var drawerSearchBottom by bool("drawer_search_bottom", false)
    var drawerLabelColor by int("drawer_label", 0xFFF3F2F7.toInt())
    var drawerFolders by PrefState(loadList("drawer_folders")) { saveList("drawer_folders", it) }

    // ---------- Ταχύτητες (%) ----------
    var speedOverlay by int("sp_overlay", 100)
    var speedSheet by int("sp_sheet", 100)
    var speedDrag by int("sp_drag", 100)
    var speedIcon by int("sp_icon", 100)
    var speedPage by int("sp_page", 100)
    var instantLaunch by bool("instant_launch", false)

    // ---------- Ταμπλό: εμφάνιση ----------
    var dashTitle by str("dash_title", "Σύστημα")
    var dashShowSub by bool("dash_sub", true)
    var dashColor by int("dash_color", 0xFF14151D.toInt())
    var dashOpacity by int("dash_opacity", 58)
    var dashScale by int("dash_scale", 100)
    var dashShortcuts by bool("dash_shortcuts", true)
    var dashCpu by bool("dash_cpu", true)
    var dashMem by bool("dash_mem", true)
    var dashStorage by bool("dash_storage", true)
    var dashNet by bool("dash_net", true)
    var dashBattery by bool("dash_bat", true)
    var dashThermal by bool("dash_thermal", true)
    var dashOrder by PrefState(loadList("dash_order")) { saveList("dash_order", it) }
    var dashItems by PrefState(loadList("dash_items")) { saveList("dash_items", it) }
    var dashHiddenShortcuts by PrefState(loadList("dash_hidden_sc").toSet()) { saveList("dash_hidden_sc", it.toList()) }

    // ---------- Widgets ----------
    var widgetPadding by int("widget_pad", 6)
    var widgetCorner by int("widget_corner", 22)

    private fun loadFolders(): Map<String, Folder> = runCatching {
        val o = JSONObject(sp.getString("folders", "{}") ?: "{}")
        o.keys().asSequence().associateWith { id ->
            val f = o.getJSONObject(id)
            val a = f.getJSONArray("a")
            Folder(f.optString("n", "Φάκελος"), List(a.length()) { a.getString(it) }, f.optInt("c", 0))
        }
    }.getOrDefault(emptyMap())

    private fun saveFolders(v: Map<String, Folder>) {
        val o = JSONObject()
        v.forEach { (id, f) -> o.put(id, JSONObject().put("n", f.name).put("a", JSONArray(f.apps)).put("c", f.color)) }
        sp.edit().putString("folders", o.toString()).apply()
    }

    private fun loadList(k: String): List<String> = runCatching {
        val a = JSONArray(sp.getString(k, "[]")); List(a.length()) { a.getString(it) }
    }.getOrDefault(emptyList())

    private fun saveList(k: String, v: List<String>) = sp.edit().putString(k, JSONArray(v).toString()).apply()

    private fun loadMap(k: String): Map<String, String> = runCatching {
        val o = JSONObject(sp.getString(k, "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    private fun saveMap(k: String, v: Map<String, String>) = sp.edit().putString(k, JSONObject(v).toString()).apply()

    private fun loadPages(): List<Map<Int, String>> = runCatching {
        val a = JSONArray(sp.getString("pages", "[]"))
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            o.keys().asSequence().associate { it.toInt() to o.getString(it) }
        }
    }.getOrDefault(emptyList())

    private fun savePages(v: List<Map<Int, String>>) {
        val a = JSONArray()
        v.forEach { m -> a.put(JSONObject().apply { m.forEach { (c, k) -> put(c.toString(), k) } }) }
        sp.edit().putString("pages", a.toString()).apply()
    }

    // ---------- Backup ----------
    fun exportJson(): String {
        val o = JSONObject()
        sp.all.forEach { (k, v) ->
            o.put(k, JSONObject().put("t", when (v) {
                is Int -> "i"; is Boolean -> "b"; is Long -> "l"; is Float -> "f"; else -> "s"
            }).put("v", v.toString()))
        }
        return o.toString()
    }

    fun importJson(json: String): Boolean = runCatching {
        val o = JSONObject(json)
        val e = sp.edit().clear()
        o.keys().forEach { k ->
            val it = o.getJSONObject(k); val v = it.getString("v")
            when (it.getString("t")) {
                "i" -> e.putInt(k, v.toInt()); "b" -> e.putBoolean(k, v.toBoolean())
                "l" -> e.putLong(k, v.toLong()); "f" -> e.putFloat(k, v.toFloat())
                else -> e.putString(k, v)
            }
        }
        e.commit()
    }.isSuccess
}
