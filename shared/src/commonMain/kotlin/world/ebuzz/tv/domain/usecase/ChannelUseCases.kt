package world.ebuzz.tv.domain.usecase

import world.ebuzz.tv.domain.model.Channel
import world.ebuzz.tv.domain.repository.ChannelRepository

/** Channels keep their API-order numbers; any the content policy rejects are dropped without renumbering the rest. */
class GetChannels(private val repo: ChannelRepository, private val policy: ContentPolicy = ContentPolicy()) {
    suspend operator fun invoke(): List<Channel> = repo.channels().filter(policy::allows)
}

/** Match by name, or by channel-number prefix so "12" finds 12, 120–129. */
class FilterChannels {
    operator fun invoke(all: List<Channel>, query: String): List<Channel> {
        val q = query.trim()
        return if (q.isEmpty()) all else all.filter { it.title.contains(q, true) || it.number.toString().startsWith(q) }
    }
}

/** Wrap-around stepping through the channel list. */
class StepChannel {
    operator fun invoke(size: Int, index: Int, dir: Int): Int = if (size == 0) 0 else (index + dir + size) % size
}
