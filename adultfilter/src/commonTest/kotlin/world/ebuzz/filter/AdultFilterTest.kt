package world.ebuzz.filter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdultFilterTest {
    private val filter = AdultFilter()
    private fun rated(r: String) = filter.allows(Subject("Plain", "Action", "", r))

    @Test fun restrictedRatingsBlock() =
        listOf("R", "NC-17", "X", "TV-MA", "18+", "18", "A", "Unrated", "UNRATED", "R18+", "MA15+", "21+").forEach { assertFalse(rated(it), it) }

    @Test fun ordinaryRatingsPass() =
        listOf("", "N/A", "G", "PG", "PG-13", "TV-14", "TV-PG", "TV-Y7", "13+", "16+", "U", "UA", "U/A 16+", "Not Rated", "NR", "Approved", "Passed")
            .forEach { assertTrue(rated(it), it) }

    @Test fun ratingNeverClearsText() = assertFalse(filter.allows(Subject("Lust Stories", "Drama", "", "G")))

    @Test fun lettersAreReadPerCountry() {
        assertFalse(filter.allows(Evidence(certifications = listOf(Certification("IN", "A")))))
        assertTrue(filter.allows(Evidence(certifications = listOf(Certification("MX", "A"), Certification("AU", "M"), Certification("FR", "12")))))
        assertFalse(filter.allows(Evidence(certifications = listOf(Certification("US", "PG-13"), Certification("GB", "18")))))
        assertFalse(filter.allows(Evidence(certifications = listOf(Certification("AU", "R 18+")))))
        assertFalse(filter.allows(Evidence(certifications = listOf(Certification("HK", "III")))))
    }

    @Test fun evidenceBlocks() {
        assertFalse(filter.allows(Evidence(adult = true)))
        assertFalse(filter.allows(Evidence(genres = listOf("Drama", "Romance"))))
        assertFalse(filter.allows(Evidence(keywords = listOf("heist", "female nudity"))))
        assertFalse(filter.allows(Evidence(keywords = listOf("pink film"))))
        assertFalse(filter.allows(Evidence(overview = "Two strangers begin a passionate affair.")))
        assertTrue(filter.allows(Evidence(genres = listOf("Family"), keywords = listOf("dog", "friendship"), overview = "A boy and his dog.")))
    }

    private class MemoryStore : VerdictStore { val map = HashMap<String, Boolean>(); override fun get(key: String) = map[key]; override fun put(key: String, allowed: Boolean) { map[key] = allowed } }

    @Test fun storedVerdictSkipsTheLookup() = kotlinx.coroutines.test.runTest {
        var lookups = 0; val store = MemoryStore()
        val source = EvidenceSource { lookups++; Evidence(adult = true) }
        val film = Subject("Plain", "Action", "", "PG", "tt1234567")
        assertFalse(AdultFilter(source, store = store).screen(film))
        assertFalse(AdultFilter(source, store = store).screen(film))          // a new filter = a new app run
        assertEquals(1, lookups)
    }

    @Test fun failedLookupIsNotStored() = kotlinx.coroutines.test.runTest {
        val store = MemoryStore()
        assertTrue(AdultFilter(EvidenceSource { null }, store = store).screen(Subject("Plain", "Action", "", "PG", "tt1234567")))
        assertTrue(store.map.isEmpty())
    }
}
