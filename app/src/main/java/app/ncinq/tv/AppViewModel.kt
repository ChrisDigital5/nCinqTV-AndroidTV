package app.ncinq.tv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.ncinq.tv.data.CatalogPage
import app.ncinq.tv.data.CatalogRepository
import app.ncinq.tv.data.HomeFeed
import app.ncinq.tv.data.LoadState
import app.ncinq.tv.data.MediaDetails
import app.ncinq.tv.data.MediaRow
import app.ncinq.tv.data.MediaType
import app.ncinq.tv.data.PlaybackRequest
import app.ncinq.tv.data.SearchResults
import app.ncinq.tv.data.SeasonDetails
import app.ncinq.tv.data.StreamResult
import app.ncinq.tv.data.TrackedItem
import app.ncinq.tv.data.TrackerRepository
import app.ncinq.tv.data.UpdateInfo
import app.ncinq.tv.data.UpdateInstallState
import app.ncinq.tv.data.ViewerProfile
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val catalogRepository = CatalogRepository()
    private val trackerRepository = TrackerRepository(application)
    private val viewingPreferences = application.getSharedPreferences("viewing_preferences", 0)

    private val _home = MutableStateFlow<LoadState<HomeFeed>>(LoadState.Loading)
    val home: StateFlow<LoadState<HomeFeed>> = _home.asStateFlow()

    private val _catalog = MutableStateFlow<LoadState<CatalogPage>>(LoadState.Loading)
    val catalog: StateFlow<LoadState<CatalogPage>> = _catalog.asStateFlow()

    private val _search = MutableStateFlow<LoadState<SearchResults>>(LoadState.Ready(SearchResults()))
    val search: StateFlow<LoadState<SearchResults>> = _search.asStateFlow()

    private val _catalogLoadingMore = MutableStateFlow(false)
    val catalogLoadingMore: StateFlow<Boolean> = _catalogLoadingMore.asStateFlow()

    private val _searchLoadingMore = MutableStateFlow(false)
    val searchLoadingMore: StateFlow<Boolean> = _searchLoadingMore.asStateFlow()

    private val _details = MutableStateFlow<LoadState<MediaDetails>>(LoadState.Loading)
    val details: StateFlow<LoadState<MediaDetails>> = _details.asStateFlow()

    private val _season = MutableStateFlow<LoadState<SeasonDetails>>(LoadState.Loading)
    val season: StateFlow<LoadState<SeasonDetails>> = _season.asStateFlow()

    private val _activePlayback = MutableStateFlow<PlaybackRequest?>(null)
    val activePlayback: StateFlow<PlaybackRequest?> = _activePlayback.asStateFlow()

    private val _update = MutableStateFlow<UpdateInfo?>(null)
    val update: StateFlow<UpdateInfo?> = _update.asStateFlow()

    private val _updateCheckMessage = MutableStateFlow<String?>(null)
    val updateCheckMessage: StateFlow<String?> = _updateCheckMessage.asStateFlow()

    val currentVersionName: String = BuildConfig.VERSION_NAME

    private val _updateInstallState = MutableStateFlow(UpdateInstallState())
    val updateInstallState: StateFlow<UpdateInstallState> = _updateInstallState.asStateFlow()


    val trackedItems = trackerRepository.items

    fun setActiveProfile(profile: ViewerProfile) {
        activeProfile = profile
        trackerRepository.setActiveProfile(profile.id)
        loadHome(force = true)
    }

    private val streamCache = ConcurrentHashMap<String, StreamResult>()
    private val pendingStreams = mutableMapOf<String, Deferred<StreamResult>>()
    private val pendingMutex = Mutex()
    private var catalogQuery = CatalogQuery(MediaType.MOVIE)
    private var searchQuery = ""
    private var searchType: MediaType? = null
    private var detailsJob: Job? = null
    private var seasonJob: Job? = null
    private var searchJob: Job? = null
    private var catalogJob: Job? = null
    private var activeProfile: ViewerProfile? = null
    val isKidsProfile: Boolean get() = activeProfile?.kidsMode == true

    init {
        loadHome()
        checkForUpdate(manual = false)
    }

    fun loadHome(force: Boolean = false) {
        if (!force && _home.value is LoadState.Ready) return
        viewModelScope.launch {
            _home.value = LoadState.Loading
            _home.value = runLoad {
                val profile = activeProfile ?: return@runLoad catalogRepository.home()
                if (profile.kidsMode) {
                    val movies = catalogRepository.catalog(MediaType.MOVIE, "popular", 1, genre = 10751).items
                    val shows = catalogRepository.catalog(MediaType.TV, "popular", 1, genre = 10762).items
                    HomeFeed(
                        featured = (shows + movies).firstOrNull(),
                        rows = listOf(MediaRow("Kids movies", movies), MediaRow("Kids shows", shows)),
                        networks = emptyList(),
                    )
                } else {
                    val feed = catalogRepository.home()
                    val preferredRows = buildList {
                        profile.moviePreferences.firstOrNull()?.let { preference ->
                            genreId(preference, MediaType.MOVIE)?.let { id ->
                                val items = catalogRepository.catalog(MediaType.MOVIE, "popular", 1, genre = id).items
                                if (items.isNotEmpty()) add(MediaRow("Movies for ${profile.name}: $preference", items))
                            }
                        }
                        profile.showPreferences.firstOrNull()?.let { preference ->
                            genreId(preference, MediaType.TV)?.let { id ->
                                val items = catalogRepository.catalog(MediaType.TV, "popular", 1, genre = id).items
                                if (items.isNotEmpty()) add(MediaRow("Shows for ${profile.name}: $preference", items))
                            }
                        }
                    }
                    feed.copy(rows = preferredRows + feed.rows)
                }
            }
        }
    }

    private fun genreId(name: String, type: MediaType): Int? = when (name) {
        "Action" -> if (type == MediaType.MOVIE) 28 else 10759
        "Comedy" -> 35
        "Drama" -> 18
        "Horror" -> 27
        "Sci-Fi" -> if (type == MediaType.MOVIE) 878 else 10765
        "Animation" -> 16
        "Reality" -> 10764
        "Crime" -> 80
        "Documentary" -> 99
        "Kids" -> 10762
        else -> null
    }

    fun loadCatalog(
        type: MediaType,
        category: String = "popular",
        genre: Int? = null,
        network: Int? = null,
        sort: String? = null,
        year: Int? = null,
    ) {
        val safeGenre = if (activeProfile?.kidsMode == true) {
            if (type == MediaType.MOVIE) 10751 else 10762
        } else genre
        val previousQuery = catalogQuery
        val keepCurrentPage = _catalog.value is LoadState.Ready &&
            previousQuery.type == type && previousQuery.network == network
        catalogQuery = CatalogQuery(type, category, safeGenre, if (activeProfile?.kidsMode == true) null else network, sort, year)
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            if (!keepCurrentPage) _catalog.value = LoadState.Loading
            _catalog.value = runLoad { catalogRepository.catalog(type, category, 1, safeGenre, catalogQuery.network, sort, year) }
        }
    }

    fun loadMoreCatalog() {
        val current = (_catalog.value as? LoadState.Ready)?.value ?: return
        if (_catalogLoadingMore.value || current.page >= current.totalPages) return
        viewModelScope.launch {
            _catalogLoadingMore.value = true
            val query = catalogQuery
            runCatching {
                catalogRepository.catalog(
                    query.type, query.category, current.page + 1,
                    query.genre, query.network, query.sort, query.year,
                )
            }.onSuccess { next ->
                _catalog.value = LoadState.Ready(next.copy(items = (current.items + next.items).distinctBy { it.type to it.id }))
            }
            _catalogLoadingMore.value = false
        }
    }

    fun search(query: String, type: MediaType? = null) {
        val normalized = query.trim()
        searchJob?.cancel()
        if (normalized.length < 2) {
            _search.value = LoadState.Ready(SearchResults())
            return
        }
        searchQuery = normalized
        searchType = type
        searchJob = viewModelScope.launch {
            _search.value = LoadState.Loading
            _search.value = runLoad { catalogRepository.search(normalized, type, 1) }
        }
    }

    fun loadMoreSearch() {
        val current = (_search.value as? LoadState.Ready)?.value ?: return
        if (_searchLoadingMore.value || current.page >= current.totalPages || searchQuery.isBlank()) return
        viewModelScope.launch {
            _searchLoadingMore.value = true
            runCatching { catalogRepository.search(searchQuery, searchType, current.page + 1) }
                .onSuccess { next ->
                    _search.value = LoadState.Ready(next.copy(items = (current.items + next.items).distinctBy { it.type to it.id }))
                }
            _searchLoadingMore.value = false
        }
    }

    fun loadDetails(type: MediaType, id: Int) {
        detailsJob?.cancel()
        seasonJob?.cancel()
        detailsJob = viewModelScope.launch {
            _details.value = LoadState.Loading
            _season.value = LoadState.Loading
            val loaded = runLoad { catalogRepository.details(type, id) }
            _details.value = loaded
            val details = (loaded as? LoadState.Ready)?.value
            if (type == MediaType.TV && details != null) {
                val remembered = viewingPreferences.getInt("last_season_${activeProfile?.id ?: "main"}_$id", 0).takeIf { it > 0 }
                    ?: trackedItems.value.firstOrNull { it.mediaId == id && it.mediaType == MediaType.TV }?.season
                val seasonNumber = remembered?.takeIf { candidate -> details.seasons.any { it.number == candidate } }
                    ?: details.seasons.firstOrNull()?.number ?: 1
                loadSeason(id, seasonNumber)
            }
        }
    }

    fun loadSeason(showId: Int, seasonNumber: Int) {
        viewingPreferences.edit().putInt("last_season_${activeProfile?.id ?: "main"}_$showId", seasonNumber).apply()
        seasonJob?.cancel()
        seasonJob = viewModelScope.launch {
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

    fun playFeatured(item: app.ncinq.tv.data.MediaItem, onReady: () -> Unit) {
        viewModelScope.launch {
            val type = MediaType.fromWire(item.type)
            val details = runCatching { catalogRepository.details(type, item.id) }.getOrNull() ?: return@launch
            if (type == MediaType.MOVIE) {
                playMovie(details)
            } else {
                val firstSeason = details.seasons.firstOrNull()?.number ?: 1
                val season = runCatching { catalogRepository.season(details.id, firstSeason) }.getOrNull() ?: return@launch
                val firstEpisode = season.episodes.firstOrNull()?.number ?: return@launch
                playEpisode(details, season, firstEpisode)
            }
            onReady()
        }
    }

    fun playEpisode(details: MediaDetails, season: SeasonDetails, episodeNumber: Int) {
        if (season.showId != details.id) return
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
            expectedRuntimeMinutes = episode.runtimeMinutes,
            episodeCount = season.episodes.size,
            seasonCount = details.seasonCount,
        )
    }

    fun resume(item: TrackedItem, details: MediaDetails? = null) {
        _activePlayback.value = PlaybackRequest(
            mediaId = item.mediaId,
            mediaType = item.mediaType,
            title = item.title,
            posterUrl = item.posterUrl,
            backdropUrl = item.backdropUrl,
            imdbId = details?.imdbId,
            releaseYear = details?.year,
            season = item.season,
            episode = item.episode,
            episodeTitle = item.episodeTitle,
            expectedRuntimeMinutes = item.expectedRuntimeMinutes,
            seasonCount = details?.seasonCount ?: 0,
        )
    }

    fun resumeHistory(item: TrackedItem, season: Int?, episode: Int?) {
        resume(
            item.copy(
                season = season,
                episode = episode,
                episodeTitle = if (item.season == season && item.episode == episode) item.episodeTitle else null,
                positionMs = if (item.season == season && item.episode == episode) item.positionMs else 0,
            ),
        )
    }

    fun resumePosition(request: PlaybackRequest): Long {
        return trackedItems.value
            .firstOrNull {
                it.mediaId == request.mediaId && it.mediaType == request.mediaType &&
                    (request.mediaType == MediaType.MOVIE || (it.season == request.season && it.episode == request.episode))
            }
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
                expectedRuntimeMinutes = nextEpisode.runtimeMinutes,
                backdropUrl = nextEpisode.stillUrl ?: current.backdropUrl,
                episodeCount = season.episodes.size,
                seasonCount = seasonCount,
            )
        }
        return null
    }

    suspend fun previousPlayback(current: PlaybackRequest): PlaybackRequest? {
        if (current.mediaType != MediaType.TV) return null
        val details = runCatching { catalogRepository.details(MediaType.TV, current.mediaId) }.getOrNull()
        val currentSeason = current.season ?: 1
        val currentEpisode = current.episode ?: 1
        for (seasonNumber in currentSeason downTo 1) {
            val season = runCatching { catalogRepository.season(current.mediaId, seasonNumber) }.getOrNull() ?: continue
            val previous = season.episodes.lastOrNull { episode ->
                seasonNumber < currentSeason || episode.number < currentEpisode
            } ?: continue
            return current.copy(
                imdbId = current.imdbId ?: details?.imdbId,
                releaseYear = current.releaseYear ?: details?.year,
                season = seasonNumber,
                episode = previous.number,
                episodeTitle = previous.name,
                expectedRuntimeMinutes = previous.runtimeMinutes,
                backdropUrl = previous.stillUrl ?: current.backdropUrl,
                episodeCount = season.episodes.size,
                seasonCount = current.seasonCount.takeIf { it > 0 } ?: details?.seasonCount ?: currentSeason,
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
        viewModelScope.launch(Dispatchers.IO) {
            trackerRepository.saveProgress(next, 0L, 0L, completed = false)
        }
    }

    suspend fun completeAndAdvance(
        current: PlaybackRequest,
        durationMs: Long,
        next: PlaybackRequest?,
    ) {
        withContext(Dispatchers.IO) {
            trackerRepository.saveProgress(current, durationMs, durationMs, completed = true)
            if (next != null) trackerRepository.saveProgress(next, 0L, 0L, completed = false)
        }
        if (next != null) _activePlayback.value = next
    }

    fun saveProgress(request: PlaybackRequest, positionMs: Long, durationMs: Long, completed: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            trackerRepository.saveProgress(request, positionMs, durationMs, completed)
        }
    }

    fun toggleFavorite(details: MediaDetails) {
        viewModelScope.launch(Dispatchers.IO) { trackerRepository.toggleFavorite(details) }
    }

    fun removeTracked(item: TrackedItem) {
        viewModelScope.launch(Dispatchers.IO) { trackerRepository.remove(item) }
    }

    fun removeFromHistory(item: TrackedItem) {
        viewModelScope.launch(Dispatchers.IO) { trackerRepository.removeFromHistory(item) }
    }

    fun removeEpisodeFromHistory(item: TrackedItem, season: Int, episode: Int) {
        viewModelScope.launch(Dispatchers.IO) { trackerRepository.removeEpisodeFromHistory(item, season, episode) }
    }

    fun dismissUpdate() {
        _update.value = null
    }

    fun setUpdateInstallState(state: UpdateInstallState) {
        _updateInstallState.value = state
    }


    fun checkForUpdates() {
        checkForUpdate(manual = true)
    }

    private fun checkForUpdate(manual: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (manual) _updateCheckMessage.value = "Checking for updates..."
            runCatching { catalogRepository.update(BuildConfig.VERSION_CODE) }
                .onSuccess { info ->
                    if (info.available) {
                        _update.value = info
                        if (manual) _updateCheckMessage.value = "Version ${info.versionName} is ready to install."
                    } else if (manual) {
                        _updateCheckMessage.value = "nCinqTV is up to date."
                    }
                }
                .onFailure {
                    if (manual) _updateCheckMessage.value = "Could not check for updates. Try again."
                }
        }
    }

    private suspend fun <T> runLoad(block: suspend () -> T): LoadState<T> {
        return try {
            LoadState.Ready(block())
        } catch (error: Throwable) {
            LoadState.Failed(error.message ?: "Something went wrong. Please try again.")
        }
    }

    private data class CatalogQuery(
        val type: MediaType,
        val category: String = "popular",
        val genre: Int? = null,
        val network: Int? = null,
        val sort: String? = null,
        val year: Int? = null,
    )
}
