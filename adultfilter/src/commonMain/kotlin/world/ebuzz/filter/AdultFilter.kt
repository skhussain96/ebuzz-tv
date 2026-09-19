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

// null = the database has no answer (unknown id, no key, network error).
fun interface EvidenceSource { suspend fun lookup(imdbId: String): Evidence? }

// Permanent, deliberately over-strict; no switch, no allow-list. Each layer can only block, never clear another's block:
//  1. text: genre, title and description term lists
//  2. rating: restricted certificates in the catalogue's own `rated` field
//  3. evidence: adult flag, genres, keywords, overview and per-country certificates from `source`
// With blockUnverified a title the database cannot vouch for is hidden too; otherwise layers 1–2 decide.
class AdultFilter(private val source: EvidenceSource? = null, private val blockUnverified: Boolean = false) {
    private val verdicts = HashMap<String, Boolean>()

    fun allows(title: String, genre: String, description: String): Boolean = TextRules.allows(title, genre, description)

    fun allows(s: Subject): Boolean = allows(s.title, s.genre, s.description) && !RatingRules.restricts(s.rated)

    fun allows(e: Evidence): Boolean = !e.adult &&
        TextRules.allows("", e.genres.joinToString(","), e.overview) && TextRules.allowsKeywords(e.keywords) &&
        e.certifications.none { RatingRules.restricts(it.value, it.country) }

    suspend fun screen(s: Subject): Boolean {
        if (!allows(s)) return false
        val source = source ?: return true
        if (!IMDB_ID.matches(s.imdbId)) return !blockUnverified
        verdicts[s.imdbId]?.let { return it }
        val evidence = try { source.lookup(s.imdbId) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            ?: return !blockUnverified   // not cached: retried next time
        return allows(evidence).also { verdicts[s.imdbId] = it }
    }

    private companion object { val IMDB_ID = Regex("tt\\d{5,10}") }
}
