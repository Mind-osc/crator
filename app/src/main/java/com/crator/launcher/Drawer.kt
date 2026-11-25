@file:OptIn(ExperimentalFoundationApi::class)

package com.crator.launcher

import android.graphics.Rect
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppDrawer(
    apps: List<AppInfo>,
    prefs: Prefs,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onLong: (AppInfo) -> Unit,
    onAskClaude: (String) -> Unit,
    onClose: () -> Unit
) {
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val nq = q.norm().trim()
    val list = remember(apps, nq, prefs.hidden) {
        apps.filter { it.key !in prefs.hidden && (nq.isEmpty() || it.label.norm().contains(nq) || it.component.packageName.contains(nq)) }
    }
    val close by rememberUpdatedState(onClose)
    val conn = remember {
        object : NestedScrollConnection {
            var acc = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag && available.y > 0) {
                    acc += available.y
                    if (acc > 260f) { acc = 0f; close() }
                } else if (available.y < 0) acc = 0f
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity { acc = 0f; return Velocity.Zero }
        }
    }
    LaunchedEffect(Unit) { if (prefs.drawerKeyboard) runCatching { focus.requestFocus() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(prefs.drawerColor).copy(alpha = prefs.drawerOpacity / 100f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Λαβή: σύρε κάτω για κλείσιμο
        Box(
            Modifier.fillMaxWidth().height(28.dp).pointerInput(Unit) {
                var t = 0f
                detectVerticalDragGestures(onDragStart = { t = 0f }, onDragEnd = { if (t > 80) close() }) { c, dy -> t += dy; c.consume() }
            },
            contentAlignment = Alignment.Center
        ) { Box(Modifier.size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.4f))) }

        Row(
            Modifier.padding(16.dp, 0.dp, 16.dp, 10.dp).fillMaxWidth().height(50.dp)
                .clip(RoundedCornerShape(25.dp)).background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, null, tint = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (q.isEmpty()) Text("Αναζήτηση σε ${list.size} εφαρμογές", color = Color.White.copy(alpha = 0.5f), fontSize = 15.sp)
                BasicTextField(
                    q, { q = it }, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        if (list.isNotEmpty()) onLaunch(list.first(), null) else if (q.isNotBlank()) onAskClaude(q)
                    }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
            }
            if (q.isNotEmpty()) Icon(Icons.Rounded.Close, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.clickable { q = "" })
        }

        if (q.isNotBlank()) {
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    .clickable { onAskClaude(q) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("Ρώτα τον Claude: «$q»", color = Color.White, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(8.dp))
        }

        if (prefs.drawerStyle == DrawerStyle.Grid) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(prefs.drawerCols),
                modifier = Modifier.weight(1f).nestedScroll(conn),
                contentPadding = PaddingValues(10.dp, 4.dp, 10.dp, 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(list, key = { it.key }) { app ->
                    AppCell(app, prefs, prefs.iconSize.dp, true, { onLaunch(app, it) }, { onLong(app) }, Modifier.animateItemPlacement())
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).nestedScroll(conn),
                contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 24.dp)
            ) {
                items(list, key = { it.key }) { app ->
                    var bounds: Rect? = null
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .combinedClickable(onClick = { onLaunch(app, bounds) }, onLongClick = { onLong(app) })
                            .padding(10.dp).animateItemPlacement(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(app, 40.dp, prefs)
                        Spacer(Modifier.width(16.dp))
                        Text(app.label, style = labelStyle(prefs, 18).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Start))
                    }
                }
            }
        }
    }
}
