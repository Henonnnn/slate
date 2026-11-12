package dev.slate.launcher

import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import dev.slate.launcher.data.PopupItem
import dev.slate.launcher.data.Popups
import dev.slate.launcher.ui.theme.SlateTheme
import kotlinx.coroutines.launch

/**
 * Handles an app's "add shortcut to home screen" request (CONFIRM_PIN_SHORTCUT). The system
 * only routes these to the default home app. Accepting pins the shortcut; it then shows in the
 * owning app's long-press sheet and in search, and — if that app is a favourite — in its pop-up.
 */
class PinShortcutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launcherApps = getSystemService(LauncherApps::class.java)
        val request = launcherApps.getPinItemRequest(intent)
        val info = request?.shortcutInfo
        if (request == null || request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT || info == null || !request.isValid) {
            finish()
            return
        }
        val container = container
        val user = info.userHandle ?: Process.myUserHandle()
        val label = (info.shortLabel ?: info.longLabel)?.toString().orEmpty().ifEmpty { info.id }
        val appLabel = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(info.`package`, 0)).toString()
        }.getOrDefault(info.`package`)

        setContent {
            SlateTheme {
                AlertDialog(
                    onDismissRequest = ::finish,
                    title = { Text(stringResource(R.string.pin_shortcut_title)) },
                    text = { Text(stringResource(R.string.pin_shortcut_body, label, appLabel)) },
                    confirmButton = {
                        TextButton(onClick = {
                            if (request.isValid && request.accept()) {
                                val key = container.shortcutRepository.keyOf(info, user)
                                val owner = container.shortcutRepository.ownerAppKey(info, user)
                                lifecycleScope.launch {
                                    container.settingsRepository.update { s ->
                                        val popups = if (owner != null && owner in s.favourites) {
                                            Popups.add(s.popups, owner, PopupItem.shortcut(key))
                                        } else {
                                            s.popups
                                        }
                                        s.copy(pinnedShortcuts = (s.pinnedShortcuts + key).distinct(), popups = popups)
                                    }
                                    finish()
                                }
                            } else {
                                finish()
                            }
                        }) { Text(stringResource(R.string.add), color = MaterialTheme.colorScheme.primary) }
                    },
                    dismissButton = {
                        TextButton(onClick = ::finish) { Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurface) }
                    },
                )
            }
        }
    }
}
