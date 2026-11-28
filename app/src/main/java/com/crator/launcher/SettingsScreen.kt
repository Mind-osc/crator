@file:OptIn(ExperimentalMaterial3Api::class)

package com.crator.launcher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class Section(val id: String, val title: String, val sub: String, val icon: ImageVector)

private val SECTIONS = listOf(
    Section("home", "Αρχική οθόνη", "Πλέγμα, σελίδες, ρολόι, αναζήτηση, χρώματα", Icons.Rounded.Home),
    Section("icons", "Εικονίδια", "Σχήμα, μέγεθος, icon pack, ετικέτες, themed", Icons.Rounded.Palette),
    Section("dock", "Dock", "Στυλ, στοίχιση, φόντο, μέγεθος", Icons.Rounded.Dock),
    Section("effects", "Εφέ & ταχύτητες", "Εφέ, ταχύτητα σε κάθε κίνηση, άμεση εκκίνηση", Icons.Rounded.Animation),
    Section("gestures", "Χειρονομίες", "Swipe πάνω/κάτω, διπλό tap", Icons.Rounded.Gesture),
    Section("folders", "Φάκελοι, widgets & badges", "Στυλ φακέλων, στρογγύλεμα widgets, ειδοποιήσεις", Icons.Rounded.Folder),
    Section("drawer", "Συρτάρι εφαρμογών", "Στυλ, χρώματα, αναζήτηση, κρυφές εφαρμογές", Icons.Rounded.Apps),
    Section("dash", "Ταμπλό συστήματος", "Τίτλος, χρώμα, μέγεθος, περιεχόμενο", Icons.Rounded.Insights),
    Section("about", "Backup & σχετικά", "Αντίγραφο ρυθμίσεων, προεπιλογή launcher", Icons.Rounded.Backup)
)

@Composable
fun SettingsScreen(
    activity: MainActivity,
    prefs: Prefs,
    repo: AppRepository,
    section: String?,
    onSection: (String?) -> Unit,
    onClose: () -> Unit
) {
    val sec = SECTIONS.firstOrNull { it.id == section }
    Column(Modifier.fillMaxSize().background(Smoke.tint.copy(alpha = 0.86f)).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp, 8.dp, 8.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (sec == null) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Color.White,
                modifier = Modifier.clip(CircleShape).clickable { if (sec == null) onClose() else onSection(null) }.padding(12.dp)
            )
            Text(sec?.title ?: "Ρυθμίσεις", color = Smoke.pearl, fontSize = 28.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.6).sp)
        }
        if (sec == null) {
            LazyColumn(contentPadding = PaddingValues(12.dp)) {
                itemsIndexed(SECTIONS) { i, s ->
                    StaggerIn(i) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).smokedGlass(RoundedCornerShape(22.dp), alpha = 0.45f, tint = Smoke.graphite)
                            .clickable { onSection(s.id) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                            Icon(s.icon, null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(s.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(s.sub, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                        }
                    }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)) {
                when (sec.id) {
                    "home" -> HomeSection(prefs)
                    "icons" -> IconsSection(prefs, repo)
                    "dock" -> DockSection(prefs)
                    "effects" -> EffectsSection(prefs)
                    "gestures" -> GesturesSection(prefs)
                    "drawer" -> DrawerSection(prefs, repo)
                    "folders" -> FoldersSection(prefs)
                    "dash" -> DashSection(prefs)
                    "about" -> AboutSection(activity, prefs)
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

// ---------------- Κοινά controls ----------------

@Composable
fun Group(title: String) {
    Text(title, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 22.dp, bottom = 6.dp, start = 4.dp))
}

@Composable
fun SliderPref(title: String, value: Int, range: IntRange, suffix: String = "", onChange: (Int) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp)) {
        Row {
            Text(title, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text("${local.toInt()}$suffix", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
        }
        Slider(
            value = local, onValueChange = { local = it }, onValueChangeFinished = { onChange(local.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceIn(0, 60).let { if (range.last - range.first > 60) 0 else it }
        )
    }
}

@Composable
fun SwitchPref(title: String, checked: Boolean, sub: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onChange(!checked) }.padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            if (sub != null) Text(sub, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
        }
        Switch(checked, onChange)
    }
}

@Composable
fun <E> ChoicePref(title: String, options: List<E>, selected: E, label: (E) -> String, onSelect: (E) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(title, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { o ->
                FilterChip(
                    selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) },
                    colors = FilterChipDefaults.filterChipColors(labelColor = Color.White)
                )
            }
        }
    }
}

