package com.crator.launcher

/** Ένας φάκελος: όνομα, εφαρμογές (component keys), χρώμα (0 = προεπιλογή) */
data class Folder(val name: String, val apps: List<String>, val color: Int = 0)

/**
 * Στοιχεία πλέγματος:
 *  - εφαρμογή: "pkg/cls"
 *  - φάκελος:  "folder:<id>"
 *  - widget:   "widget:<appWidgetId>:<spanX>:<spanY>"
 *  - widget Crator: "cw:<type>:<spanX>:<spanY>"  (clock, claude, system, toggles)
 */
object Item {
    fun isFolder(s: String) = s.startsWith("folder:")
    fun folderId(s: String) = s.removePrefix("folder:")
    fun folder(id: String) = "folder:$id"
    fun isWidget(s: String) = s.startsWith("widget:")
    fun widget(id: Int, sx: Int, sy: Int) = "widget:$id:$sx:$sy"
    fun widgetId(s: String) = s.split(":").getOrNull(1)?.toIntOrNull() ?: -1
    fun isCrator(s: String) = s.startsWith("cw:")
    fun crator(type: String, sx: Int, sy: Int) = "cw:$type:$sx:$sy"
    fun cratorType(s: String) = s.split(":").getOrNull(1) ?: ""
    fun isSized(s: String) = isWidget(s) || isCrator(s)
    fun resized(s: String, sx: Int, sy: Int): String {
        val p = s.split(":")
        return "${p[0]}:${p[1]}:$sx:$sy"
    }
    fun isApp(s: String) = !isFolder(s) && !isWidget(s) && !isCrator(s)
    fun span(s: String): Pair<Int, Int> {
        if (!isSized(s)) return 1 to 1
        val p = s.split(":")
        return (p.getOrNull(2)?.toIntOrNull() ?: 1) to (p.getOrNull(3)?.toIntOrNull() ?: 1)
    }
}

// με ενδιάμεσες γραμμές προστίθενται (fine-1) λεπτές γραμμές στο κάτω μέρος,
// ώστε και η τελευταία σειρά να έχει ενδιάμεσες θέσεις από κάτω της
val Prefs.gridRows: Int get() = fine.coerceIn(1, 4).let { f -> rows * f + (f - 1) }

/** Μέγεθος στοιχείου σε κελιά του (λεπτού) πλέγματος */
fun Prefs.spanOf(item: String): Pair<Int, Int> =
    if (Item.isSized(item)) Item.span(item) else 1 to appSpanY.coerceIn(1, fine.coerceIn(1, 4))

/** Αλλαγή ενδιάμεσων γραμμών: οι θέσεις και τα ύψη των widgets μετατρέπονται */
fun Prefs.setFineGrid(newF: Int) {
    val oldF = fine.coerceIn(1, 4)
    val nf = newF.coerceIn(1, 4)
    if (nf == oldF) return
    pages = pages.map { m ->
        val out = LinkedHashMap<Int, String>()
        m.forEach { (cell, item) ->
            val r = cell / cols; val c = cell % cols
            val nr = r * nf / oldF
            val it2 = if (Item.isSized(item)) {
                val (sx, sy) = Item.span(item)
                Item.resized(item, sx, (sy * nf / oldF).coerceAtLeast(1))
            } else item
            val key = nr * cols + c
            if (key !in out) out[key] = it2
        }
        out
    }
    fine = nf
    appSpanY = nf
}

/** κελί που καλύπτεται → κελί-άγκυρα του στοιχείου */
fun Prefs.occupied(page: Int, exclude: Int? = null): Map<Int, Int> {
    val out = HashMap<Int, Int>()
    val m = pages.getOrNull(page) ?: return out
    for ((anchor, item) in m) {
        if (anchor == exclude) continue
        val (sx, sy) = spanOf(item)
        val r = anchor / cols; val c = anchor % cols
        for (dy in 0 until sy) for (dx in 0 until sx) {
            val rr = r + dy; val cc = c + dx
            if (rr < gridRows && cc < cols) out[rr * cols + cc] = anchor
        }
    }
    return out
}

fun Prefs.fits(page: Int, anchor: Int, sx: Int, sy: Int, exclude: Int? = null): Boolean {
    val r = anchor / cols; val c = anchor % cols
    if (anchor < 0 || c + sx > cols || r + sy > gridRows) return false
    val occ = occupied(page, exclude)
    for (dy in 0 until sy) for (dx in 0 until sx) if ((r + dy) * cols + (c + dx) in occ) return false
    return true
}

fun Prefs.findSpot(page: Int, sx: Int, sy: Int): Int? =
    (0 until cols * gridRows).firstOrNull { fits(page, it, sx, sy) }

fun Prefs.setHome(page: Int, cell: Int, item: String) {
    val ps = pages.map { it.toMutableMap() }.toMutableList()
    while (ps.size <= page) ps.add(mutableMapOf())
    ps[page][cell] = item
    pages = ps
}

fun Prefs.removeHomeAt(page: Int, cell: Int) {
    pages = pages.mapIndexed { i, m -> if (i == page) m - cell else m }
}

fun Prefs.findOnHome(item: String): Pair<Int, Int>? {
    pages.forEachIndexed { p, m -> m.entries.firstOrNull { it.value == item }?.let { return p to it.key } }
    return null
}

