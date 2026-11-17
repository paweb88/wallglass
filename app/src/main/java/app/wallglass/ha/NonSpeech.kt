package app.wallglass.ha

/**
 * Speech-to-text often turns sounds into annotations instead of words: "*śmiech*", "[muzyka]", "(płacz dziecka)", "♪".
 * Such a transcript means nobody spoke to the panel.
 */
object NonSpeech {

    private val ANNOTATION = Regex("""\*[^*]*\*|\[[^\]]*]|\([^)]*\)|[♪♫]""")

    fun isNonSpeech(text: String?): Boolean {
        val rest = ANNOTATION.replace(text.orEmpty(), " ")
        return rest.none { it.isLetter() }
    }
}