private val SWATCHES = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFF15161F, 0xFF262A40, 0xFF8C9BFF, 0xFF6FC3FF, 0xFF7DE3C2, 0xFF9BE37D,
    0xFFFFE07D, 0xFFFFB86B, 0xFFFF8A65, 0xFFFF7FB0, 0xFFC792EA, 0xFFD6DCFF, 0xFF3A3F58, 0xFF0B0C14
).map { it.toInt() }

@Composable
fun ColorPref(title: String, color: Int, onChange: (Int) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text("#" + String.format("%06X", color and 0xFFFFFF), color = Smoke.mist, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(24.dp).clip(CircleShape).background(Color(color)).border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape).clickable { picking = true })
        }
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                // Ελεύθερη επιλογή: ακριβώς το χρώμα που θέλεις
                Box(
                    Modifier.size(32.dp).clip(CircleShape)
                        .background(Brush.sweepGradient((0..6).map { Color.hsv(it * 60f % 360f, 0.8f, 1f) }))
                        .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                        .clickable { picking = true },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Colorize, "Ακριβές χρώμα", tint = Color.White, modifier = Modifier.size(16.dp)) }
            }
            items(SWATCHES) { c ->
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                        .border(if (c == color) 3.dp else 1.dp, if (c == color) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.25f), CircleShape)
                        .clickable { onChange(c) }
                )
            }
        }
    }
    if (picking) ColorPickerDialog(color, { picking = false }) { onChange(it) }
}

// ---------------- Ενότητες ----------------

@Composable
private fun HomeSection(p: Prefs) {
    Group("Πλέγμα")
    SliderPref("Στήλες", p.cols, 3..7) { p.cols = it }
    SliderPref("Γραμμές", p.rows, 3..9) { p.rows = it }
    ChoicePref("Ενδιάμεσες γραμμές", listOf(1, 2, 3, 4), p.fine, { when (it) { 1 -> "Όχι"; 2 -> "Μισά"; 3 -> "Τρίτα"; else -> "Τέταρτα" } }) { p.setFineGrid(it) }
    SliderPref("Σελίδες αρχικής", p.homePages, 1..8) { p.homePages = it }
    SwitchPref("Δείκτης σελίδων", p.pageIndicator) { p.pageIndicator = it }
    Group("Ρολόι")
    ChoicePref("Στυλ ρολογιού", ClockStyle.entries, p.clockStyle, { it.label }) { p.clockStyle = it }
    Group("Μπάρα αναζήτησης")
    ChoicePref("Θέση", SearchPos.entries, p.searchPos, { it.label }) { p.searchPos = it }
    SliderPref("Ύψος", p.searchHeight, 30..56, "dp") { p.searchHeight = it }
    ColorPref("Χρώμα", p.searchColor) { p.searchColor = it }
    SliderPref("Αδιαφάνεια", p.searchOpacity, 0..100, "%") { p.searchOpacity = it }
    ColorPref("Χρώμα κειμένου & εικονιδίων", p.searchTextColor) { p.searchTextColor = it }
    Group("Εμφάνιση")
    SliderPref("Σκίαση ταπετσαρίας", p.wallpaperDim, 0..70, "%") { p.wallpaperDim = it }
    SwitchPref("Χρώματα από ταπετσαρία", p.dynamicColors, "Material You") { p.dynamicColors = it }
    if (!p.dynamicColors) ColorPref("Χρώμα έμφασης", p.accent) { p.accent = it }
}

