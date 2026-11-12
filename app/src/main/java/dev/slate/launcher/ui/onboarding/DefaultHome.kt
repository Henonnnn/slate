package dev.slate.launcher.ui.onboarding

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.slate.launcher.R

object DefaultHome {
    fun isHeld(context: Context): Boolean =
        context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)

    /** Role dialog when the system offers it, else the system "Default home app" page. */
    fun requestIntent(context: Context): Intent {
        val roles = context.getSystemService(RoleManager::class.java)
        return if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
            roles.createRequestRoleIntent(RoleManager.ROLE_HOME)
        } else {
            settingsIntent()
        }
    }

    fun settingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)
}

/**
 * Non-blocking card at the top of the list. The role dialog stops appearing after
 * repeated denials (the system returns immediately), so if the role is still not
 * held afterwards we fall through to the settings page.
 */
@Composable
fun DefaultHomeCard(onDismiss: () -> Unit, onResult: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onResult(DefaultHome.isHeld(context))
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val held = DefaultHome.isHeld(context)
        onResult(held)
        if (!held) runCatching { settingsLauncher.launch(DefaultHome.settingsIntent()) }
    }

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    ) {
        Column(Modifier.padding(start = 24.dp, end = 16.dp, top = 20.dp, bottom = 12.dp)) {
            Text(stringResource(R.string.default_home_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.default_home_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.not_now), color = MaterialTheme.colorScheme.onSurface)
                }
                Button(onClick = {
                    val intent = DefaultHome.requestIntent(context)
                    try {
                        if (intent.action == Settings.ACTION_HOME_SETTINGS) settingsLauncher.launch(intent) else roleLauncher.launch(intent)
                    } catch (_: ActivityNotFoundException) {
                        runCatching { settingsLauncher.launch(Intent(Settings.ACTION_SETTINGS)) }
                    }
                }) { Text(stringResource(R.string.default_home_action)) }
            }
        }
    }
}
