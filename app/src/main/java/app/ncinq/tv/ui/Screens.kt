package app.ncinq.tv.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import kotlinx.coroutines.delay

private val ScreenGutter = OverscanHorizontal

@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onOpen: (MediaItem) -> Unit,
    onPlay: (MediaItem) -> Unit,
    onNetwork: (NetworkItem) -> Unit,
) {
    val state by viewModel.home.collectAsState()
    when (val value = state) {
        LoadState.Loading -> LoadingScreen("Loading nCinqTV")
        is LoadState.Failed -> ErrorScreen(value.message) { viewModel.loadHome(force = true) }
        is LoadState.Ready -> {
            val feed = value.value
            var featuredItem by remember(feed.featured?.id) { mutableStateOf(feed.featured) }
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(AppBackground),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = OverscanVertical),
            ) {
                featuredItem?.let { featured ->
                    item(key = "featured") {
                        FeaturedHero(
                            featured = featured,
                            onPlay = { onPlay(featured) },
                            onOpen = { onOpen(featured) },
                        )
                    }
                }
                if (feed.networks.isNotEmpty()) {
                    item(key = "networks") { NetworkShelf(feed.networks, onNetwork) }
                }
                items(feed.rows, key = { it.title }) { row ->
                    MediaShelf(row = row, onOpen = onOpen, onFocus = { featuredItem = it })
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
private fun FeaturedHero(featured: MediaItem, onPlay: () -> Unit, onOpen: () -> Unit) {
    val actionFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { actionFocusRequester.requestFocus() }
    Box(modifier = Modifier.fillMaxWidth().height(500.dp)) {
        Crossfade(targetState = featured, label = "featuredArtwork") { item ->
            AsyncImage(
                model = item.backdropUrl,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
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
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, AppBackground)),
            ),
        )
        Column(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = ScreenGutter, end = ScreenGutter).width(600.dp),
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FocusButton(
                    label = "Play",
                    onClick = onPlay,
                    icon = Icons.Rounded.PlayArrow,
                    emphasis = true,
                    modifier = Modifier.focusRequester(actionFocusRequester),
                )
                FocusButton(label = "More info", onClick = onOpen, icon = Icons.Rounded.Info)
            }
        }
    }
}

@Composable
fun CatalogScreen(
    viewModel: AppViewModel,
    type: MediaType,
    onOpen: (MediaItem) -> Unit,
    initialNetwork: Int? = null,
    gridHeader: (@Composable () -> Unit)? = null,
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
            val categories = if (type == MediaType.MOVIE) {
                listOf("popular" to "Popular", "top_rated" to "Top rated", "now_playing" to "Now playing", "upcoming" to "Upcoming")
            } else {
                listOf("popular" to "Popular", "top_rated" to "Top rated", "airing_today" to "Airing today", "on_the_air" to "On the air")
            }
            Row(
                modifier = Modifier.fillMaxWidth().background(Panel.copy(alpha = 0.58f))
                    .padding(horizontal = ScreenGutter, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val browseKey = if (sort != null) "newest" else category
                val browseOptions = categories + ("newest" to "Newest")
                FilterDropdown(
                    label = "Browse: ${browseOptions.firstOrNull { it.first == browseKey }?.second ?: "Popular"}",
                    options = browseOptions,
                    selectedKey = browseKey,
                ) { selected ->
                    if (selected == "newest") {
                        sort = if (type == MediaType.MOVIE) "primary_release_date.desc" else "first_air_date.desc"
                    } else {
                        category = selected
                        sort = null
                    }
                    viewModel.loadCatalog(type, category, genre, initialNetwork, sort)
                }
                val genreOptions = listOf("all" to "All genres") + value.value.genres.map { it.id.toString() to it.name }
                FilterDropdown(
                    label = "Genre: ${genreOptions.firstOrNull { it.first == (genre?.toString() ?: "all") }?.second ?: "All"}",
                    options = genreOptions,
                    selectedKey = genre?.toString() ?: "all",
                ) { selected ->
                    genre = selected.takeUnless { it == "all" }?.toIntOrNull()
                    viewModel.loadCatalog(type, category, genre, initialNetwork, sort)
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(horizontal = ScreenGutter, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                gridHeader?.let { header ->
                    item(key = "grid-header", span = { GridItemSpan(maxLineSpan) }) { header() }
                }
                itemsIndexed(value.value.items, key = { _, item -> "${item.type}:${item.id}" }) { index, item ->
                    if (index >= value.value.items.lastIndex - 5) LaunchedEffect(index, value.value.page) { viewModel.loadMoreCatalog() }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        MediaCard(
                            item = item,
                            onClick = { onOpen(item) },
                            cardWidth = 132.dp,
                            posterHeight = 198.dp,
                            focusedScale = 1.05f,
                        )
                    }
                }
                if (loadingMore) item { CircularProgressIndicator(color = BrandBright, modifier = Modifier.padding(28.dp)) }
            }
        }
    }
}