@Composable
private fun IconsSection(p: Prefs, repo: AppRepository) {
    val ctx = LocalContext.current
    val preview = repo.apps.value.take(5)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF1C2030)).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        preview.forEach { AppCell(it, p, p.iconSize.dp, p.showLabels, {}, {}, Modifier.width((p.iconSize + 12).dp)) }
    }
    Group("Σχήμα")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(IconShape.entries) { s ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { p.iconShape = s }.padding(6.dp)) {
                Box(
                    Modifier.size(44.dp).clip(shapeFor(s, p.iconCorner))
                        .background(if (s == p.iconShape) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.25f))
                )
                Text(s.label, color = Color.White.copy(alpha = if (s == p.iconShape) 1f else 0.6f), fontSize = 10.sp)
            }
        }
    }
    if (p.iconShape == IconShape.Rounded) SliderPref("Γωνίες", p.iconCorner, 0..50, "%") { p.iconCorner = it }
    Group("Μέγεθος & εμφάνιση")
    SliderPref("Μέγεθος", p.iconSize, 32..84, "dp") { p.iconSize = it }
    SliderPref("Κλίμακα μέσα στο σχήμα", p.iconScale, 60..140, "%") { p.iconScale = it }
    SliderPref("Αδιαφάνεια", p.iconOpacity, 20..100, "%") { p.iconOpacity = it }
    SliderPref("Κορεσμός χρώματος", p.iconSaturation, 0..200, "%") { p.iconSaturation = it }
    SwitchPref("Σκιά", p.iconShadow) { p.iconShadow = it }
    SliderPref("Περίγραμμα", p.iconBorder, 0..4, "dp") { p.iconBorder = it }
    if (p.iconBorder > 0) ColorPref("Χρώμα περιγράμματος", p.iconBorderColor) { p.iconBorderColor = it }
    SwitchPref("Φόντο σε παλιά εικονίδια", p.iconPlate, "Για εικονίδια που δεν είναι adaptive") { p.iconPlate = it }
    if (p.iconPlate) ColorPref("Χρώμα φόντου", p.plateColor) { p.plateColor = it }
    Group("Themed (μονόχρωμα)")
    SwitchPref("Themed εικονίδια", p.themedIcons, "Όσες εφαρμογές υποστηρίζουν μονόχρωμο εικονίδιο") { p.themedIcons = it }
    if (p.themedIcons) {
        ColorPref("Χρώμα συμβόλου", p.themedFg) { p.themedFg = it }
        ColorPref("Χρώμα φόντου", p.themedBg) { p.themedBg = it }
    }
    Group("Icon pack")
    val packs = remember { IconPack.list(ctx) }
    ChoicePref("Πακέτο", listOf("" to "Κανένα") + packs, packs.firstOrNull { it.first == p.iconPack } ?: ("" to "Κανένα"), { it.second }) {
        p.iconPack = it.first; repo.load()
    }
    if (packs.isEmpty()) Text("Δεν βρέθηκαν icon packs. Εγκατέστησε ένα από το Play Store (συμβατά με Nova/ADW).", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.padding(4.dp))
    if (p.iconOverrides.isNotEmpty()) TextButton(onClick = { p.iconOverrides = emptyMap(); repo.load() }) { Text("Επαναφορά προσαρμοσμένων εικονιδίων") }
    Group("Ετικέτες")
    SwitchPref("Εμφάνιση ετικετών", p.showLabels) { p.showLabels = it }
    SliderPref("Μέγεθος", p.labelSize, 8..18, "sp") { p.labelSize = it }
    SliderPref("Γραμμές", p.labelLines, 1..2) { p.labelLines = it }
    SwitchPref("Έντονα", p.labelBold) { p.labelBold = it }
    SwitchPref("Σκιά κειμένου", p.labelShadow) { p.labelShadow = it }
    ColorPref("Χρώμα", p.labelColor) { p.labelColor = it }
    Group("Πάτημα")
    ChoicePref("Εφέ πατήματος", PressEffect.entries, p.pressEffect, { it.label }) { p.pressEffect = it }
}

