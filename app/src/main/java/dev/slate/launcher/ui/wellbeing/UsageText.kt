package dev.slate.launcher.ui.wellbeing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import dev.slate.launcher.R
import dev.slate.launcher.wellbeing.UsageMath

/** Localised duration formatter ("1 h 05 min") for the wellbeing screens. */
@Composable
fun rememberUsageText(): (Long) -> String {
    val h = stringResource(R.string.unit_hours)
    val m = stringResource(R.string.unit_minutes)
    val lt = stringResource(R.string.less_than_minute)
    return remember(h, m, lt) { { ms: Long -> UsageMath.format(ms, h, m, lt) } }
}
