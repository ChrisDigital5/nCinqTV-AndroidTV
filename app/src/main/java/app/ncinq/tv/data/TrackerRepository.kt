package app.ncinq.tv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.trackerDataStore by preferencesDataStore(name = "tracker")

class TrackerRepository(private val context: Context) {
    private val gson = Gson()
    private val itemsKey = stringPreferencesKey("tracked_items_v1")
    private val itemListType = object : TypeToken<List<TrackedItem>>() {}.type
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val items = context.trackerDataStore.data
        .map { preferences ->
            runCatching {
                gson.fromJson<List<TrackedItem>>(preferences[itemsKey] ?: "[]", itemListType)
            }.getOrDefault(emptyList())
                .sortedByDescending { it.updatedAt }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun toggleFavorite(details: MediaDetails) {
        mutate { current ->
            val existing = current.firstOrNull { it.mediaId == details.id && it.mediaType.wireName == details.type }
            if (existing != null) {
                val favorite = !existing.isFavorite
                if (!favorite && existing.status == TrackingStatus.PLANNED) {
                    current.filterNot { it.mediaId == details.id && it.mediaType.wireName == details.type }
                } else {
                    current.map {
                        if (it.mediaId == details.id && it.mediaType.wireName == details.type) it.copy(favorite = favorite) else it
                    }
                }
            } else {
                current + TrackedItem(
                    mediaId = details.id,
                    mediaType = MediaType.fromWire(details.type),
                    title = details.title,
                    posterUrl = details.posterUrl,
                    backdropUrl = details.backdropUrl,
                    favorite = true,
                )
            }
        }
    }

    suspend fun saveProgress(
        request: PlaybackRequest,
        positionMs: Long,
        durationMs: Long,
        completed: Boolean,
    ) {
        mutate { current ->
            val existing = current.firstOrNull {
                it.mediaId == request.mediaId && it.mediaType == request.mediaType
            }
            val status = when {
                completed && request.mediaType == MediaType.MOVIE -> TrackingStatus.COMPLETED
                else -> TrackingStatus.WATCHING
            }
            val watchedEpisodes = existing?.watchedEpisodes.orEmpty().toMutableSet()
            if (request.mediaType == MediaType.TV &&
                (completed || (durationMs > 0 && positionMs >= durationMs * 0.9))
            ) {
                watchedEpisodes += "${request.season ?: 1}:${request.episode ?: 1}"
            }
            val updated = TrackedItem(
                mediaId = request.mediaId,
                mediaType = request.mediaType,
                title = request.title,
                posterUrl = request.posterUrl,
                backdropUrl = request.backdropUrl,
                status = status,
                season = request.season,
                episode = request.episode,
                episodeTitle = request.episodeTitle,
                expectedRuntimeMinutes = request.expectedRuntimeMinutes,
                watchedEpisodes = watchedEpisodes.toList(),
                favorite = existing?.isFavorite ?: false,
                positionMs = if (completed) 0 else positionMs.coerceAtLeast(0),
                durationMs = durationMs.coerceAtLeast(0),
                updatedAt = System.currentTimeMillis(),
            )
            current.filterNot { it.mediaId == request.mediaId && it.mediaType == request.mediaType } + updated
        }
    }

    suspend fun remove(item: TrackedItem) {
        mutate { current ->
            current.filterNot { it.mediaId == item.mediaId && it.mediaType == item.mediaType }
        }
    }

    suspend fun removeFromHistory(item: TrackedItem) {
        mutate { current ->
            current.mapNotNull { existing ->
                if (existing.mediaId != item.mediaId || existing.mediaType != item.mediaType) return@mapNotNull existing
                if (existing.isFavorite) {
                    existing.copy(
                        status = TrackingStatus.PLANNED,
                        season = null,
                        episode = null,
                        episodeTitle = null,
                        watchedEpisodes = emptyList(),
                        positionMs = 0,
                        durationMs = 0,
                        favorite = true,
                    )
                } else null
            }
        }
    }

    suspend fun removeEpisodeFromHistory(item: TrackedItem, season: Int, episode: Int) {
        mutate { current ->
            current.mapNotNull { existing ->
                if (existing.mediaId != item.mediaId || existing.mediaType != item.mediaType) return@mapNotNull existing
                val key = "$season:$episode"
                val remaining = existing.watchedEpisodes.orEmpty().filterNot { it == key }
                val removingCurrent = existing.season == season && existing.episode == episode
                if (!removingCurrent) return@mapNotNull existing.copy(watchedEpisodes = remaining)

                val latest = remaining.mapNotNull { value ->
                    val parts = value.split(':')
                    val seasonNumber = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                    val episodeNumber = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                    seasonNumber to episodeNumber
                }.maxWithOrNull(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })

                when {
                    latest != null -> existing.copy(
                        season = latest.first,
                        episode = latest.second,
                        episodeTitle = null,
                        watchedEpisodes = remaining,
                        positionMs = 0,
                        durationMs = 0,
                    )
                    existing.isFavorite -> existing.copy(
                        status = TrackingStatus.PLANNED,
                        season = null,
                        episode = null,
                        episodeTitle = null,
                        watchedEpisodes = emptyList(),
                        positionMs = 0,
                        durationMs = 0,
                        favorite = true,
                    )
                    else -> null
                }
            }
        }
    }

    private suspend fun mutate(transform: (List<TrackedItem>) -> List<TrackedItem>) {
        context.trackerDataStore.edit { preferences ->
            val current = runCatching {
                gson.fromJson<List<TrackedItem>>(preferences[itemsKey] ?: "[]", itemListType)
            }.getOrDefault(emptyList())
            preferences[itemsKey] = gson.toJson(transform(current))
        }
    }
}
