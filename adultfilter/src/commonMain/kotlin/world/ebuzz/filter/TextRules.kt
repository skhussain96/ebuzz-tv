package world.ebuzz.filter

// Entries are regex fragments; `\w*` catches inflections (seduce, seduction, seductive…).
internal object TextRules {
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

    // Tags only a keyword database uses; checked on top of the title and description terms.
    val KEYWORD_TERMS = listOf(
        "pink film", "pinku eiga", "roman porno", "skin ?flick", "hentai", "ecchi", "breasts?", "boobs?", "cleavage", "skinny dipping",
        "wet t-?shirt", "panties", "milf", "free love",
        "open (?:marriage|relationship)", "friends with benefits", "menage a trois", "polyamor\\w*", "cuckold\\w*", "exhibitionis\\w*",
        "sadomasochis\\w*", "dominatrix", "sex(?:ual)? (?:addict\\w*|worker|comedy|toy|tape|education)", "teen sex\\w*", "coming of age sex\\w*",
    )

    private val genres = wordRegex(BLOCKED_GENRES)
    private val title = wordRegex(TITLE_TERMS)
    private val description = wordRegex(TITLE_TERMS + DESCRIPTION_TERMS)
    private val keywords = wordRegex(TITLE_TERMS + DESCRIPTION_TERMS + KEYWORD_TERMS)

    fun allows(title: String, genre: String, description: String): Boolean = !(
        genres.containsMatchIn(genre) || this.title.containsMatchIn("$title $genre") || this.description.containsMatchIn(description)
        )

    fun allowsKeywords(tags: List<String>): Boolean = tags.none(keywords::containsMatchIn)
}
