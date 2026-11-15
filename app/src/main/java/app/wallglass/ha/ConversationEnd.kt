package app.wallglass.ha

/** Recognises short replies that mean "that's all" (e.g. "OK", "dzięki", "to wszystko"). */
object ConversationEnd {

    private val PHRASES = setOf(
        "ok", "okej", "okay", "dobra", "dobrze", "w porządku", "super", "spoko",
        "dzięki", "dziękuję", "dziękuje", "dziekuje", "dzięki bardzo", "dziękuję bardzo", "dziękuję ci",
        "to wszystko", "to już wszystko", "wszystko", "koniec", "nic", "już nic", "nic więcej",
        "nieważne", "stop", "wystarczy", "na razie", "pa", "zamknij", "anuluj", "nie", "nie dzięki",
    )

    /** Longest closing reply worth checking; anything longer is treated as a real request. */
    private const val MAX_WORDS = 4

    fun isClosing(text: String?): Boolean {
        val words = text.orEmpty().lowercase()
            .replace(Regex("[^\\p{L}\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (words.isEmpty() || words.size > MAX_WORDS) return false
        // "dobrze, dziękuję" / "już nic, dzięki": every part must be a closing phrase on its own
        return splits(words).any { parts -> parts.all { it.joinToString(" ") in PHRASES } }
    }

    /** All ways to cut the words into consecutive groups (at most 2^(MAX_WORDS-1) of them). */
    private fun splits(words: List<String>): List<List<List<String>>> {
        if (words.size <= 1) return listOf(listOf(words))
        return (1..words.size).flatMap { cut ->
            val head = words.take(cut)
            val rest = words.drop(cut)
            if (rest.isEmpty()) listOf(listOf(head)) else splits(rest).map { listOf(head) + it }
        }
    }
}
