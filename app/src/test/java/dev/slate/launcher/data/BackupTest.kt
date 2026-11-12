package dev.slate.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {
    private val settings = LauncherSettings(
        favourites = listOf("a/.A/0"),
        hidden = setOf("b/.B/0"),
        labelOverrides = mapOf("a/.A/0" to "Mail"),
        popups = mapOf("a/.A/0" to listOf(PopupItem.app("c/.C/0"))),
        widgets = listOf(
            WidgetStack(listOf(WidgetRef.app(7), WidgetRef.calendar), 170),
            WidgetStack(listOf(WidgetRef.app(8)), 120),
        ),
        theme = ThemeSettings(accent = AccentChoice.Moss),
        behaviour = BehaviourSettings(rowSwipeLeft = RowSwipeAction.Hide),
    )

    private fun roundTrip() = (Backup.decode(Backup.encode(settings, "1.0", 5)) as Backup.Decoded.Ok).file

    @Test fun roundTripKeepsEverythingButAppWidgets() {
        val file = roundTrip()
        assertEquals(2, file.appWidgetsLeftOut)
        assertEquals(settings.copy(widgets = listOf(WidgetStack(listOf(WidgetRef.calendar), 170))), file.settings)
    }

    @Test fun restoreKeepsLiveAppWidgets() {
        val current = LauncherSettings(widgets = listOf(WidgetStack(listOf(WidgetRef.app(9), WidgetRef.weather), 200)))
        val out = Backup.restoreOnto(current, roundTrip().settings)
        assertEquals(listOf(listOf("calendar"), listOf("app:9")), out.widgets.map { s -> s.items.map { it.key } })
        assertEquals(listOf("a/.A/0"), out.favourites)
    }

    @Test fun rejectsOtherFiles() {
        assertEquals(Backup.Decoded.NotABackup, Backup.decode("not json"))
        assertEquals(Backup.Decoded.NotABackup, Backup.decode("""{"name":"something else"}"""))
        assertEquals(Backup.Decoded.NotABackup, Backup.decode("""{"format":"other","settings":{}}"""))
        assertEquals(Backup.Decoded.TooNew, Backup.decode("""{"format":"slate-backup","version":99,"settings":{}}"""))
    }

    @Test fun clampsHandEditedValuesAndStripsWidgetIds() {
        val text = """{"format":"slate-backup","version":1,"settings":{
            "theme":{"textScale":9.0,"dimming":-1.0},
            "widgets":[{"items":[{"kind":"app","appWidgetId":3}],"heightDp":100},{"items":[{"kind":"weather"}],"heightDp":99999}]
        }}"""
        val s = (Backup.decode(text) as Backup.Decoded.Ok).file.settings
        assertEquals(ThemeSettings.MAX_SCALE, s.theme.textScale)
        assertEquals(0f, s.theme.dimming)
        assertEquals(1, s.widgets.size)
        assertEquals(Widgets.MAX_HEIGHT_DP, s.widgets.single().heightDp)
        assertTrue(s.widgets.single().items.single().kind == WidgetRef.Kind.Weather)
    }
}

class SettingsMigrationTest {
    @Test fun schemaOneDropsTheOldDefaultDimming() {
        val old = LauncherSettings(schemaVersion = 1, theme = ThemeSettings(dimming = 0.45f), favourites = listOf("a"))
        val new = old.migrated()
        assertEquals(0f, new.theme.dimming)
        assertEquals(LauncherSettings.CURRENT_SCHEMA, new.schemaVersion)
        assertEquals(listOf("a"), new.favourites)
        // Current files keep a dimming the user chose.
        val chosen = LauncherSettings(theme = ThemeSettings(dimming = 0.3f))
        assertEquals(0.3f, chosen.migrated().theme.dimming)
    }

    @Test fun `out of range clock and position are pulled back in`() {
        val settings = LauncherSettings(clock = ClockSettings(size = 9f), behaviour = BehaviourSettings(homeOffset = 5f))
        val decoded = Backup.decode(Backup.encode(settings, "1.0", 5)) as Backup.Decoded.Ok
        assertEquals(ClockSettings.MAX_SIZE, decoded.file.settings.clock.size, 0f)
        assertEquals(BehaviourSettings.MAX_HOME_OFFSET, decoded.file.settings.behaviour.homeOffset, 0f)
    }

    @Test fun `schema 2 clocks move onto the bundled font`() {
        val old = LauncherSettings(schemaVersion = 2, clock = ClockSettings(font = FontChoice.Sans, weight = ClockWeight.Light))
        val clock = old.migrated().clock
        assertEquals(null, clock.font)
        assertEquals(ClockWeight.Regular, clock.weight)
        assertEquals(LauncherSettings.CURRENT_SCHEMA, old.migrated().schemaVersion)
    }

    @Test fun `a chosen clock font survives the migration`() {
        assertEquals(FontChoice.Serif, LauncherSettings(schemaVersion = 2, clock = ClockSettings(font = FontChoice.Serif)).migrated().clock.font)
    }
}
