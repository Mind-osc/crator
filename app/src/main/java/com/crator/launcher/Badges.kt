package com.crator.launcher

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.StatusBarNotification
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat

object Badges {
    val counts = mutableStateOf<Map<String, Int>>(emptyMap())
    fun of(pkg: String): Int = counts.value[pkg] ?: 0
    fun of(pkg: String, prefs: Prefs): Int = if (pkg in prefs.noBadge) 0 else of(pkg)

    fun hasAccess(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    fun openAccessSettings(ctx: Context) {
        Actions.settings(ctx, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }
}

class BadgeService : NotificationListenerService() {
    override fun onListenerConnected() = refresh()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) = refresh()
    override fun onListenerDisconnected() { Badges.counts.value = emptyMap() }

    private fun refresh() {
        val list = runCatching { activeNotifications }.getOrNull() ?: return
        val ranking = runCatching { currentRanking }.getOrNull()
        val r = Ranking()
        val m = HashMap<String, Int>()
        for (n in list) {
            val f = n.notification.flags
            if (n.isOngoing || f and Notification.FLAG_GROUP_SUMMARY != 0 || f and Notification.FLAG_FOREGROUND_SERVICE != 0) continue
            // σέβεται τη ρύθμιση «badge» της εφαρμογής/καναλιού και τις σιωπηλές ειδοποιήσεις (όπως ο launcher της Samsung)
            if (ranking != null && ranking.getRanking(n.key, r)) {
                if (!r.canShowBadge()) continue
                if (r.importance <= android.app.NotificationManager.IMPORTANCE_MIN) continue
            }
            m[n.packageName] = (m[n.packageName] ?: 0) + 1
        }
        Badges.counts.value = m
    }

    companion object {
        fun component(ctx: Context) = ComponentName(ctx, BadgeService::class.java)
    }
}

@Composable
fun BadgeView(count: Int, prefs: Prefs, iconSize: Dp, modifier: Modifier = Modifier) {
    if (!prefs.badges || count <= 0) return
    val c = Color(prefs.badgeColor)
    if (prefs.badgeStyle == BadgeStyle.Dot) {
        Box(modifier.size(iconSize * 0.24f).background(c, CircleShape))
    } else {
        Box(
            modifier.defaultMinSize(minWidth = iconSize * 0.36f, minHeight = iconSize * 0.36f)
                .background(c, CircleShape).padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(if (count > 99) "99+" else count.toString(), color = Color.White, fontSize = (iconSize.value * 0.2f).sp, fontWeight = FontWeight.Bold)
        }
    }
}
