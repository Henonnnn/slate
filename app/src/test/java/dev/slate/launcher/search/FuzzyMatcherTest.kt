package dev.slate.launcher.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatcherTest {
    private fun rank(query: String, vararg labels: String) =
        labels.mapNotNull { l -> FuzzyMatcher.score(query, l)?.let { l to it } }.sortedByDescending { it.second }.map { it.first }

    @Test fun tiersOrderResults() {
        assertEquals(
            listOf("Maps", "Maps Go", "Google Maps", "Mail Apps"),
            rank("maps", "Mail Apps", "Google Maps", "Maps Go", "Maps"),
        )
    }

    @Test fun initialsAndAccents() {
        assertEquals(listOf("Gmail", "Google Maps"), rank("gm", "Google Maps", "Gmail"))
        assertTrue(FuzzyMatcher.score("gm", "Gmail")!! > FuzzyMatcher.score("gm", "Google Maps")!!)
        assertNotNull(FuzzyMatcher.score("cafe", "Café Finder"))
    }

    @Test fun subsequenceAndTypos() {
        assertNotNull(FuzzyMatcher.score("clc", "Calculator"))
        assertNotNull(FuzzyMatcher.score("gogle", "Google"))
        assertNotNull(FuzzyMatcher.score("calednar", "Calendar"))
        assertNull(FuzzyMatcher.score("xyz", "Calculator"))
        assertNull(FuzzyMatcher.score("z", "Calculator"))
    }

    @Test fun editDistance() {
        assertTrue(FuzzyMatcher.withinOneEdit("abcd", "abdc"))
        assertTrue(FuzzyMatcher.withinOneEdit("abc", "abxc"))
        assertTrue(FuzzyMatcher.withinOneEdit("abc", "abd"))
        assertTrue(!FuzzyMatcher.withinOneEdit("abc", "cab"))
    }
}
