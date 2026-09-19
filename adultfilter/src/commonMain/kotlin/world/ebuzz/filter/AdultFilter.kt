package world.ebuzz.filter

import kotlin.coroutines.cancellation.CancellationException

class Subject(
    val title: String, val genre: String = "", val description: String = "",
    val rated: String = "", val imdbId: String = "",
)

class Certification(val country: String, val value: String)

// What an external film database (TMDB) knows about a title.
class Evidence(
    val adult: Boolean = false, val genres: List<String> = emptyList(), val keywords: List<String> = emptyList(),
    val overview: String = "", val certifications: List<Certification> = emptyList(),
)

// Remembers final verdicts across app runs so a title is screened (and looked up) once, not on every page load.
interface VerdictStore {
    fun get(key: String): Boolean?
    fun put(key: String, allowed: Boolean)
}

// null = the database has no answer (unknown id, no key, network error).
fun interface EvidenceSource { suspend fun lookup(imdbId: String): Evidence? }

// Permanent, deliberately over-strict; no switch, no allow-list. Each layer can only block, never clear another's block:
//  1. text: genre, title and description term lists
//  2. rating: restricted certificates in the catalogue's own `rated` field
//  3. evidence: adult flag, genres, keywords, overview and per-country certificates from `source`
// With blockUnverified a title the database cannot vouch for is hidden too; otherwise layers 1–2 decide.
class AdultFilter(
    private val source: EvidenceSource? = null, private val blockUnverified: Boolean = false, private val store: VerdictStore? = null,
) {
    private val verdicts = HashMap<String, Boolean>()

    fun allows(title: String, genre: String, description: String): Boolean = TextRules.allows(title, genre, description)

    fun allows(s: Subject): Boolean = allows(s.title, s.genre, s.description) && !RatingRules.restricts(s.rated)

    fun allows(e: Evidence): Boolean = !e.adult &&
        TextRules.allows("", e.genres.joinToString(","), e.overview) && TextRules.allowsKeywords(e.keywords) &&
        e.certifications.none { RatingRules.restricts(it.value, it.country) }

    // Only final verdicts are remembered: a block by any layer, or a pass the database vouched for. "Allowed because the
    // lookup failed / there is no key" is never stored, so it is retried. The key carries RULES_VERSION and a hash of the
    // catalogue text, so editing the rules or the catalogue entry re-screens the title.
    suspend fun screen(s: Subject): Boolean {
        val key = key(s)
        verdicts[key]?.let { return it }
        store?.get(key)?.let { verdicts[key] = it; return it }
        if (!allows(s)) return remember(key, false)
        val source = source ?: return true
        if (!IMDB_ID.matches(s.imdbId)) return !blockUnverified
        val evidence = try { source.lookup(s.imdbId) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            ?: return !blockUnverified   // not cached: retried next time
        return remember(key, allows(evidence))
    }

    private fun remember(key: String, allowed: Boolean): Boolean { verdicts[key] = allowed; store?.put(key, allowed); return allowed }

    private fun key(s: Subject): String {
        var h = 1125899906842597L
        for (part in arrayOf(s.title, s.genre, s.description, s.rated)) { for (c in part) h = 31 * h + c.code; h = 31 * h + 7 }
        return "v$RULES_VERSION:${s.imdbId}:${h.toString(36)}"
    }

    companion object {
        // Bump whenever TextRules, RatingRules or the evidence rules change: every stored verdict is then ignored.
        const val RULES_VERSION = 1
        private val IMDB_ID = Regex("tt\\d{5,10}")
    }
}
