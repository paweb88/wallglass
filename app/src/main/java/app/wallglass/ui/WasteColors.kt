package app.wallglass.ui

import androidx.compose.ui.graphics.Color

/** Standard Polish/EU waste fraction colours, matched by keywords in the fraction name. */
object WasteColors {
    private val RULES = listOf(
        listOf("papier", "paper", "karton") to Color(0xFF2F7DE1),
        listOf("plast", "metal", "tworzyw", "plastic", "packaging") to Color(0xFFF5C518),
        listOf("szkł", "szkl", "glass") to Color(0xFF2E9D4F),
        listOf("bio", "organic", "kompost") to Color(0xFF8B5A2B),
        listOf("zmiesz", "resztk", "mixed", "residual") to Color(0xFF6B6F76),
        listOf("gabaryt", "bulky") to Color(0xFF9C6ADE),
    )
    val fallback = Color(0xFFB0B4BA)

    fun colorFor(fraction: String): Color {
        val name = fraction.lowercase()
        return RULES.firstOrNull { (keys, _) -> keys.any { name.contains(it) } }?.second ?: fallback
    }

    /** "Bio, Papier" → colours of each fraction in order. */
    fun colorsFor(state: String): List<Color> =
        state.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map(::colorFor)
}
