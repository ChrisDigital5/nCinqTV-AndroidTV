package app.ncinq.tv.data

enum class MediaType(val wireName: String) {
    MOVIE("movie"),
    TV("tv");

    companion object {
        fun fromWire(value: String) = entries.firstOrNull { it.wireName == value } ?: MOVIE
    }
}

data class MediaItem(
    val id: Int = 0,
    val type: String = "movie",
    val title: String = "",
    val overview: String = "",
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val rating: Double = 0.0,
    val year: Int? = null,
)

data class MediaRow(
    val title: String = "",
    val items: List<MediaItem> = emptyList(),
)

data class HomeFeed(
    val featured: MediaItem? = null,
    val rows: List<MediaRow> = emptyList(),
)

data class CatalogPage(
    val page: Int = 1,
    val totalPages: Int = 1,
    val items: List<MediaItem> = emptyList(),
)

data class SearchResults(
    val items: List<MediaItem> = emptyList(),
)

data class SeasonSummary(
    val number: Int = 0,
    val name: String = "",
    val episodeCount: Int = 0,
    val airDate: String? = null,
    val posterUrl: String? = null,
)

data class MediaDetails(
    val id: Int = 0,
    val type: String = "movie",
    val title: String = "",
    val overview: String = "",
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val rating: Double = 0.0,
    val year: Int? = null,
    val imdbId: String? = null,
    val runtimeMinutes: Int? = null,
    val genres: List<String> = emptyList(),
    val seasonCount: Int = 0,
    val seasons: List<SeasonSummary> = emptyList(),
)

data class Episode(
    val number: Int = 0,
    val name: String = "",
    val overview: String = "",
    val runtimeMinutes: Int? = null,
    val airDate: String? = null,
    val stillUrl: String? = null,
)

data class SeasonDetails(
    val showId: Int = 0,
    val seasonNumber: Int = 1,
    val name: String = "",
    val episodes: List<Episode> = emptyList(),
)

data class Caption(
    val id: String = "",
    val url: String = "",
    val language: String = "Unknown",
    val type: String = "vtt",
)

data class StreamResult(
    val url: String = "",
    val type: String = "hls",
    val quality: String? = null,
    val captions: List<Caption> = emptyList(),
    val provider: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val proxyToken: String? = null,
)

data class StreamResponse(
    val success: Boolean = false,
    val stream: StreamResult? = null,
    val error: String? = null,
    val fallback: Boolean = false,
)

data class StreamRequest(
    val tmdbId: Int,
    val type: String,
    val title: String,
    val imdbId: String? = null,
    val releaseYear: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
)

data class PlaybackRequest(
    val mediaId: Int,
    val mediaType: MediaType,
    val title: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val imdbId: String? = null,
    val releaseYear: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null,
    val episodeCount: Int = 0,
    val seasonCount: Int = 0,
) {
    val key: String
        get() = "${mediaType.wireName}:$mediaId:${season ?: 0}:${episode ?: 0}"

    fun toStreamRequest() = StreamRequest(
        tmdbId = mediaId,
        type = mediaType.wireName,
        title = title,
        imdbId = imdbId,
        releaseYear = releaseYear,
        season = season,
        episode = episode,
    )
}

enum class TrackingStatus {
    PLANNED,
    WATCHING,
    COMPLETED,
}

data class TrackedItem(
    val mediaId: Int,
    val mediaType: MediaType,
    val title: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val status: TrackingStatus = TrackingStatus.PLANNED,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

data class UpdateInfo(
    val available: Boolean = false,
    val versionCode: Int = 0,
    val versionName: String = "",
    val releaseNotes: String = "",
    val publishedAt: String? = null,
    val downloadUrl: String = "",
)

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
    data class Failed(val message: String) : LoadState<Nothing>
}