@Composable
private fun DockSection(p: Prefs) {
    Group("Στυλ")
    ChoicePref("Στυλ dock", DockStyle.entries, p.dockStyle, { it.label }) { p.dockStyle = it }
    ChoicePref("Στοίχιση", DockAlign.entries, p.dockAlign, { it.label }) { p.dockAlign = it }
    Group("Μέγεθος")
    SliderPref("Εφαρμογές", p.dockCount, 3..8) { p.dockCount = it }
    SliderPref("Μέγεθος εικονιδίων", p.dockIconSize, 32..80, "dp") { p.dockIconSize = it }
    SwitchPref("Ετικέτες", p.dockLabels) { p.dockLabels = it }
    Group("Φόντο")
    ColorPref("Χρώμα", p.dockColor) { p.dockColor = it }
    SliderPref("Αδιαφάνεια", p.dockOpacity, 0..100, "%") { p.dockOpacity = it }
    SliderPref("Γωνίες", p.dockCorner, 0..44, "dp") { p.dockCorner = it }
    SliderPref("Απόσταση από κάτω", p.dockMargin, 0..48, "dp") { p.dockMargin = it }
    SwitchPref("Σκιά", p.dockShadow) { p.dockShadow = it }
}

@Composable
private fun EffectsSection(p: Prefs) {
    Group("Αλλαγή σελίδων")
    ChoicePref("Εφέ", PageEffect.entries, p.pageEffect, { it.label }) { p.pageEffect = it }
    Group("Άνοιγμα εφαρμογών")
    ChoicePref("Κίνηση", OpenAnim.entries, p.openAnim, { it.label }) { p.openAnim = it }
    Group("Ταχύτητες")
    SliderPref("Γενική ταχύτητα", p.animSpeed, 40..250, "%") { p.animSpeed = it }
    SliderPref("Αλλαγή σελίδων", p.speedPage, 40..250, "%") { p.speedPage = it }
    SliderPref("Συρτάρι, αναζήτηση, ρυθμίσεις", p.speedOverlay, 40..250, "%") { p.speedOverlay = it }
    SliderPref("Μενού, φάκελοι, popups", p.speedSheet, 40..250, "%") { p.speedSheet = it }
    SliderPref("Σύρσιμο & παραμερισμός", p.speedDrag, 40..250, "%") { p.speedDrag = it }
    SliderPref("Πάτημα & εμφάνιση εικονιδίων", p.speedIcon, 40..250, "%") { p.speedIcon = it }
    Group("Άνοιγμα εφαρμογών")
    SwitchPref("Εκκίνηση με το άγγιγμα", p.instantLaunch, "Η εφαρμογή ανοίγει μόλις ακουμπήσεις, χωρίς να περιμένει να σηκώσεις το δάχτυλο") { p.instantLaunch = it }
    Group("Γενικά")
    SliderPref("Ένταση blur", p.blurStrength, 0..40) { p.blurStrength = it }
    ChoicePref("Εφέ πατήματος εικονιδίων", PressEffect.entries, p.pressEffect, { it.label }) { p.pressEffect = it }
}

@Composable
private fun GesturesSection(p: Prefs) {
    Group("Αρχική οθόνη")
    ChoicePref("Swipe κάτω", GestureAction.entries, p.swipeDown, { it.label }) { p.swipeDown = it }
    ChoicePref("Swipe πάνω", GestureAction.entries, p.swipeUp, { it.label }) { p.swipeUp = it }
    ChoicePref("Διπλό tap", GestureAction.entries, p.doubleTap, { it.label }) { p.doubleTap = it }
}

