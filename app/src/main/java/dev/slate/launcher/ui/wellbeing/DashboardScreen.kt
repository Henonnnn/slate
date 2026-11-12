package dev.slate.launcher.ui.wellbeing

import dev.slate.launcher.ui.components.backProgress
import dev.slate.launcher.ui.components.rememberBackProgress
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.slate.launcher.R
import dev.slate.launcher.apps.AppEntry
import dev.slate.launcher.container
import dev.slate.launcher.ui.appearance.PanelBackdrop
import dev.slate.launcher.ui.appearance.ScreenTitle
import dev.slate.launcher.ui.home.AppIcon
import dev.slate.launcher.ui.theme.LocalSlatePalette
import dev.slate.launcher.ui.theme.SlateText
import dev.slate.launcher.wellbeing.DayUsage
import java.time.format.TextStyle

/**
 * Screen time over the wallpaper: today's total and unlocks, the last seven days, and today's
 * apps. Tapping an app opens its Usage Breaker settings. Read from the device on open; nothing
 * is stored.
 */
@Composable
fun DashboardScreen(
    apps: List<AppEntry>,
    breakerPackages: Set<String>,
    usageText: (Long) -> String,
    onApp: (packageName: String, label: String) -> Unit,
    onClose: () -> Unit,
) {
    val backModifier = Modifier.backProgress(rememberBackProgress(onBack = onClose))
    val context = LocalContext.current
    val palette = LocalSlatePalette.current
    val repository = context.container.usageRepository
    var refresh by remember { mutableIntStateOf(0) }
    // Coming back from the usage-access screen re-reads.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val days by produceState<List<DayUsage>?>(null, refresh) { value = repository.days(7) }
    val hasAccess = remember(refresh) { repository.hasAccess() }
    val byPackage = remember(apps) { apps.groupBy { it.packageName } }

    PanelBackdrop(solid = true, modifier = backModifier) {
        ScreenTitle(stringResource(R.string.screen_time), onClose)
        LazyColumn(Modifier.weight(1f)) {
            if (!hasAccess) {
                item(key = "access") {
                    Column(Modifier.padding(horizontal = 28.dp, vertical = 12.dp)) {
                        Text(stringResource(R.string.usage_access_body), style = SlateText.hint, color = palette.onWallpaper)
                        TextButton(onClick = repository::openSettings, modifier = Modifier.padding(top = 8.dp)) {
                            Text(stringResource(R.string.usage_access_action), color = palette.accent)
                        }
                    }
                }
                return@LazyColumn
            }
            val week = days ?: return@LazyColumn
            val today = week.last()
            item(key = "today") {
                Column(Modifier.padding(horizontal = 28.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.calendar_today).uppercase(), style = SlateText.sectionLabel, color = palette.onWallpaperMuted)
                    Text(usageText(today.totalMs), style = SlateText.clock.copy(fontSize = 52.sp), color = palette.onWallpaper)
                    Text(pluralStringResource(R.plurals.unlocks, today.unlocks, today.unlocks), style = SlateText.hint, color = palette.onWallpaperMuted)
                }
            }
            item(key = "week") { WeekChart(week, usageText) }
            item(key = "apps-label") {
                Text(
                    stringResource(R.string.screen_time_apps).uppercase(),
                    style = SlateText.sectionLabel,
                    color = palette.onWallpaperMuted,
                    modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 8.dp),
                )
            }
            val rows = today.byPackage.entries.filter { it.value >= 60_000 }.sortedByDescending { it.value }
            val max = rows.firstOrNull()?.value ?: 1L
            items(rows, key = { it.key }) { (pkg, ms) ->
                val entry = byPackage[pkg]?.firstOrNull()
                // Apps without a launcher entry (system screens) still have a name.
                val label = entry?.label ?: remember(pkg) {
                    runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                }
                AppUsageRow(entry, label, ms, ms.toFloat() / max, pkg in breakerPackages, usageText) { onApp(pkg, label) }
            }
            item(key = "bottom") { Spacer(Modifier.height(48.dp)) }
    }
    }
}

@Composable
private fun WeekChart(week: List<DayUsage>, usageText: (Long) -> String) {
    val palette = LocalSlatePalette.current
    val locale = LocalConfiguration.current.locales[0]
    val max = week.maxOf { it.totalMs }.coerceAtLeast(60_000)
    val description = week.joinToString { "${it.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)} ${usageText(it.totalMs)}" }
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 16.dp).semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val slot = size.width / week.size
            val barWidth = slot * 0.46f
            week.forEachIndexed { i, day ->
                val h = (day.totalMs.toFloat() / max) * size.height
                val isToday = i == week.lastIndex
                drawRoundRect(
                    color = if (isToday) palette.accent else palette.onWallpaperMuted.copy(alpha = 0.45f),
                    topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - h.coerceAtLeast(3f)),
                    size = Size(barWidth, h.coerceAtLeast(3f)),
                    cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            week.forEach { day ->
                Text(
                    day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                    style = SlateText.scrubberLetter.copy(shadow = null),
                    color = palette.onWallpaperMuted,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun AppUsageRow(entry: AppEntry?, label: String, ms: Long, fraction: Float, breaker: Boolean, usageText: (Long) -> String, onClick: () -> Unit) {
    val palette = LocalSlatePalette.current
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 28.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (entry != null) AppIcon(entry, 36.dp) else Spacer(Modifier.size(36.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = SlateText.appLabel.copy(fontSize = 17.sp), color = palette.onWallpaper, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (breaker) {
                    Text(
                        stringResource(R.string.breaker_badge),
                        style = SlateText.scrubberLetter.copy(shadow = null),
                        color = palette.accent,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .height(3.dp)
                    .background(palette.accent.copy(alpha = 0.7f), RoundedCornerShape(2.dp)),
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(usageText(ms), style = SlateText.hint.copy(shadow = null), color = palette.onWallpaperMuted)
    }
}
