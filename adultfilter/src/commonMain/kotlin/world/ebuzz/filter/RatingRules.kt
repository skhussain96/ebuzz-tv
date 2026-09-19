package world.ebuzz.filter

internal object RatingRules {
    private val age = Regex("\\d+")

    // Letter certificates mean different things per country (IN "A" = adults, MX "A" = everyone), so they are keyed by country.
    // "" is the upstream `rated` field, which carries no country: MPAA / TV Parental Guidelines plus the Indian "A".
    private val RESTRICTED = mapOf(
        "" to setOf("R", "X", "NC17", "TVMA", "MA", "A", "S", "UNRATED", "UR", "ADULT", "ADULTS"),
        "US" to setOf("R", "X", "NC17", "TVMA"),
        "CA" to setOf("R", "A"),
        "IN" to setOf("A", "S"),
        "AU" to setOf("R", "X"),
        "NZ" to setOf("R"),
        "ES" to setOf("X"),
        "HK" to setOf("III"),
        "MX" to setOf("C", "D"),
        "MO" to setOf("D"),
        "PH" to setOf("R", "X"),
        "KR" to setOf("RESTRICTEDSCREENING"),
    )

    // Any certificate naming an age of 17 or more is restricted wherever it is from: 18, 18+, R18+, K-18, M/18, VM18, NC-17, 19, R21…
    fun restricts(certificate: String, country: String = ""): Boolean {
        val c = certificate.uppercase().filter(Char::isLetterOrDigit)
        if (c.isEmpty()) return false
        if (age.findAll(certificate).any { (it.value.toIntOrNull() ?: 0) in 17..99 }) return true
        return c.trimEnd(Char::isDigit) in RESTRICTED[country.uppercase()].orEmpty()
    }
}