@Composable
private fun FoldersSection(p: Prefs) {
    val ctx = LocalContext.current
    val resumed = rememberResumed()
    val access = remember(resumed) { Badges.hasAccess(ctx) }
    Group("Φάκελοι")
    ChoicePref("Στυλ εικονιδίου", FolderStyle.entries, p.folderStyle, { it.label }) { p.folderStyle = it }
    ColorPref("Χρώμα", p.folderColor) { p.folderColor = it }
    SliderPref("Αδιαφάνεια", p.folderOpacity, 10..100, "%") { p.folderOpacity = it }
    SliderPref("Στήλες στο άνοιγμα", p.folderCols, 3..5) { p.folderCols = it }
    Text("Σύρε μια εφαρμογή πάνω σε άλλη για νέο φάκελο.", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.padding(4.dp))
    Group("Widgets")
    SliderPref("Απόσταση", p.widgetPadding, 0..20, "dp") { p.widgetPadding = it }
    SliderPref("Στρογγύλεμα", p.widgetCorner, 0..40, "dp") { p.widgetCorner = it }
    Text("Πάτα παρατεταμένα σε κενό σημείο της αρχικής → Widgets.", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.padding(4.dp))
    Group("Badges ειδοποιήσεων")
    if (!access) {
        TextButton(onClick = { Badges.openAccessSettings(ctx) }) { Text("Δώσε πρόσβαση στις ειδοποιήσεις (απαιτείται)") }
    }
    SwitchPref("Badges", p.badges, if (access) "Ενεργή πρόσβαση" else "Χωρίς πρόσβαση ακόμα") { p.badges = it }
    ChoicePref("Στυλ", BadgeStyle.entries, p.badgeStyle, { it.label }) { p.badgeStyle = it }
    ColorPref("Χρώμα", p.badgeColor) { p.badgeColor = it }
    Group("Διάταξη")
    SwitchPref("Κλείδωμα διάταξης", p.lockLayout, "Αποτρέπει μετακινήσεις κατά λάθος") { p.lockLayout = it }
}

@Composable
private fun DrawerSection(p: Prefs, repo: AppRepository) {
    Group("Εμφάνιση")
    ChoicePref("Στυλ", DrawerStyle.entries, p.drawerStyle, { it.label }) { p.drawerStyle = it }
    SliderPref("Στήλες", p.drawerCols, 3..7) { p.drawerCols = it }
    SliderPref("Μέγεθος εικονιδίων", p.drawerIconSize, 32..64, "dp") { p.drawerIconSize = it }
    SliderPref("Πλάτος πάνελ", p.drawerWidth, 60..100, "%") { p.drawerWidth = it }
    SliderPref("Ύψος πάνελ", p.drawerHeight, 40..100, "%") { p.drawerHeight = it }
    ColorPref("Χρώμα φόντου", p.drawerColor) { p.drawerColor = it }
    SliderPref("Αδιαφάνεια", p.drawerOpacity, 0..100, "%") { p.drawerOpacity = it }
    ColorPref("Χρώμα ετικετών", p.drawerLabelColor) { p.drawerLabelColor = it }
    Group("Αναζήτηση στο συρτάρι")
    SwitchPref("Μπάρα αναζήτησης", p.drawerSearch) { p.drawerSearch = it }
    if (p.drawerSearch) {
        ChoicePref("Θέση", listOf(false, true), p.drawerSearchBottom, { if (it) "Κάτω" else "Πάνω" }) { p.drawerSearchBottom = it }
        SwitchPref("Άνοιγμα πληκτρολογίου", p.drawerKeyboard) { p.drawerKeyboard = it }
    }
    Text("Το χρώμα και το ύψος της μπάρας ρυθμίζονται στην Αρχική οθόνη → Μπάρα αναζήτησης.", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.padding(4.dp))
    Group("Κρυφές εφαρμογές")
    repo.apps.value.forEach { app ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, 32.dp, p)
            Spacer(Modifier.width(12.dp))
            Text(app.label, color = Color.White, modifier = Modifier.weight(1f))
            Switch(app.key in p.hidden, { h -> p.hidden = if (h) p.hidden + app.key else p.hidden - app.key })
        }
    }
}

