package dev.slate.launcher.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A backup file: the settings plus enough context to reject the wrong file. */
@Serializable
data class BackupFile(
    val format: String = Backup.FORMAT,
    val version: Int = Backup.VERSION,
    val appVersion: String = "",
    val createdAtMillis: Long = 0,
    /** App widgets can't travel (see [Backup]); kept so restore can say how many are missing. */
    val appWidgetsLeftOut: Int = 0,
    val settings: LauncherSettings,
)

/**
 * Backup format, pure so it's tested. A backup is the settings JSON in a small envelope.
 *
 * App widgets are left out: their ids belong to the system's binding on this install and mean
 * nothing in a restore, and each needs the system's permission dialog anyway. Restoring keeps
 * whatever app widgets are on the home screen now, so nothing live gets orphaned.
 */
object Backup {
    const val FORMAT = "slate-backup"
    const val VERSION = 1
    const val MIME = "application/json"
    /** Far above any real settings file; refuses something that's clearly not one. */
    const val MAX_BYTES = 4 * 1024 * 1024

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    sealed interface Decoded {
        data class Ok(val file: BackupFile) : Decoded
        data object NotABackup : Decoded
        data object TooNew : Decoded
    }

    /** App widget ids mean nothing on another install, so only Slate's own widgets travel. */
    private fun portableOnly(stacks: List<WidgetStack>): List<WidgetStack> = stacks.mapNotNull { s ->
        s.items.filter { it.kind != WidgetRef.Kind.App }.takeIf { it.isNotEmpty() }?.let { s.copy(items = it) }
    }

    fun encode(settings: LauncherSettings, appVersion: String, nowMs: Long): String {
        val portable = portableOnly(settings.widgets)
        val leftOut = settings.usedWidgetIds().size
        val file = BackupFile(
            appVersion = appVersion,
            createdAtMillis = nowMs,
            appWidgetsLeftOut = leftOut,
            settings = settings.copy(widgets = portable, popupWidgets = settings.popupWidgets.mapValues { (_, stacks) -> portableOnly(stacks) }.filterValues { it.isNotEmpty() }),
        )
        return json.encodeToString(BackupFile.serializer(), file)
    }

    fun decode(text: String): Decoded {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (_: SerializationException) {
            return Decoded.NotABackup
        } catch (_: IllegalArgumentException) {
            return Decoded.NotABackup
        }
        return when {
            file.format != FORMAT -> Decoded.NotABackup
            file.version > VERSION -> Decoded.TooNew
            else -> Decoded.Ok(file.copy(settings = sanitize(file.settings)))
        }
    }

    /** A hand-edited file shouldn't be able to put the app in a state the UI can't reach. */
    private fun sanitize(s: LauncherSettings): LauncherSettings = s.migrated().copy(
        theme = s.theme.copy(
            textScale = s.theme.textScale.coerceIn(ThemeSettings.MIN_SCALE, ThemeSettings.MAX_SCALE),
            dimming = s.theme.dimming.coerceIn(0f, ThemeSettings.MAX_DIMMING),
            iconScale = s.theme.iconScale.coerceIn(ThemeSettings.MIN_ICON_SCALE, ThemeSettings.MAX_ICON_SCALE),
            accentHue = s.theme.accentHue.coerceIn(0f, 360f),
            rowGap = s.theme.rowGap.coerceIn(ThemeSettings.MIN_ROW_GAP, ThemeSettings.MAX_ROW_GAP),
        ),
        clock = s.clock.sanitized(),
        behaviour = s.behaviour.copy(homeOffset = s.behaviour.homeOffset.coerceIn(0f, BehaviourSettings.MAX_HOME_OFFSET)),
        widgets = s.widgets.mapNotNull { stack ->
            stack.items.filter { it.kind != WidgetRef.Kind.App }.distinct().takeIf { it.isNotEmpty() }
                ?.let { WidgetStack(it, Widgets.clampHeight(stack.heightDp)) }
        },
    )

    /** The restored settings, keeping this install's live app widgets at the end of the list. */
    fun restoreOnto(current: LauncherSettings, restored: LauncherSettings): LauncherSettings {
        val live = current.widgets.mapNotNull { s ->
            s.items.filter { it.kind == WidgetRef.Kind.App }.takeIf { it.isNotEmpty() }?.let { s.copy(items = it) }
        }
        return restored.copy(widgets = restored.widgets + live)
    }
}
