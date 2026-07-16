package app.ncinq.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.ncinq.tv.AppViewModel
import app.ncinq.tv.data.Episode
import app.ncinq.tv.data.LoadState
import app.ncinq.tv.data.MediaDetails
import app.ncinq.tv.data.MediaItem
import app.ncinq.tv.data.MediaType
import app.ncinq.tv.data.NetworkItem
import app.ncinq.tv.data.SeasonDetails
import app.ncinq.tv.data.TrackedItem
import app.ncinq.tv.data.TrackingStatus
import coil3.compose.AsyncImage

private val ScreenGutter = 36.dp

@Composable
fun HomeScreen(viewModel: AppViewModel, onOpen: (MediaItem) -> Unit, onNetwork: (NetworkItem) -> Unit) {
    val state by viewModel.home.collectAsState()
    when (val value = state) {
        LoadState.Loading -> LoadingScreen("Loading nCinqTV")
        is LoadState.Failed -> ErrorScreen(value.message) { viewModel.loadHome(force = true) }
        is LoadState.Ready -> {
            val feed = value.value
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(AppBackground),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 42.dp),
            ) {
                feed.featured?.let { featured ->
                    item(key = "featured") {
                        FeaturedHero(featured = featured, onOpen = { onOpen(featured) })
                    }
                }
                if (feed.networks.isNotEmpty()) {
                    item(key = "networks") { NetworkShelf(feed.networks, onNetwork) }
                }
                items(feed.rows, key = { it.title }) { row ->
                    MediaShelf(row = row, onOpen = onOpen)
                }
            }
        }
    }
}

