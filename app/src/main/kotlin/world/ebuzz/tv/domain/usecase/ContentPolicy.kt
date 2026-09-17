package world.ebuzz.tv.domain.usecase

import world.ebuzz.tv.domain.model.Movie

/**
 * Permanent content filter — the same rules as the web player. There is deliberately no switch:
 * Romance-tagged titles and adult keywords in title, genre or description are never shown.
 */
class ContentPolicy {
    private val title = Regex("\\b(sex|sexy|sexual|erotic|erotica|nude|naked|porn|porno|softcore|hardcore|stripper|escort|call girls?|adult)\\b", RegexOption.IGNORE_CASE)
    private val titleCaseSensitive = Regex("\\bXXX\\b")
    private val description = Regex("\\b(erotic|erotica|softcore|porn\\w*|sexual encounters?|sex scenes?|nudity|sexploitation|prostitut\\w*|brothel|escorts?|call girls?|strip club|strippers?|orgy|orgies|seduc\\w*|affair|adultery|mistress|threesome|swingers?|nymphomaniac|sexual|sex|sensual|seductive|kinky|fetish|bondage)\\b", RegexOption.IGNORE_CASE)
    private val romance = Regex("\\bromance\\b", RegexOption.IGNORE_CASE)

    fun allows(m: Movie): Boolean = !(
        romance.containsMatchIn(m.genre) || title.containsMatchIn("${m.title} ${m.genre}") ||
            titleCaseSensitive.containsMatchIn(m.title) || description.containsMatchIn(m.description)
        )
}
