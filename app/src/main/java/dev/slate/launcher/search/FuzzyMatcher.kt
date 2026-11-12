package dev.slate.launcher.search

import java.text.Normalizer

/**
 * Scores how well a typed query matches a label. Higher is better; null is no match.
 *
 * Tiers, best first: exact, prefix of the label, prefix of a later word, initials
 * ("gm" → Google Maps), substring, in-order subsequence (penalised per gap), and finally
 * one typo in a word prefix for queries of four or more characters.
 */
object FuzzyMatcher {

    private val marks = Regex("\\p{Mn}+")

    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(marks, "")
            .lowercase()
            .trim()

    fun score(query: String, label: String): Int? = scoreNormalized(normalize(query), normalize(label))

    /** Both arguments already passed through [normalize]. */
    fun scoreNormalized(q: String, t: String): Int? {
        if (q.isEmpty() || t.isEmpty()) return null
        // Shorter labels win ties: "Maps" over "Maps Go" for "maps".
        val lengthPenalty = (t.length - q.length).coerceIn(0, 40)
        if (t == q) return 1000
        if (t.startsWith(q)) return 900 - lengthPenalty
        val words = words(t)
        if (words.drop(1).any { it.startsWith(q) }) return 800 - lengthPenalty
        if (q.length >= 2 && initials(words).startsWith(q)) return 700 - lengthPenalty
        if (q.length >= 2 && t.contains(q)) return 600 - lengthPenalty
        if (q.length >= 3) subsequence(q, t)?.let { gaps -> return (450 - gaps * 25 - lengthPenalty).coerceAtLeast(201) }
        if (q.length >= 4 && words.any { w -> (q.length - 1..q.length + 1).any { n -> n <= w.length && withinOneEdit(q, w.take(n)) } }) {
            return 200 - lengthPenalty
        }
        return null
    }

    private val separators = Regex("[\\s\\-_.:/()&+]+")

    private fun words(t: String): List<String> = t.split(separators).filter { it.isNotEmpty() }

    private fun initials(words: List<String>): String = words.joinToString("") { it.take(1) }

    /** Number of gaps when [q] appears in order inside [t], or null. Prefers matches at word starts. */
    private fun subsequence(q: String, t: String): Int? {
        var gaps = 0
        var ti = 0
        var lastMatch = -1
        for (c in q) {
            val found = t.indexOf(c, ti)
            if (found < 0) return null
            if (lastMatch >= 0 && found != lastMatch + 1) gaps++
            lastMatch = found
            ti = found + 1
        }
        // A first letter that isn't the label's or a word's first letter is a weak match.
        val first = t.indexOf(q[0])
        if (first > 0 && t[first - 1].isLetterOrDigit()) gaps += 2
        return gaps
    }

    /** Damerau–Levenshtein distance ≤ 1 (substitution, insertion, deletion or swap). */
    internal fun withinOneEdit(a: String, b: String): Boolean {
        if (a == b) return true
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        if (a.length == b.length) {
            val diffs = a.indices.filter { a[it] != b[it] }
            return diffs.size == 1 ||
                diffs.size == 2 && diffs[1] == diffs[0] + 1 && a[diffs[0]] == b[diffs[1]] && a[diffs[1]] == b[diffs[0]]
        }
        val (long, short) = if (a.length > b.length) a to b else b to a
        var i = 0
        while (i < short.length && long[i] == short[i]) i++
        return long.substring(i + 1) == short.substring(i)
    }
}
