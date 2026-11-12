package dev.slate.launcher.ui.theme

import dev.slate.launcher.data.PanelTone
import dev.slate.launcher.data.AccentChoice
import dev.slate.launcher.data.IconStyle
import dev.slate.launcher.data.Presets
import dev.slate.launcher.data.ThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentTest {
    private fun lightness(argb: Int) = Accent.toHsl(argb).third

    @Test fun readableOnDarkLiftsDarkColoursAndKeepsHue() {
        val navy = 0xFF0A1A40.toInt()
        val out = Accent.readableOnDark(navy)
        assertTrue(lightness(out) >= 0.6f)
        assertEquals(Accent.toHsl(navy).first, Accent.toHsl(out).first, 3f)
        // Greys stay grey.
        assertTrue(Accent.toHsl(Accent.readableOnDark(0xFF333333.toInt())).second < 0.05f)
    }

    @Test fun deepIsDarkEnoughForLightSurfaces() {
        assertTrue(lightness(Accent.deep(Accent.EMBER)) <= 0.4f)
        assertTrue(Accent.swatch(AccentChoice.Wallpaper) == null)
    }

    @Test fun panelMatchesNiagaraForAVioletAccent() {
        // Niagara's violet theme: card #160E27, raised tile #302844.
        val violet = 0xFF7C5CD6.toInt()
        assertEquals(0.104f, lightness(Accent.panel(violet)), 0.01f)
        assertEquals(0.212f, lightness(Accent.panelRaised(violet)), 0.01f)
        assertEquals(Accent.toHsl(violet).first, Accent.toHsl(Accent.panel(violet)).first, 4f)
        assertTrue(lightness(Accent.onPanel(violet)) > 0.9f)
        // A grey accent gives a grey panel.
        assertTrue(Accent.toHsl(Accent.panel(0xFF888888.toInt())).second < 0.05f)
    }

    @Test fun panelTonesAndCustomHue() {
        val violet = 0xFF7C5CD6.toInt()
        assertEquals(0xFF000000.toInt(), Accent.panel(violet, PanelTone.Black))
        assertTrue(lightness(Accent.panel(violet, PanelTone.Light)) > 0.9f)
        // Text flips with the surface.
        assertTrue(lightness(Accent.onPanel(violet, PanelTone.Light)) < 0.2f)
        assertTrue(lightness(Accent.onPanel(violet, PanelTone.Black)) > 0.9f)
        assertEquals(200f, Accent.toHsl(Accent.resolve(AccentChoice.Custom, null, 200f)).first, 3f)
        assertEquals(Accent.custom(10f), Accent.custom(370f))
    }

    @Test fun presetsKeepTheChosenIconPack() {
        val current = ThemeSettings(iconStyle = IconStyle.Pack, iconPack = "com.pack")
        val paper = Presets.apply(current, "paper")
        assertEquals("paper", paper.preset)
        assertEquals(IconStyle.Pack, paper.iconStyle)
        assertEquals("com.pack", paper.iconPack)
        // A preset with its own icon style wins, but the pack stays remembered.
        val mono = Presets.apply(current, "mono")
        assertEquals(IconStyle.Themed, mono.iconStyle)
        assertEquals("com.pack", mono.iconPack)
        assertEquals(Presets.CUSTOM, Presets.edited(paper).preset)
    }
}
