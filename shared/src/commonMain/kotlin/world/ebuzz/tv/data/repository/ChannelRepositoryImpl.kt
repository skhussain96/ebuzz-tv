package world.ebuzz.tv.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import world.ebuzz.tv.data.remote.EbuzzApi
import world.ebuzz.tv.data.remote.arr
import world.ebuzz.tv.data.remote.hasNextPage
import world.ebuzz.tv.data.remote.toChannel
import world.ebuzz.tv.domain.model.Channel
import world.ebuzz.tv.domain.repository.ChannelRepository

/** Channels are fetched once per process in id order, so channel numbers stay stable. */
class ChannelRepositoryImpl(private val api: EbuzzApi) : ChannelRepository {
    private val lock = Mutex()
    private var cache: List<Channel> = emptyList()

    override suspend fun channels(): List<Channel> = lock.withLock {
        cache.ifEmpty {
            val out = ArrayList<Channel>()
            var page = 1
            while (true) {
                val json = api.get("channels?includes=categories&limit=250&order_by=id,asc&page=$page")
                val data = json.arr("data").orEmpty()
                data.forEach { (it as? JsonObject)?.toChannel(out.size + 1)?.let(out::add) }
                if (!json.hasNextPage() || data.isEmpty()) break
                page++
            }
            out.also { cache = it }
        }
    }
}
