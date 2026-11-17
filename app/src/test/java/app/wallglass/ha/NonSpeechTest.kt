package app.wallglass.ha

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NonSpeechTest {

    @Test
    fun `sound annotations are not speech`() {
        listOf("*muzyka*", "*śmiech*", " *śmiech*\n", "[płacz dziecka]", "(muzyka)", "♪ ♪", "*śmiech* *muzyka*", "", null)
            .forEach { assertTrue("$it", NonSpeech.isNonSpeech(it)) }
    }

    @Test
    fun `words around annotations are speech`() {
        listOf("Jaka będzie jutro pogoda?", "*śmiech* Chodź, zarobimy", "OK")
            .forEach { assertFalse(it, NonSpeech.isNonSpeech(it)) }
    }
}
