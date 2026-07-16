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

    suspend fun togglePlanned(details: MediaDetails) {
        mutate { current ->
            val existing = current.firstOrNull { it.mediaId == details.id && it.mediaType.wireName == details.type }
            if (existing != null) {
                current.filterNot { it.mediaId == details.id && it.mediaType.wireName == details.type }
            } else {
                current + TrackedItem(
                    mediaId = details.id,
                    mediaType = MediaType.fromWire(details.type),
                    title = details.title,
                    posterUrl = details.posterUrl,
                    backdropUrl = details.backdropUrl,
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
            val status = when {
                completed && request.mediaType == MediaType.MOVIE -> TrackingStatus.COMPLETED
                else -> TrackingStatus.WATCHING
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

    private suspend fun mutate(transform: (List<TrackedItem>) -> List<TrackedItem>) {
        context.trackerDataStore.edit { preferences ->
            val current = runCatching {
                gson.fromJson<List<TrackedItem>>(preferences[itemsKey] ?: "[]", itemListType)
            }.getOrDefault(emptyList())
            preferences[itemsKey] = gson.toJson(transform(current))
        }
    }
}
