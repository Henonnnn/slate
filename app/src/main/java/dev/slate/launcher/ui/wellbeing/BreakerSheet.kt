package dev.slate.launcher.ui.wellbeing

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.slate.launcher.R
import dev.slate.launcher.container
import dev.slate.launcher.wellbeing.BreakerReminder
import dev.slate.launcher.wellbeing.BreakerRule

private val LIMITS = listOf<Int?>(null, 15, 30, 60, 120)

/** Usage Breaker settings for one app, from its long-press sheet or the dashboard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakerSheet(
    label: String,
    packageName: String,
    rule: BreakerRule?,
    usageText: (Long) -> String,
    onRule: (BreakerRule?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val usage = context.container.usageRepository
    val used by produceState<Long?>(null, packageName) { value = usage.usedToday(packageName) }
    // The reminder needs notification permission on Android 13+; ask when the breaker goes on.
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        properties = ModalBottomSheetProperties(isAppearanceLightStatusBars = false, isAppearanceLightNavigationBars = !isSystemInDarkTheme()),
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.breaker_sheet_title, label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 6.dp),
            )
            Text(
                stringResource(R.string.breaker_sheet_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            val on = rule != null
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(on, role = Role.Switch) { enable ->
                        onRule(if (enable) BreakerRule() else null)
                        if (enable && !BreakerReminder.canNotify(context)) {
                            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        if (!enable) BreakerReminder.cancel(context, packageName)
                    }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.breaker_pause_toggle), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = on, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary))
            }
            if (on) {
                Text(
                    stringResource(R.string.breaker_daily_limit),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 4.dp),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LIMITS.forEach { limit ->
                        FilterChip(
                            selected = rule?.dailyLimitMinutes == limit,
                            onClick = { onRule(BreakerRule(dailyLimitMinutes = limit)) },
                            label = { Text(if (limit == null) stringResource(R.string.breaker_no_limit) else usageText(limit * 60_000L)) },
                            colors = FilterChipDefaults.filterChipColors(selectedLabelColor = MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
            val u = used
            if (u != null) {
                Text(
                    stringResource(R.string.breaker_used, usageText(u)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
                )
            } else {
                Text(
                    stringResource(R.string.usage_access_needed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
                )
                TextButton(onClick = usage::openSettings, modifier = Modifier.padding(start = 12.dp)) {
                    Text(stringResource(R.string.usage_access_action), color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                stringResource(R.string.breaker_scope_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
            )
        }
    }
}
