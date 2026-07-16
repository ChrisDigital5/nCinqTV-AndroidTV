package app.ncinq.tv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.ncinq.tv.data.CatalogPage
import app.ncinq.tv.data.CatalogRepository
import app.ncinq.tv.data.HomeFeed
import app.ncinq.tv.data.LoadState
import app.ncinq.tv.data.MediaDetails
import app.ncinq.tv.data.MediaType
import app.ncinq.tv.data.PlaybackRequest
import app.ncinq.tv.data.SearchResults
import app.ncinq.tv.data.SeasonDetails
import app.ncinq.tv.data.StreamResult
import app.ncinq.tv.data.TrackedItem
import app.ncinq.tv.data.TrackerRepository
import app.ncinq.tv.data.UpdateInfo
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val catalogRepository = CatalogRepository()
    private val trackerRepository = TrackerRepository(application)

    private val _home = MutableStateFlow<LoadState<HomeFeed>>(LoadState.Loading)
    val home: StateFlow<LoadState<HomeFeed>> = _home.asStateFlow()

    private val _catalog = MutableStateFlow<LoadState<CatalogPage>>(LoadState.Loading)
    val catalog: StateFlow<LoadState<CatalogPage>> = _catalog.asStateFlow()

    private val _search = MutableStateFlow<LoadState<SearchResults>>(LoadState.Ready(SearchResults()))
    val search: StateFlow<LoadState<SearchResults>> = _search.asStateFlow()

    private val _details = MutableStateFlow<LoadState<MediaDetails>>(LoadState.Loading)
    val details: StateFlow<LoadState<MediaDetails>> = _details.asStateFlow()

    private val _season = MutableStateFlow<LoadState<SeasonDetails>>(LoadState.Loading)
    val season: StateFlow<LoadState<SeasonDetails>> = _season.asStateFlow()

    private val _activePlayback = MutableStateFlow<PlaybackRequest?>(null)
    val activePlayback: StateFlow<PlaybackRequest?> = _activePlayback.asStateFlow()

    private val _update = MutableStateFlow<UpdateInfo?>(null)
    val update: StateFlow<UpdateInfo?> = _update.asStateFlow()

    val trackedItems = trackerRepository.items

    private val streamCache = ConcurrentHashMap<String, StreamResult>()
    private val pendingStreams = mutableMapOf<String, Deferred<StreamResult>>()
    private val pendingMutex = Mutex()

    init {
        loadHome()
        checkForUpdate()
    }

    fun loadHome(force: Boolean = false) {
        if (!force && _home.value is LoadState.Ready) return
        viewModelScope.launch {
            _home.value = LoadState.Loading
            _home.value = runLoad { catalogRepository.home() }
        }
    }

    fun loadCatalog(type: MediaType, category: String = "popular") {
        viewModelScope.launch {
            _catalog.value = LoadState.Loading
            _catalog.value = runLoad { catalogRepository.catalog(type, category) }
        }
    }

    fun search(query: String) {
        val normalized = query.trim()
        if (normalized.length < 2) {
            _search.value = LoadState.Ready(SearchResults())
            return
        }
        viewModelScope.launch {
            _search.value = LoadState.Loading
            _search.value = runLoad { catalogRepository.search(normalized) }
        }
    }

    fun loadDetails(type: MediaType, id: Int) {
        viewModelScope.launch {
            _details.value = LoadState.Loading
            _season.value = LoadState.Loading
            val loaded = runLoad { catalogRepository.details(type, id) }
            _details.value = loaded
            val details = (loaded as? LoadState.Ready)?.value
            if (type == MediaType.TV && details != null) {
                val firstSeason = details.seasons.firstOrNull()?.number ?: 1
                loadSeason(id, firstSeason)
            }
        }
    }

    fun loadSeason(showId: Int, seasonNumber: Int) {
        viewModelScope.launch {
            _season.value = LoadState.Loading
            _season.value = runLoad { catalogRepository.season(showId, seasonNumber) }
        }
    }

    fun playMovie(details: MediaDetails) {
        _activePlayback.value = PlaybackRequest(
            mediaId = details.id,
            mediaType = MediaType.MOVIE,
            title = details.title,
            posterUrl = details.posterUrl,
            backdropUrl = details.backdropUrl,
            imdbId = details.imdbId,
            releaseYear = details.year,
        )
    }

    fun playEpisode(details: MediaDetails, season: SeasonDetails, episodeNumber: Int) {
        val episode = season.episodes.firstOrNull { it.number == episodeNumber } ?: return
        _activePlayback.value = PlaybackRequest(
            mediaId = details.id,
            mediaType = MediaType.TV,
            title = details.title,
            posterUrl = details.posterUrl,
            backdropUrl = episode.stillUrl ?: details.backdropUrl,
            imdbId = details.imdbId,
            releaseYear = details.year,
            season = season.seasonNumber,
            episode = episode.number,
            episodeTitle = episode.name,
            episodeCount = season.episodes.size,
            seasonCount = details.seasonCount,
        )
    }

    fun resume(item: TrackedItem) {
        _activePlayback.value = PlaybackRequest(
            mediaId = item.mediaId,
            mediaType = item.mediaType,
            title = item.title,
            posterUrl = item.posterUrl,
            backdropUrl = item.backdropUrl,
            season = item.season,
            episode = item.episode,
            episodeTitle = item.episodeTitle,
        )
    }

    fun resumePosition(request: PlaybackRequest): Long {
        return trackedItems.value
            .firstOrNull { it.mediaId == request.mediaId && it.mediaType == request.mediaType }
            ?.positionMs
            ?: 0L
    }

    suspend fun resolveStream(request: PlaybackRequest, force: Boolean = false): StreamResult {
        if (force) streamCache.remove(request.key)
        streamCache[request.key]?.let { return it }

        val pending = pendingMutex.withLock {
            pendingStreams[request.key] ?: viewModelScope.async(Dispatchers.IO) {
                catalogRepository.resolve(request).also { streamCache[request.key] = it }
            }.also { pendingStreams[request.key] = it }
        }

        return try {
            pending.await()
        } finally {
            pendingMutex.withLock { pendingStreams.remove(request.key, pending) }
        }
    }

    suspend fun nextPlayback(current: PlaybackRequest): PlaybackRequest? {
        if (current.mediaType != MediaType.TV) return null
        val currentSeasonNumber = current.season ?: 1
        val currentEpisodeNumber = current.episode ?: 1
        val details = runCatching {
            catalogRepository.details(MediaType.TV, current.mediaId)
        }.getOrNull()
        val seasonCount = current.seasonCount.takeIf { it > 0 } ?: details?.seasonCount ?: currentSeasonNumber
        val imdbId = current.imdbId ?: details?.imdbId
        val releaseYear = current.releaseYear ?: details?.year

        for (seasonNumber in currentSeasonNumber..seasonCount) {
            val season = runCatching {
                catalogRepository.season(current.mediaId, seasonNumber)
            }.getOrNull() ?: continue
            val nextEpisode = season.episodes.firstOrNull { episode ->
                seasonNumber > currentSeasonNumber || episode.number > currentEpisodeNumber
            } ?: continue
            return current.copy(
                imdbId = imdbId,
                releaseYear = releaseYear,
                season = seasonNumber,
                episode = nextEpisode.number,
                episodeTitle = nextEpisode.name,
                backdropUrl = nextEpisode.stillUrl ?: current.backdropUrl,
                episodeCount = season.episodes.size,
                seasonCount = seasonCount,
            )
        }
        return null
    }

    fun prefetchNext(current: PlaybackRequest) {
        viewModelScope.launch {
            val next = nextPlayback(current) ?: return@launch
            runCatching { resolveStream(next) }
        }
    }

    fun advanceTo(next: PlaybackRequest) {
        _activePlayback.value = next
    }

    fun saveProgress(request: PlaybackRequest, positionMs: Long, durationMs: Long, completed: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            trackerRepository.saveProgress(request, positionMs, durationMs, completed)
        }
    }

    fun toggleTracked(details: MediaDetails) {
        viewModelScope.launch(Dispatchers.IO) { trackerRepository.togglePlanned(details) }
    }

    fun removeTracked(item: TrackedItem) {
        viewModelScope.launch(Dispatchers.IO) { trackerRepository.remove(item) }
    }

    fun dismissUpdate() {
        _update.value = null
    }

    private fun checkForUpdate() {
        viewModelScope.launch(Dispatchers.IO) {
            val info = runCatching { catalogRepository.update(BuildConfig.VERSION_CODE) }.getOrNull()
            if (info?.available == true) _update.value = info
        }
    }

    private suspend fun <T> runLoad(block: suspend () -> T): LoadState<T> {
        return try {
            LoadState.Ready(block())
        } catch (error: Throwable) {
            LoadState.Failed(error.message ?: "Something went wrong. Please try again.")
        }
    }
}
