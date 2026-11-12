package dev.slate.launcher.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class AlphabetIndexTest {

    @Test
    fun bucketsLatinLettersCaseInsensitively() {
        assertEquals('C', AlphabetIndex.bucketOf("Calendar"))
        assertEquals('C', AlphabetIndex.bucketOf("calendar"))
        assertEquals('M', AlphabetIndex.bucketOf("  Maps"))
    }

    @Test
    fun stripsAccents() {
        assertEquals('E', AlphabetIndex.bucketOf("Été"))
        assertEquals('O', AlphabetIndex.bucketOf("Ökotest"))
    }

    @Test
    fun digitsSymbolsAndOtherScriptsGoToOther() {
        assertEquals('#', AlphabetIndex.bucketOf("1Password"))
        assertEquals('#', AlphabetIndex.bucketOf("@home"))
        assertEquals('#', AlphabetIndex.bucketOf("微信"))
        assertEquals('#', AlphabetIndex.bucketOf("😀 Emoji"))
        assertEquals('#', AlphabetIndex.bucketOf(""))
    }

    @Test
    fun sortsLocaleAwareWithOtherBucketLast() {
        val labels = listOf("zoom", "1Password", "Éclair", "apple", "Banana", "微信", "eBay")
        val sorted = AlphabetIndex.sort(labels, AlphabetIndex.collatorFor(Locale.ENGLISH)) { it }
        assertEquals(listOf("apple", "Banana", "eBay", "Éclair", "zoom"), sorted.take(5))
        assertEquals(setOf("1Password", "微信"), sorted.drop(5).toSet())
    }

    @Test
    fun firstIndexByBucket() {
        val index = AlphabetIndex.firstIndexByBucket(listOf('A', 'A', 'C', 'F', 'F', '#'))
        assertEquals(mapOf('A' to 0, 'C' to 2, 'F' to 3, '#' to 5), index)
    }

    @Test
    fun resolvePrefersExactThenNextThenPrevious() {
        val index = mapOf('B' to 0, 'D' to 4, 'M' to 9)
        assertEquals(4, AlphabetIndex.resolve('D', index))
        assertEquals(4, AlphabetIndex.resolve('C', index))
        assertEquals(0, AlphabetIndex.resolve('A', index))
        assertEquals(9, AlphabetIndex.resolve('Z', index))
        assertEquals(9, AlphabetIndex.resolve('#', index))
        assertNull(AlphabetIndex.resolve('A', emptyMap()))
    }

    @Test fun `sections list only what is there, icons around the letters`() {
        val sections = AlphabetIndex.sections(setOf('C', 'A', AlphabetIndex.OTHER), favourites = true, privateSpace = true)
        assertEquals(
            listOf(
                AlphabetSection.Favourites,
                AlphabetSection.Letter('A'),
                AlphabetSection.Letter('C'),
                AlphabetSection.Letter(AlphabetIndex.OTHER),
                AlphabetSection.PrivateSpace,
            ),
            sections,
        )
    }

    @Test fun `no favourites or private space means no icons`() {
        assertEquals(listOf(AlphabetSection.Letter('B')), AlphabetIndex.sections(setOf('B'), favourites = false, privateSpace = false))
    }
}
