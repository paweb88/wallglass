package app.wallglass.ha

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationEndTest {

    @Test
    fun `short closing phrases end the conversation`() {
        listOf("OK", "Ok.", "okej", "Dobra!", "Dzięki", "Dziękuję bardzo.", "To wszystko", "Koniec",
            "Nic", "Nieważne", "Stop", "Wystarczy", "Już nic, dzięki", "Dobrze, dziękuję", "Zamknij")
            .forEach { assertTrue(it, ConversationEnd.isClosing(it)) }
    }

    @Test
    fun `real requests continue the conversation`() {
        listOf("Ok, włącz światło w salonie", "Dobra, jaka jest pogoda jutro?", "Jakie mam zadania?",
            "Dziękuję, a ile jest stopni w garażu?", "", "Nic nie gra w salonie, włącz muzykę")
            .forEach { assertFalse(it, ConversationEnd.isClosing(it)) }
    }
}