@Composable
private fun FilterDropdown(
    label: String,
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var restoreButtonFocus by remember { mutableStateOf(false) }
    val buttonFocusRequester = remember { FocusRequester() }
    LaunchedEffect(expanded, restoreButtonFocus) {
        if (!expanded && restoreButtonFocus) {
            delay(60)
            buttonFocusRequester.requestFocus()
            restoreButtonFocus = false
        }
    }
    Box {
        FocusButton(
            label,
            onClick = { expanded = true },
            selected = expanded,
            modifier = Modifier.focusRequester(buttonFocusRequester),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                restoreButtonFocus = true
                expanded = false
            },
            modifier = Modifier.background(PanelRaised),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option.second,
                            color = if (option.first == selectedKey) BrandBright else TextPrimary,
                            fontWeight = if (option.first == selectedKey) FontWeight.Bold else FontWeight.Medium,
                        )
                    },
                    onClick = {
                        restoreButtonFocus = true
                        expanded = false
                        onSelect(option.first)
                    },
                )
            }
        }
    }
}

@Composable
fun NetworkCatalogScreen(viewModel: AppViewModel, networkId: Int, onOpen: (MediaItem) -> Unit) {
    var type by remember(networkId) { mutableStateOf(MediaType.TV) }
    val firstToggleFocusRequester = remember(networkId) { FocusRequester() }
    val homeState by viewModel.home.collectAsState()
    val network = (homeState as? LoadState.Ready)?.value?.networks?.firstOrNull { it.id == networkId }
        ?: NetworkItem(id = networkId, name = "Network")
    LaunchedEffect(networkId) {
        delay(120)
        firstToggleFocusRequester.requestFocus()
    }
    Column(Modifier.fillMaxSize().background(AppBackground)) {
        Row(
            modifier = Modifier.padding(horizontal = ScreenGutter, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FocusButton(
                "TV Shows",
                onClick = { type = MediaType.TV },
                selected = type == MediaType.TV,
                modifier = Modifier.focusRequester(firstToggleFocusRequester),
            )
            FocusButton("Movies", onClick = { type = MediaType.MOVIE }, selected = type == MediaType.MOVIE)
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            CatalogScreen(
                viewModel,
                type,
                onOpen,
                initialNetwork = networkId,
                gridHeader = { NetworkBanner(listOf(network), Modifier.padding(vertical = 8.dp)) },
            )
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
    val searchFocusRequester = remember { FocusRequester() }

    fun submitSearch() {
        viewModel.search(query, mediaType)
        focusManager.clearFocus()
    }

    LaunchedEffect(query, mediaType) {
        delay(300)
        viewModel.search(query, mediaType)
    }

    LaunchedEffect(Unit) {
        delay(180)
        searchFocusRequester.requestFocus()
    }

    Column(modifier = Modifier.fillMaxSize().background(AppBackground).imePadding()) {
        ScreenTitle("Search")
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = ScreenGutter, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(6.dp))
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
                        modifier = Modifier.weight(1f).focusRequester(searchFocusRequester).onFocusChanged { fieldFocused = it.isFocused },
                        decorationBox = { field ->
                            Box {
                                if (query.isBlank()) Text("Search titles", color = if (fieldFocused) Color.DarkGray else TextSecondary, fontSize = 17.sp)
                                field()
                            }
                        },
                    )
                }
                Text("Search in", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                listOf(null to "Everything", MediaType.MOVIE to "Movies", MediaType.TV to "TV Shows").forEach { option ->
                    FocusButton(
                        option.second,
                        onClick = { mediaType = option.first },
                        selected = mediaType == option.first,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val suggestions = when (val current = state) {
                    is LoadState.Ready -> current.value.items.distinctBy { it.title }.take(8)
                    else -> emptyList()
                }
                if (suggestions.isNotEmpty()) {
                    Text("Suggestions", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(suggestions, key = { "suggestion:${it.type}:${it.id}" }) { suggestion ->
                            FocusButton(
                                suggestion.title,
                                onClick = {
                                    query = suggestion.title
                                    viewModel.search(suggestion.title, mediaType)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            Column(Modifier.weight(2f).fillMaxHeight()) {
                Text(
                    if (query.isBlank()) "Results" else "Results for \"${query.trim()}\"",
                    color = TextPrimary,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                when (val value = state) {
                    LoadState.Loading -> LoadingScreen("Searching", modifier = Modifier.fillMaxSize())
                    is LoadState.Failed -> ErrorScreen(value.message, modifier = Modifier.fillMaxSize()) { viewModel.search(query, mediaType) }
                    is LoadState.Ready -> if (value.value.items.isEmpty()) {
                        EmptyMessage(
                            title = if (query.isBlank()) "Find something to watch" else "No matches",
                            detail = if (query.isBlank()) "Start typing to see results." else "Try another title.",
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(132.dp),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            itemsIndexed(value.value.items, key = { _, item -> "${item.type}:${item.id}" }) { index, item ->
                                if (index >= value.value.items.lastIndex - 5) LaunchedEffect(index, value.value.page) { viewModel.loadMoreSearch() }
                                MediaCard(
                                    item = item,
                                    onClick = { onOpen(item) },
                                    cardWidth = 132.dp,
                                    posterHeight = 198.dp,
                                    focusedScale = 1.05f,
                                )
                            }
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
    val trackedItem = tracked.firstOrNull { it.mediaId == mediaId && it.mediaType == mediaType }

    val currentDetailsState = when (val value = detailsState) {
        is LoadState.Ready -> if (value.value.id == mediaId && value.value.type == mediaType.wireName) value else LoadState.Loading
        else -> value
    }

    when (val value = currentDetailsState) {
        LoadState.Loading -> LoadingScreen("Loading details")
        is LoadState.Failed -> ErrorScreen(value.message) { viewModel.loadDetails(mediaType, mediaId) }
        is LoadState.Ready -> DetailsContent(
            details = value.value,
            seasonState = when (val season = seasonState) {
                is LoadState.Ready -> if (season.value.showId == mediaId) season else LoadState.Loading
                else -> season
            },
            trackedItem = trackedItem,
            onToggleFavorite = { viewModel.toggleFavorite(value.value) },
            onContinue = {
                trackedItem?.let { viewModel.resume(it, value.value) }
                onPlay()
            },
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
            showRecommendations = !viewModel.isKidsProfile,
        )
    }
}

@Composable
private fun DetailsContent(
    details: MediaDetails,
    seasonState: LoadState<SeasonDetails>,
    trackedItem: TrackedItem?,
    onToggleFavorite: () -> Unit,
    onContinue: () -> Unit,
    onSeason: (Int) -> Unit,
    onMoviePlay: () -> Unit,
    onEpisodePlay: (SeasonDetails, Int) -> Unit,
    onOpenRelated: (MediaItem) -> Unit,
    showRecommendations: Boolean,
) {
    val actionFocusRequester = remember(details.id, details.type) { FocusRequester() }
    val canContinue = details.type == MediaType.TV.wireName && trackedItem?.let {
        it.status == TrackingStatus.WATCHING && it.season != null && it.episode != null
    } == true
    LaunchedEffect(details.id, details.type) {
        delay(180)
        actionFocusRequester.requestFocus()
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AppBackground),
        contentPadding = PaddingValues(bottom = 42.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "header") {
            Box(Modifier.fillMaxWidth().height(520.dp)) {
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
                if (details.networks.isNotEmpty()) {
                    NetworkBanner(
                        details.networks,
                        Modifier.align(Alignment.TopEnd).fillMaxWidth(0.34f)
                            .padding(top = 28.dp, end = ScreenGutter),
                    )
                }
                Column(
                    modifier = Modifier.align(Alignment.TopStart)
                        .padding(start = ScreenGutter, top = 28.dp, end = 24.dp)
                        .fillMaxWidth(0.62f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(details.title, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, maxLines = 3)
                    if (details.tagline.isNotBlank()) Text(details.tagline, color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        details.rating.takeIf { it > 0 }?.let { MetadataTag("%.1f rating".format(it), highlighted = true) }
                        details.year?.let { MetadataTag(it.toString()) }
                        details.contentRating?.let { MetadataTag(it) }
                        if (details.type == MediaType.TV.wireName && details.seasonCount > 0) {
                            MetadataTag("${details.seasonCount} season${if (details.seasonCount == 1) "" else "s"}")
                        } else {
                            details.runtimeMinutes?.let { MetadataTag("$it min") }
                        }
                        details.genres.firstOrNull()?.let { MetadataTag(it) }
                    }
                    Text(details.overview, color = Color.White.copy(alpha = 0.92f), fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (canContinue) {
                            FocusButton(
                                "Continue S${trackedItem?.season} E${trackedItem?.episode}",
                                onContinue,
                                icon = Icons.Rounded.PlayArrow,
                                modifier = Modifier.focusRequester(actionFocusRequester),
                            )
                        }
                        if (details.type == MediaType.MOVIE.wireName) {
                            FocusButton("Play", onMoviePlay, icon = Icons.Rounded.PlayArrow, modifier = Modifier.focusRequester(actionFocusRequester))
                        }
                        FocusButton(
                            if (trackedItem?.isFavorite == true) "Favorited" else "Add favorite",
                            onToggleFavorite,
                            selected = trackedItem?.isFavorite == true,
                            icon = if (trackedItem?.isFavorite == true) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            modifier = if (details.type == MediaType.TV.wireName && !canContinue) Modifier.focusRequester(actionFocusRequester) else Modifier,
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
                                val isCurrent = trackedItem?.let {
                                    it.season == seasonState.value.seasonNumber && it.episode == episode.number
                                } == true
                                EpisodeCard(
                                    episode = episode,
                                    watched = trackedItem?.hasWatched(seasonState.value.seasonNumber, episode.number) == true,
                                    progress = if (isCurrent) trackedItem?.progress ?: 0f else 0f,
                                ) { onEpisodePlay(seasonState.value, episode.number) }
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
        if (showRecommendations && details.recommendations.isNotEmpty()) item(key = "recommendations") {
            MediaShelf(app.ncinq.tv.data.MediaRow("More like this", details.recommendations), onOpenRelated)
        }
    }
}

@Composable
private fun NetworkBanner(networks: List<NetworkItem>, modifier: Modifier = Modifier) {
    val visibleNetworks = networks.take(3)
    Box(
        modifier = modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(8.dp))
            .background(Brush.horizontalGradient(listOf(Brand, BrandBright, Color(0xFFFF8A62))))
            .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("NOW STREAMING ON", color = Color.White.copy(alpha = 0.82f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    visibleNetworks.joinToString("  •  ") { it.name },
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            visibleNetworks.forEach { network ->
                network.logoUrl?.let { logo ->
                    Box(
                        Modifier.width(118.dp).height(60.dp).clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.96f)).padding(10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(logo, network.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeCard(episode: Episode, watched: Boolean, progress: Float, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale = if (focused) 1.09f else 1f
    Column(
        modifier = Modifier.width(250.dp).scale(scale).onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick),
    ) {
        Box(
            Modifier.size(width = 250.dp, height = 141.dp).clip(RoundedCornerShape(6.dp)).background(Panel)
                .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp)),
        ) {
            AsyncImage(episode.stillUrl, episode.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (progress > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.3f))) {
                    Box(Modifier.fillMaxWidth(progress).height(4.dp).background(BrandBright))
                }
            }
            Box(
                Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.78f)).padding(horizontal = 9.dp, vertical = 5.dp),
            ) {
                Text("Episode ${episode.number}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            if (watched) {
                Text(
                    "Watched",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).background(Brand.copy(alpha = 0.92f)).padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(episode.name, color = TextPrimary, fontSize = 14.sp, fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        episode.runtimeMinutes?.let { Text("$it min", color = TextSecondary, fontSize = 11.sp) }
    }
}

@Composable
private fun MetadataTag(label: String, highlighted: Boolean = false) {
    Text(
        text = label,
        color = if (highlighted) Success else Color.White.copy(alpha = 0.9f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.48f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

private enum class HistoryFilter { ALL, CONTINUE, COMPLETED }

private data class HistoryEntry(
    val item: TrackedItem,
    val season: Int? = null,
    val episode: Int? = null,
    val completed: Boolean = false,
) {
    val key: String get() = "${item.mediaType.wireName}:${item.mediaId}:${season ?: 0}:${episode ?: 0}"
}

@Composable
fun HistoryScreen(viewModel: AppViewModel, onResume: () -> Unit) {
    val tracked by viewModel.trackedItems.collectAsState()
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    val historyItems = tracked.filter { it.status != TrackingStatus.PLANNED }
    val entries = historyItems.flatMap { item ->
        if (item.mediaType == MediaType.MOVIE) {
            listOf(HistoryEntry(item, completed = item.status == TrackingStatus.COMPLETED))
        } else {
            val watched = item.watchedEpisodes.orEmpty().mapNotNull { key ->
                val parts = key.split(':')
                val season = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val episode = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                HistoryEntry(item, season, episode, completed = true)
            }
            val current = if (item.season != null && item.episode != null &&
                watched.none { it.season == item.season && it.episode == item.episode }
            ) listOf(HistoryEntry(item, item.season, item.episode)) else emptyList()
            watched + current
        }
    }.filter { entry ->
        when (filter) {
            HistoryFilter.ALL -> true
            HistoryFilter.CONTINUE -> !entry.completed
            HistoryFilter.COMPLETED -> entry.completed
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        ScreenTitle("History")
        Row(modifier = Modifier.padding(horizontal = ScreenGutter, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HistoryFilter.entries.forEach { option ->
                val label = when (option) {
                    HistoryFilter.ALL -> "All history"
                    HistoryFilter.CONTINUE -> "Continue watching"
                    HistoryFilter.COMPLETED -> "Completed"
                }
                FocusButton(label, onClick = { filter = option }, selected = filter == option)
            }
        }
        if (entries.isEmpty()) {
            EmptyMessage("No watch history", "Movies and episodes appear here automatically after you play them.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(242.dp),
                contentPadding = PaddingValues(horizontal = ScreenGutter, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                items(entries, key = { it.key }) { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val label = if (entry.item.mediaType == MediaType.TV) {
                            "${entry.item.title}  S${entry.season} E${entry.episode}"
                        } else entry.item.title
                        LandscapeMediaCard(
                            item = entry.item.toMediaItem().copy(title = label),
                            progress = if (entry.completed) 1f else entry.item.progress,
                            onClick = {
                                viewModel.resumeHistory(entry.item, entry.season, entry.episode)
                                onResume()
                            },
                        )
                        FocusButton(
                            "Remove",
                            onClick = {
                                if (entry.item.mediaType == MediaType.TV && entry.season != null && entry.episode != null) {
                                    viewModel.removeEpisodeFromHistory(entry.item, entry.season, entry.episode)
                                } else viewModel.removeFromHistory(entry.item)
                            },
                            icon = Icons.Rounded.Delete,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FavoritesScreen(viewModel: AppViewModel, onOpen: (MediaItem) -> Unit) {
    val tracked by viewModel.trackedItems.collectAsState()
    val favorites = tracked.filter { it.isFavorite }
    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        ScreenTitle("Favorites")
        if (favorites.isEmpty()) {
            EmptyMessage("No favorites yet", "Add favorites from any movie or TV show details page.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(172.dp),
                contentPadding = PaddingValues(horizontal = ScreenGutter, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                items(favorites, key = { "${it.mediaType.wireName}:${it.mediaId}" }) { item ->
                    MediaCard(item.toMediaItem(), onClick = { onOpen(item.toMediaItem()) }, progress = item.progress)
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
