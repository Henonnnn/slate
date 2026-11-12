package dev.slate.launcher.apps

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** Pure bucketing/sorting logic behind the alphabet scrubber. */
/** One stop on the alphabet: a letter with apps under it, or a part of the list marked by an icon. */
sealed interface AlphabetSection {
    data object Favourites : AlphabetSection
    data object Folders : AlphabetSection
    data class Letter(val char: Char) : AlphabetSection
    data object PrivateSpace : AlphabetSection
}

object AlphabetIndex {
    const val OTHER = '#'
    val LETTERS: List<Char> = ('A'..'Z').toList() + OTHER

    /** Only what's really in the list: letters with no apps don't appear at all. */
    fun sections(present: Set<Char>, favourites: Boolean, privateSpace: Boolean, folders: Boolean = false): List<AlphabetSection> = buildList {
        if (favourites) add(AlphabetSection.Favourites)
        if (folders) add(AlphabetSection.Folders)
        LETTERS.filter { it in present }.forEach { add(AlphabetSection.Letter(it)) }
        if (privateSpace) add(AlphabetSection.PrivateSpace)
    }

    /** First letter with accents stripped, or [OTHER] for digits, symbols and non-Latin scripts. */
    fun bucketOf(label: String): Char {
        val trimmed = label.trimStart()
        if (trimmed.isEmpty()) return OTHER
        val firstCodePoint = String(Character.toChars(trimmed.codePointAt(0)))
        val base = Normalizer.normalize(firstCodePoint, Normalizer.Form.NFD).first().uppercaseChar()
        return if (base in 'A'..'Z') base else OTHER
    }

    /** Locale-aware alphabetical order, with the [OTHER] bucket last to match the scrubber. */
    fun <T> sort(items: List<T>, collator: Collator, label: (T) -> String): List<T> {
        val keyed = items.map { Triple(it, bucketOf(label(it)) == OTHER, collator.getCollationKey(label(it))) }
        return keyed.sortedWith(compareBy<Triple<T, Boolean, java.text.CollationKey>> { it.second }.thenBy { it.third })
            .map { it.first }
    }

    fun collatorFor(locale: Locale): Collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }

    /** Index of the first item in each bucket, given the buckets of an already-sorted list. */
    fun firstIndexByBucket(buckets: List<Char>): Map<Char, Int> {
        val result = LinkedHashMap<Char, Int>()
        buckets.forEachIndexed { i, b -> result.putIfAbsent(b, i) }
        return result
    }

    /**
     * Where a touch on [letter] should land: that bucket if present, otherwise the
     * nearest following bucket, otherwise the nearest preceding one.
     */
    fun resolve(letter: Char, firstIndex: Map<Char, Int>): Int? {
        firstIndex[letter]?.let { return it }
        val pos = LETTERS.indexOf(letter).takeIf { it >= 0 } ?: return null
        for (i in pos + 1 until LETTERS.size) firstIndex[LETTERS[i]]?.let { return it }
        for (i in pos - 1 downTo 0) firstIndex[LETTERS[i]]?.let { return it }
        return null
    }
}