fun Prefs.isOnHome(key: String) = pages.any { it.containsValue(key) }
fun Prefs.isInDock(key: String) = key in dock

/** Βάζει ένα στοιχείο στην πρώτη ελεύθερη θέση· αν δεν χωράει, προσθέτει σελίδα */
fun Prefs.placeOnHome(item: String, preferPage: Int = 0): Boolean {
    val (sx, sy) = spanOf(item)
    val n = homePages
    val start = preferPage.coerceIn(0, n - 1)
    for (p in (start until n) + (0 until start)) {
        findSpot(p, sx, sy)?.let { setHome(p, it, item); return true }
    }
    if (homePages < 8 && sx <= cols && sy <= gridRows) {
        homePages = homePages + 1
        setHome(homePages - 1, 0, item)
        return true
    }
    return false
}

fun Prefs.addToHome(key: String, preferPage: Int = 0) = placeOnHome(key, preferPage)

fun Prefs.removeFromHome(item: String) {
    pages = pages.map { m -> m.filterValues { it != item } }
}

/** Αντικαθιστά (ή αφαιρεί αν new == null) ένα στοιχείο σε αρχική και dock */
fun Prefs.replaceItem(old: String, new: String?) {
    pages = pages.map { m ->
        if (new == null) m.filterValues { it != old } else m.mapValues { if (it.value == old) new else it.value }
    }
    dock = if (new == null) dock - old else dock.map { if (it == old) new else it }
}

fun Prefs.insertDock(index: Int, item: String) {
    val d = dock.toMutableList()
    d.remove(item)
    d.add(index.coerceIn(0, d.size), item)
    dock = d
}

fun Prefs.addToDock(key: String) { if (key !in dock) dock = dock + key }
fun Prefs.removeFromDock(key: String) { dock = dock - key }

fun Prefs.createFolder(a: String, b: String): String {
    val id = System.currentTimeMillis().toString(36)
    folders = folders + (id to Folder("Φάκελος", listOf(a, b)))
    return Item.folder(id)
}

fun Prefs.addToFolder(id: String, key: String) {
    val f = folders[id] ?: return
    if (key in f.apps) return
    folders = folders + (id to f.copy(apps = f.apps + key))
}

fun Prefs.removeFromFolder(id: String, key: String) {
    val f = folders[id] ?: return
    val rest = f.apps - key
    when {
        rest.size >= 2 -> folders = folders + (id to f.copy(apps = rest))
        rest.size == 1 -> { replaceItem(Item.folder(id), rest[0]); folders = folders - id }
        else -> { replaceItem(Item.folder(id), null); folders = folders - id }
    }
}

fun Prefs.ungroupFolder(id: String) {
    val f = folders[id] ?: return
    val item = Item.folder(id)
    val pos = findOnHome(item)
    if (f.apps.isEmpty()) replaceItem(item, null) else replaceItem(item, f.apps[0])
    folders = folders - id
    f.apps.drop(1).forEach { placeOnHome(it, pos?.first ?: 0) }
}

fun Prefs.setPage(page: Int, map: Map<Int, String>) {
    val ps = pages.toMutableList()
    while (ps.size <= page) ps.add(emptyMap())
    ps[page] = map
    pages = ps
}

/**
 * Παραμερισμός (σαν iPhone): για να μπει κάτι στο insertAt, τα εικονίδια από εκεί
 * μέχρι το πρώτο κενό μετακινούνται κατά μία θέση (μπροστά, αλλιώς πίσω).
 * Τα widgets μένουν ακίνητα. Επιστρέφει παλιό κελί → νέο κελί, ή null αν δεν γίνεται.
 */
fun Prefs.reflowMoves(page: Int, insertAt: Int, exclude: Int?): Map<Int, Int>? {
    if (fine > 1 && appSpanY > 1) return null // με ενδιάμεσες γραμμές τα εικονίδια μπαίνουν όπου υπάρχει χώρος
    val n = cols * gridRows
    val m = pages.getOrNull(page) ?: return null
    val occ = occupied(page, exclude)
    val owner = occ[insertAt] ?: return null
    if (m[owner]?.let { Item.isSized(it) } != false) return null
    fun isWidget(c: Int) = occ[c]?.let { a -> m[a]?.let { Item.isSized(it) } } == true
    fun isFree(c: Int) = c !in occ
    val fwd = (insertAt + 1 until n).firstOrNull { isFree(it) }
    if (fwd != null) {
        val chain = (insertAt until fwd).filter { !isWidget(it) }
        return chain.zip(chain.drop(1) + fwd).toMap()
    }
    val back = (insertAt - 1 downTo 0).firstOrNull { isFree(it) } ?: return null
    val chain = (back + 1..insertAt).filter { !isWidget(it) }
    return chain.zip(listOf(back) + chain.dropLast(1)).toMap()
}

fun Prefs.applyReflow(page: Int, moves: Map<Int, Int>, insertAt: Int, item: String) {
    val pm = (pages.getOrNull(page) ?: emptyMap()).toMutableMap()
    val moved = moves.mapNotNull { (from, to) -> pm[from]?.let { to to it } }
    moves.keys.forEach { pm.remove(it) }
    moved.forEach { (to, it) -> pm[to] = it }
    pm[insertAt] = item
    setPage(page, pm)
}
