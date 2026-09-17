package world.ebuzz.tv.domain.usecase

import world.ebuzz.tv.domain.model.Album
import world.ebuzz.tv.domain.model.Channel
import world.ebuzz.tv.domain.model.Movie

/**
 * Permanent, deliberately over-strict content filter shared by every client. There is no switch and no
 * allow-list: when in doubt a title is hidden. False positives are accepted; false negatives are not.
 *
 * A title is blocked when
 *  - its genre is Romance / Erotic / Adult, or
 *  - its title or genre contains a [TITLE_TERMS] word, or
 *  - its description contains a [TITLE_TERMS] or [DESCRIPTION_TERMS] word or phrase.
 */
class ContentPolicy {
    private val genres = wordRegex(BLOCKED_GENRES)
    private val title = wordRegex(TITLE_TERMS)
    private val description = wordRegex(TITLE_TERMS + DESCRIPTION_TERMS)

    fun allows(m: Movie): Boolean = allows(m.title, m.genre, m.description)

    fun allows(c: Channel): Boolean = allows(c.title, "", "")

    /** Albums have no genre; the album title, its description and every track name are checked. */
    fun allows(a: Album): Boolean = allows(a.title, "", a.description) && a.tracks.none { !allows(it.name, "", "") }

    fun allows(title: String, genre: String, description: String): Boolean = !(
        genres.containsMatchIn(genre) || this.title.containsMatchIn("$title $genre") ||
            this.description.containsMatchIn(description)
        )

    companion object {
        /** Entries are regex fragments; `\w*` catches inflections (seduce, seduction, seductive…). */
        private fun wordRegex(terms: List<String>) = Regex("\\b(?:${terms.joinToString("|")})\\b", RegexOption.IGNORE_CASE)

        val BLOCKED_GENRES = listOf("romance", "romantic", "erotic\\w*", "adult")

        val TITLE_TERMS = listOf(
            // explicit
            "sex", "sexy", "sexual\\w*", "sexuality", "sexting", "erotic\\w*", "porn\\w*", "xxx", "x-rated", "18\\+", "nsfw",
            "softcore", "hardcore", "nud(?:e|es|ity|ist\\w*)", "naked", "topless", "striptease", "strippers?", "strip club",
            "lap dance\\w*", "pole danc\\w*", "playboy", "penthouse", "hustler", "kama ?sutra", "bdsm", "bondage", "fetish\\w*",
            "kinky?", "orgy", "orgies", "orgasm\\w*", "threesome", "foursome", "swingers?", "nympho\\w*", "voyeur\\w*", "peeping",
            "masturbat\\w*", "aphrodisiac", "libido", "arous\\w*", "horny", "lewd", "raunchy", "smut\\w*", "obscen\\w*",
            // sex work
            "prostitut\\w*", "hookers?", "escorts?", "call ?girls?", "brothels?", "gigolos?", "pimps?", "courtesans?", "concubines?",
            "harem", "sugar dadd(?:y|ies)", "sugar bab(?:y|ies)", "red[- ]light",
            // seduction / infidelity
            "seduc\\w*", "lust\\w*", "temptress", "temptation", "mistress\\w*", "adulter\\w*", "infidelity", "unfaithful",
            "extramarital", "affairs?", "one[- ]night stand", "hook ?ups?", "fling", "cheating (?:wife|husband|spouse)",
            // garments / framing used by exploitation titles
            "lingerie", "bikini", "hot girls?", "bad girls?", "naughty", "sultry", "steamy", "sensual\\w*", "intimate", "intimacy",
            "adults? only", "adult", "uncensored", "unrated",
            // sexual violence and abuse
            "rap(?:e|es|ed|ing|ist|ists)", "molest\\w*", "incest\\w*", "pa?edophil\\w*", "sexploitation", "sex traffick\\w*",
        )

        val DESCRIPTION_TERMS = listOf(
            "sex scenes?", "love scenes?", "sexual encounters?", "make love", "making love", "made love", "sleeps? with",
            "sleeping with", "slept with", "in bed with", "one night together", "passionate (?:affair|night|encounter|romance|relationship)",
            "forbidden (?:love|passion|desire|romance)", "secret lovers?", "(?:her|his|a|the|new|young|older|former|ex|married|secret) lovers?",
            "lovers", "love affair", "love triangle", "illicit", "carnal", "desires?", "virginity", "virgins?", "promiscu\\w*",
            "provocative", "explicit", "indecent", "scandalous", "honeymoon", "bachelor(?:ette)? party", "wife[- ]swap\\w*",
        )
    }
}