@Composable
private fun NetworkShelf(networks: List<NetworkItem>, onNetwork: (NetworkItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Browse by network", color = TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = ScreenGutter))
        LazyRow(contentPadding = PaddingValues(horizontal = ScreenGutter, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(networks, key = { it.id }) { network ->
                var focused by remember { mutableStateOf(false) }
                Box(
                    Modifier.width(190.dp).height(86.dp).scale(if (focused) 1.05f else 1f)
                        .clip(RoundedCornerShape(6.dp)).background(Color(0xFF17181D))
                        .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                        .onFocusChanged { focused = it.isFocused }.clickable { onNetwork(network) },
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(network.logoUrl, network.name, Modifier.width(128.dp).height(54.dp), contentScale = ContentScale.Fit)
                }
            }
        }
    }
}

@Composable
private fun FeaturedHero(featured: MediaItem, onOpen: () -> Unit) {
    val actionFocusRequester = remember { FocusRequester() }
    LaunchedEffect(featured.id) { actionFocusRequester.requestFocus() }
    Box(modifier = Modifier.fillMaxWidth().height(370.dp)) {
        AsyncImage(
            model = featured.backdropUrl,
            contentDescription = featured.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.92f),
                    0.52f to Color.Black.copy(alpha = 0.46f),
                    1f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.fillMaxWidth().height(70.dp).align(Alignment.BottomCenter).background(
                Brush.verticalGradient(listOf(Color.Transparent, AppBackground)),
            ),
        )
        Column(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = ScreenGutter, end = 24.dp).width(560.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Text(featured.title, color = Color.White, fontSize = 39.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
            val metadata = listOfNotNull(
                featured.year?.toString(),
                featured.rating.takeIf { it > 0 }?.let { "%.1f rating".format(it) },
                featured.type.replaceFirstChar { it.uppercase() },
            ).joinToString("  |  ")
            Text(metadata, color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                featured.overview,
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 15.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            FocusButton(
                label = "View details",
                onClick = onOpen,
                icon = Icons.Rounded.Info,
                modifier = Modifier.focusRequester(actionFocusRequester),
            )
        }
    }
}

@Composable
fun CatalogScreen(
    viewModel: AppViewModel,
    type: MediaType,
    onOpen: (MediaItem) -> Unit,
    initialNetwork: Int? = null,
) {
    var category by remember(type, initialNetwork) { mutableStateOf("popular") }
    var genre by remember(type, initialNetwork) { mutableStateOf<Int?>(null) }
    var sort by remember(type, initialNetwork) { mutableStateOf<String?>(null) }
    LaunchedEffect(type, initialNetwork) { viewModel.loadCatalog(type, network = initialNetwork) }
    val state by viewModel.catalog.collectAsState()
    val loadingMore by viewModel.catalogLoadingMore.collectAsState()
    when (val value = state) {
        LoadState.Loading -> LoadingScreen("Loading ${if (type == MediaType.MOVIE) "movies" else "shows"}")
        is LoadState.Failed -> ErrorScreen(value.message) { viewModel.loadCatalog(type, category, genre, initialNetwork, sort) }
        is LoadState.Ready -> Column(Modifier.fillMaxSize().background(AppBackground)) {
            ScreenTitle(if (type == MediaType.MOVIE) "Movies" else "TV Shows")
            LazyRow(
                contentPadding = PaddingValues(horizontal = ScreenGutter, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val categories = if (type == MediaType.MOVIE) {
                    listOf("popular" to "Popular", "top_rated" to "Top rated", "now_playing" to "Now playing", "upcoming" to "Upcoming")
                } else {
                    listOf("popular" to "Popular", "top_rated" to "Top rated", "airing_today" to "Airing today", "on_the_air" to "On the air")
                }
                items(categories, key = { it.first }) { option ->
                    FocusButton(option.second, onClick = {
                        category = option.first; sort = null
                        viewModel.loadCatalog(type, category, genre, initialNetwork, sort)
                    }, selected = category == option.first && sort == null)
                }
                item {
                    FocusButton("Newest", onClick = {
                        sort = if (type == MediaType.MOVIE) "primary_release_date.desc" else "first_air_date.desc"
                        viewModel.loadCatalog(type, category, genre, initialNetwork, sort)
                    }, selected = sort != null)
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = ScreenGutter, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                item {
                    FocusButton("All genres", onClick = {
                        genre = null; viewModel.loadCatalog(type, category, null, initialNetwork, sort)
                    }, selected = genre == null)
                }
                items(value.value.genres, key = { it.id }) { option ->
                    FocusButton(option.name, onClick = {
                        genre = option.id; viewModel.loadCatalog(type, category, option.id, initialNetwork, sort)
                    }, selected = genre == option.id)
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(172.dp),
                contentPadding = PaddingValues(horizontal = ScreenGutter, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(25.dp),
            ) {
                itemsIndexed(value.value.items, key = { _, item -> "${item.type}:${item.id}" }) { index, item ->
                    if (index >= value.value.items.lastIndex - 5) LaunchedEffect(index, value.value.page) { viewModel.loadMoreCatalog() }
                    MediaCard(item = item, onClick = { onOpen(item) })
                }
                if (loadingMore) item { CircularProgressIndicator(color = BrandBright, modifier = Modifier.padding(28.dp)) }
            }
        }
    }
}

@Composable
fun SearchScreen(viewModel: AppViewModel, onOpen: (MediaItem) -> Unit) {
    var query by remember { mutableStateOf("") }
    var fieldFocused by remember { mutableStateOf(false) }
    var mediaType by remember { mutableStateOf<MediaType?>(null) }
    val state by viewModel.search.collectAsState()
    val focusManager = LocalFocusManager.current
    val firstResultFocusRequester = remember { FocusRequester() }

    fun submitSearch() {
        viewModel.search(query, mediaType)
        focusManager.clearFocus()
    }

    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        ScreenTitle("Search")
        Row(
            modifier = Modifier.padding(horizontal = ScreenGutter, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .width(620.dp)
                    .height(50.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (fieldFocused) Color.White else Panel)
                    .border(2.dp, if (fieldFocused) Color.White else Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = if (fieldFocused) Color.Black else TextSecondary, modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(11.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = if (fieldFocused) Color.Black else TextPrimary, fontSize = 17.sp),
                    cursorBrush = SolidColor(if (fieldFocused) Color.Black else BrandBright),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                    modifier = Modifier.weight(1f).onFocusChanged { fieldFocused = it.isFocused },
                    decorationBox = { field ->
                        Box {
                            if (query.isBlank()) Text("Movies and TV shows", color = if (fieldFocused) Color.DarkGray else TextSecondary, fontSize = 17.sp)
                            field()
                        }
                    },
                )
            }
            FocusButton("Search", onClick = ::submitSearch, enabled = query.trim().length >= 2, icon = Icons.Rounded.Search)
        }
        Row(modifier = Modifier.padding(horizontal = ScreenGutter, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(null to "Everything", MediaType.MOVIE to "Movies", MediaType.TV to "TV Shows").forEach { option ->
                FocusButton(option.second, onClick = {
                    mediaType = option.first
                    if (query.trim().length >= 2) viewModel.search(query, mediaType)
                }, selected = mediaType == option.first)
            }
        }

        when (val value = state) {
            LoadState.Loading -> LoadingScreen("Searching", modifier = Modifier.fillMaxWidth().weight(1f))
            is LoadState.Failed -> ErrorScreen(value.message, modifier = Modifier.fillMaxWidth().weight(1f)) { viewModel.search(query, mediaType) }
            is LoadState.Ready -> {
                if (value.value.items.isEmpty()) {
                    EmptyMessage(
                        title = if (query.isBlank()) "Find something to watch" else "No matches",
                        detail = if (query.isBlank()) "Search by title." else "Try another title.",
                    )
                } else {
                    LaunchedEffect(value.value.items) { firstResultFocusRequester.requestFocus() }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(172.dp),
                        contentPadding = PaddingValues(horizontal = ScreenGutter, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalArrangement = Arrangement.spacedBy(25.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) {
                        itemsIndexed(value.value.items, key = { _, item -> "${item.type}:${item.id}" }) { index, item ->
                            if (index >= value.value.items.lastIndex - 5) LaunchedEffect(index, value.value.page) { viewModel.loadMoreSearch() }
                            MediaCard(
                                item = item,
                                onClick = { onOpen(item) },
                                modifier = if (index == 0) Modifier.focusRequester(firstResultFocusRequester) else Modifier,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailsScreen(
    viewModel: AppViewModel,
    mediaType: MediaType,
    mediaId: Int,
    onPlay: () -> Unit,
    onOpenRelated: (MediaItem) -> Unit,
) {
    LaunchedEffect(mediaType, mediaId) { viewModel.loadDetails(mediaType, mediaId) }
    val detailsState by viewModel.details.collectAsState()
    val seasonState by viewModel.season.collectAsState()
    val tracked by viewModel.trackedItems.collectAsState()

    when (val value = detailsState) {
        LoadState.Loading -> LoadingScreen("Loading details")
        is LoadState.Failed -> ErrorScreen(value.message) { viewModel.loadDetails(mediaType, mediaId) }
        is LoadState.Ready -> DetailsContent(
            details = value.value,
            seasonState = seasonState,
            isTracked = tracked.any { it.mediaId == mediaId && it.mediaType == mediaType },
            onToggleTracked = { viewModel.toggleTracked(value.value) },
            onSeason = { viewModel.loadSeason(mediaId, it) },
            onMoviePlay = {
                viewModel.playMovie(value.value)
                onPlay()
            },
            onEpisodePlay = { season, episode ->
                viewModel.playEpisode(value.value, season, episode)
                onPlay()
            },
            onOpenRelated = onOpenRelated,
        )
    }
}

@Composable
private fun DetailsContent(
    details: MediaDetails,
    seasonState: LoadState<SeasonDetails>,
    isTracked: Boolean,
    onToggleTracked: () -> Unit,
    onSeason: (Int) -> Unit,
    onMoviePlay: () -> Unit,
    onEpisodePlay: (SeasonDetails, Int) -> Unit,
    onOpenRelated: (MediaItem) -> Unit,
) {
    val actionFocusRequester = remember(details.id, details.type) { FocusRequester() }
    LaunchedEffect(details.id, details.type) { actionFocusRequester.requestFocus() }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AppBackground),
        contentPadding = PaddingValues(bottom = 42.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "header") {
            Box(Modifier.fillMaxWidth().height(430.dp)) {
                AsyncImage(details.backdropUrl, details.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 0.94f),
                            0.55f to Color.Black.copy(alpha = 0.5f),
                            1f to Color.Transparent,
                        ),
                    ),
                )
                Box(
                    Modifier.fillMaxWidth().height(70.dp).align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, AppBackground))),
                )
                Column(
                    modifier = Modifier.align(Alignment.TopStart).padding(start = ScreenGutter, top = 28.dp, end = 24.dp).width(760.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(details.title, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                    if (details.tagline.isNotBlank()) Text(details.tagline, color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(
                        listOfNotNull(
                            details.year?.toString(),
                            details.contentRating,
                            details.runtimeMinutes?.let { "$it min" },
                            details.rating.takeIf { it > 0 }?.let { "%.1f rating".format(it) },
                            details.genres.take(3).joinToString(" / ").takeIf(String::isNotBlank),
                        ).joinToString("  |  "),
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(details.overview, color = Color.White.copy(alpha = 0.88f), fontSize = 15.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (details.type == MediaType.MOVIE.wireName) {
                            FocusButton("Play", onMoviePlay, icon = Icons.Rounded.PlayArrow, modifier = Modifier.focusRequester(actionFocusRequester))
                        }
                        FocusButton(
                            if (isTracked) "In tracker" else "Add to tracker",
                            onToggleTracked,
                            selected = isTracked,
                            icon = if (isTracked) Icons.Rounded.Check else Icons.Rounded.Add,
                            modifier = if (details.type == MediaType.TV.wireName) Modifier.focusRequester(actionFocusRequester) else Modifier,
                        )
                    }
                }
            }
        }

        if (details.type == MediaType.TV.wireName) {
            item(key = "season-picker") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Seasons", color = TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = ScreenGutter))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = ScreenGutter, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(details.seasons, key = { it.number }) { season ->
                            FocusButton(
                                label = season.name,
                                onClick = { onSeason(season.number) },
                                selected = (seasonState as? LoadState.Ready)?.value?.seasonNumber == season.number,
                            )
                        }
                    }
                }
            }

            when (seasonState) {
                LoadState.Loading -> item { LoadingScreen("Loading episodes", modifier = Modifier.fillMaxWidth().height(190.dp)) }
                is LoadState.Failed -> item { EmptyMessage("Episodes unavailable", seasonState.message) }
                is LoadState.Ready -> item(key = "episodes-${seasonState.value.seasonNumber}") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(seasonState.value.name, color = TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = ScreenGutter))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = ScreenGutter, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            items(seasonState.value.episodes, key = { it.number }) { episode ->
                                EpisodeCard(episode) { onEpisodePlay(seasonState.value, episode.number) }
                            }
                        }
                    }
                }
            }
        }
        item(key = "about") {
            Column(Modifier.padding(horizontal = ScreenGutter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("About ${details.title}", color = TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                val facts = listOfNotNull(
                    details.status.takeIf(String::isNotBlank)?.let { "Status: $it" },
                    details.creators.takeIf { it.isNotEmpty() }?.joinToString()?.let { "Created by: $it" },
                    details.countries.takeIf { it.isNotEmpty() }?.joinToString()?.let { "Countries: $it" },
                    details.networks.takeIf { it.isNotEmpty() }?.joinToString { it.name }?.let { "Networks: $it" },
                )
                facts.forEach { Text(it, color = TextSecondary, fontSize = 14.sp) }
            }
        }
        if (details.cast.isNotEmpty()) item(key = "cast") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Cast", color = TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = ScreenGutter))
                LazyRow(contentPadding = PaddingValues(horizontal = ScreenGutter, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(details.cast, key = { it.id }) { person ->
                        Column(Modifier.width(116.dp)) {
                            AsyncImage(person.profileUrl, person.name, Modifier.width(116.dp).height(145.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                            Text(person.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(person.character, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (details.recommendations.isNotEmpty()) item(key = "recommendations") {
            MediaShelf(app.ncinq.tv.data.MediaRow("More like this", details.recommendations), onOpenRelated)
        }
    }
}

@Composable
private fun EpisodeCard(episode: Episode, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale = if (focused) 1.045f else 1f
    Column(
        modifier = Modifier.width(250.dp).scale(scale).onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick),
    ) {
        Box(
            Modifier.size(width = 250.dp, height = 141.dp).clip(RoundedCornerShape(6.dp)).background(Panel)
                .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp)),
        ) {
            AsyncImage(episode.stillUrl, episode.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(
                Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.78f)).padding(horizontal = 9.dp, vertical = 5.dp),
            ) {
                Text("Episode ${episode.number}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(episode.name, color = TextPrimary, fontSize = 14.sp, fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        episode.runtimeMinutes?.let { Text("$it min", color = TextSecondary, fontSize = 11.sp) }
    }
}

private enum class TrackerFilter { ALL, CONTINUE, PLANNED, COMPLETED }

@Composable
fun TrackerScreen(viewModel: AppViewModel, onResume: () -> Unit) {
    val tracked by viewModel.trackedItems.collectAsState()
    var filter by remember { mutableStateOf(TrackerFilter.ALL) }
    val visible = tracked.filter { item ->
        when (filter) {
            TrackerFilter.ALL -> true
            TrackerFilter.CONTINUE -> item.status == TrackingStatus.WATCHING
            TrackerFilter.PLANNED -> item.status == TrackingStatus.PLANNED
            TrackerFilter.COMPLETED -> item.status == TrackingStatus.COMPLETED
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        ScreenTitle("Tracker")
        Row(modifier = Modifier.padding(horizontal = ScreenGutter, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TrackerFilter.entries.forEach { option ->
                val label = when (option) {
                    TrackerFilter.ALL -> "All"
                    TrackerFilter.CONTINUE -> "Continue watching"
                    TrackerFilter.PLANNED -> "Planned"
                    TrackerFilter.COMPLETED -> "Completed"
                }
                FocusButton(label, onClick = { filter = option }, selected = filter == option)
            }
        }
        if (visible.isEmpty()) {
            EmptyMessage("Nothing here yet", "Titles you save or watch will appear here.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(242.dp),
                contentPadding = PaddingValues(horizontal = ScreenGutter, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                items(visible, key = { "${it.mediaType.wireName}:${it.mediaId}" }) { trackedItem ->
                    LandscapeMediaCard(
                        item = trackedItem.toMediaItem(),
                        progress = trackedItem.progress,
                        onClick = {
                            viewModel.resume(trackedItem)
                            onResume()
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: AppViewModel) {
    val updateMessage by viewModel.updateCheckMessage.collectAsState()
    val installState by viewModel.updateInstallState.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().background(AppBackground).padding(horizontal = ScreenGutter, vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("Settings", color = TextPrimary, fontSize = 31.sp, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("App updates", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("nCinqTV ${viewModel.currentVersionName}", color = TextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            FocusButton("Check for updates", onClick = viewModel::checkForUpdates, icon = Icons.Rounded.SystemUpdate)
            updateMessage?.let { message ->
                Text(message, color = if (message.contains("up to date")) Success else TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 5.dp))
            }
            if (installState.active) {
                Text(installState.message, color = TextSecondary, fontSize = 14.sp)
                Box(Modifier.width(360.dp).height(6.dp).background(Color.White.copy(alpha = 0.16f))) {
                    Box(Modifier.fillMaxWidth(installState.progress / 100f).height(6.dp).background(BrandBright))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Playback", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Captions are enabled when available. Progress is saved on this TV.", color = TextSecondary, fontSize = 14.sp)
        }
    }
}

private fun TrackedItem.toMediaItem() = MediaItem(
    id = mediaId,
    type = mediaType.wireName,
    title = title,
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
)

@Composable
private fun ScreenTitle(title: String) {
    Text(
        title,
        color = TextPrimary,
        fontSize = 31.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = ScreenGutter, top = 28.dp, bottom = 8.dp),
    )
}

@Composable
private fun LoadingScreen(message: String, modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier.background(AppBackground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(13.dp)) {
            CircularProgressIndicator(color = BrandBright, strokeWidth = 3.dp)
            Text(message, color = TextSecondary, fontSize = 15.sp)
        }
    }
}

@Composable
private fun ErrorScreen(message: String, modifier: Modifier = Modifier.fillMaxSize(), onRetry: () -> Unit) {
    Box(modifier.background(AppBackground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Text("Unable to load", color = TextPrimary, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(message, color = TextSecondary, fontSize = 14.sp)
            FocusButton("Try again", onRetry)
        }
    }
}