@Composable
private fun DashSection(p: Prefs) {
    SwitchPref("Αριστερή σελίδα ταμπλό", p.dashboardEnabled, "RAM, CPU, χώρος, δίκτυο, μπαταρία + συντομεύσεις") { p.dashboardEnabled = it }
    SliderPref("Ανανέωση κάθε", p.dashRefresh, 500..5000, " ms") { p.dashRefresh = it }
    SliderPref("Ιστορικό γραφημάτων", p.dashHistory, 20..180, " σημεία") { p.dashHistory = it }
    Group("Εμφάνιση")
    var title by remember { mutableStateOf(p.dashTitle) }
    OutlinedTextField(title, { title = it; p.dashTitle = it }, label = { Text("Τίτλος (κενό = χωρίς τίτλο)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    SwitchPref("Υπότιτλος (συσκευή, Android, χρόνος λειτουργίας)", p.dashShowSub) { p.dashShowSub = it }
    SliderPref("Μέγεθος", p.dashScale, 70..130, "%") { p.dashScale = it }
    ColorPref("Χρώμα καρτών", p.dashColor) { p.dashColor = it }
    SliderPref("Αδιαφάνεια καρτών", p.dashOpacity, 0..100, "%") { p.dashOpacity = it }
    Group("Περιεχόμενο")
    Text("Πάτα παρατεταμένα οποιαδήποτε κάρτα ή κουμπί στο ταμπλό: ✕ για αφαίρεση, σύρσιμο για αλλαγή θέσης, + για νέες κάρτες, συντομεύσεις και εφαρμογές.", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.padding(4.dp))
    TextButton(onClick = { p.dashItems = DASH_DEFAULT_ITEMS }) { Text("Επαναφορά προεπιλογής") }
}

@Composable
private fun AboutSection(activity: MainActivity, p: Prefs) {
    val cm = activity.getSystemService(ClipboardManager::class.java)
    Group("Launcher")
    TextButton(onClick = { activity.requestHomeRole() }) { Text("Ορισμός ως προεπιλογή") }
    TextButton(onClick = { Actions.settings(activity, Settings.ACTION_HOME_SETTINGS) }) { Text("Ρυθμίσεις αρχικής εφαρμογής συστήματος") }
    Group("Backup")
    TextButton(onClick = {
        cm.setPrimaryClip(ClipData.newPlainText("crator-backup", p.exportJson()))
        Toast.makeText(activity, "Το backup αντιγράφηκε στο πρόχειρο", Toast.LENGTH_SHORT).show()
    }) { Text("Αντιγραφή backup στο πρόχειρο") }
    TextButton(onClick = {
        val t = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        if (p.importJson(t)) activity.recreate() else Toast.makeText(activity, "Μη έγκυρο backup", Toast.LENGTH_SHORT).show()
    }) { Text("Επαναφορά από πρόχειρο") }
    Group("Έκδοση")
    Text("Crator 0.3", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(4.dp))
}

// ---------------- Επιλογή εικονιδίου από icon pack ----------------

@Composable
fun IconPicker(app: AppInfo, prefs: Prefs, repo: AppRepository, onDone: () -> Unit) {
    val pack = repo.pack
    var q by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Εικονίδιο · ${app.label}") },
        text = {
            if (pack == null) {
                Text("Διάλεξε πρώτα icon pack: Ρυθμίσεις → Εικονίδια → Icon pack.")
            } else {
                Column {
                    OutlinedTextField(q, { q = it }, label = { Text("Αναζήτηση") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    val names = remember(q) { pack.drawableNames.filter { q.isBlank() || it.contains(q.lowercase()) }.take(400) }
                    LazyVerticalGrid(GridCells.Fixed(5), Modifier.height(340.dp).padding(top = 8.dp)) {
                        items(names) { n ->
                            val bmp by produceState<ImageBitmap?>(null, n) {
                                value = withContext(Dispatchers.Default) { runCatching { pack.drawableByName(n)?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull() }
                            }
                            Box(Modifier.padding(4.dp).size(48.dp).clip(RoundedCornerShape(10.dp)).clickable {
                                prefs.iconOverrides = prefs.iconOverrides + (app.key to n); repo.load(); onDone()
                            }, contentAlignment = Alignment.Center) {
                                bmp?.let { androidx.compose.foundation.Image(it, n, Modifier.fillMaxSize()) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (app.key in prefs.iconOverrides) { prefs.iconOverrides = prefs.iconOverrides - app.key; repo.load() }
                onDone()
            }) { Text(if (app.key in prefs.iconOverrides) "Επαναφορά" else "Κλείσιμο") }
        }
    )
}
