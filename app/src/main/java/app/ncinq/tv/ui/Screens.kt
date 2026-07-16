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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.ncinq.tv.AppViewModel
import app.ncinq.tv.data.LoadState
import app.ncinq.tv.data.MediaDetails
import app.ncinq.tv.data.MediaItem
import app.ncinq.tv.data.MediaType
import app.ncinq.tv.data.SeasonDetails
import app.ncinq.tv.data.TrackedItem
import app.ncinq.tv.data.TrackingStatus
import coil3.compose.AsyncImage

@Composable
fun HomeScreen(viewModel: AppViewModel, onOpen: (MediaItem) -> Unit) {
    val state by viewModel.home.collectAsState()
    when (val value = state) {
        LoadState.Loading -> LoadingScreen("Loading nCinqTV")
        is LoadState.Failed -> ErrorScreen(value.message) { viewModel.loadHome(force = true) }
        is LoadState.Ready -> {
            val feed = value.value
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(AppBackground),
                verticalArrangement = Arrangement.spacedBy(26.dp),
                contentPadding = PaddingValues(bottom = 42.dp),
            ) {
                feed.featured?.let { featured ->
                    item(key = "featured") {
                        FeaturedHero(featured = featured, onOpen = { onOpen(featured) })
                    }
                }
                items(feed.rows, key = { it.title }) { row ->
                    MediaShelf(row = row, onOpen = onOpen)
                }
            }
        }
    }
}

@Composable
private fun FeaturedHero(featured: MediaItem, onOpen: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(355.dp)) {
        AsyncImage(
            model = featured.backdropUrl,
            contentDescription = featured.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.46f)))
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 48.dp, vertical = 34.dp).width(620.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(featured.title, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                featured.overview,
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 16.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FocusButton(label = "View details", onClick = onOpen)
                val metadata = listOfNotNull(
                    featured.year?.toString(),
                    featured.rating.takeIf { it > 0 }?.let { "%.1f rating".format(it) },
                ).joinToString("  |  ")
                if (metadata.isNotBlank()) {
                    Text(metadata, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.align(Alignment.CenterVertically))
                }
            }
        }
    }
}

@Composable
fun CatalogScreen(viewModel: AppViewModel, type: MediaType, onOpen: (MediaItem) -> Unit) {
    LaunchedEffect(type) { viewModel.loadCatalog(type) }
    val state by viewModel.catalog.collectAsState()
    when (val value = state) {
        LoadState.Loading -> LoadingScreen("Loading ${if (type == MediaType.MOVIE) "movies" else "shows"}")
        is LoadState.Failed -> ErrorScreen(value.message) { viewModel.loadCatalog(type) }
        is LoadState.Ready -> Column(Modifier.fillMaxSize().background(AppBackground)) {
            Text(
                text = if (type == MediaType.MOVIE) "Movies" else "TV Shows",
                color = TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 42.dp, top = 28.dp, bottom = 12.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(154.dp),
                contentPadding = PaddingValues(horizontal = 42.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                items(value.value.items, key = { "${it.type}:${it.id}" }) { item ->
                    MediaCard(item = item, onClick = { onOpen(item) })
                }
            }
        }
    }
}

@Composable
fun SearchScreen(viewModel: AppViewModel, onOpen: (MediaItem) -> Unit) {
    var query by remember { mutableStateOf("") }
    var fieldFocused by remember { mutableStateOf(false) }
    val state by viewModel.search.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().background(AppBackground).padding(horizontal = 42.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Text("Search", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 18.sp),
                cursorBrush = SolidColor(BrandBright),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search(query) }),
                modifier = Modifier
                    .width(600.dp)
                    .height(50.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Panel)
                    .border(
                        if (fieldFocused) 2.dp else 1.dp,
                        if (fieldFocused) Color.White else Color.White.copy(alpha = 0.12f),
                        RoundedCornerShape(6.dp),
                    )
                    .onFocusChanged { fieldFocused = it.isFocused }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                decorationBox = { field ->
                    Box {
                        if (query.isBlank()) Text("Movie or show title", color = TextSecondary, fontSize = 18.sp)
                        field()
                    }
                },
            )
            FocusButton(label = "Search", onClick = { viewModel.search(query) }, enabled = query.trim().length >= 2)
        }

        when (val value = state) {
            LoadState.Loading -> LoadingScreen("Searching", modifier = Modifier.fillMaxWidth().weight(1f))
            is LoadState.Failed -> ErrorScreen(value.message, modifier = Modifier.fillMaxWidth().weight(1f)) {
                viewModel.search(query)
            }
            is LoadState.Ready -> {
                if (value.value.items.isEmpty()) {
                    EmptyMessage(
                        title = if (query.isBlank()) "Find something to watch" else "No matches",
                        detail = if (query.isBlank()) "Use the remote keyboard to search movies and TV shows." else "Try another title.",
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(154.dp),
                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) {
                        items(value.value.items, key = { "${it.type}:${it.id}" }) { item ->
                            MediaCard(item = item, onClick = { onOpen(item) })
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
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AppBackground),
        contentPadding = PaddingValues(bottom = 42.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "header") {
            Box(Modifier.fillMaxWidth().height(420.dp)) {
                AsyncImage(
                    model = details.backdropUrl,
                    contentDescription = details.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.58f)))
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).padding(48.dp).width(700.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(details.title, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        listOfNotNull(
                            details.year?.toString(),
                            details.runtimeMinutes?.let { "$it min" },
                            details.rating.takeIf { it > 0 }?.let { "%.1f rating".format(it) },
                            details.genres.take(3).joinToString(" / ").takeIf(String::isNotBlank),
                        ).joinToString("  |  "),
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 15.sp,
                    )
                    Text(details.overview, color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (details.type == MediaType.MOVIE.wireName) {
                            FocusButton("Play", onMoviePlay)
                        }
                        FocusButton(if (isTracked) "Remove from tracker" else "Add to tracker", onToggleTracked, selected = isTracked)
                    }
                }
            }
        }

        if (details.type == MediaType.TV.wireName) {
            item(key = "season-picker") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Seasons", color = TextPrimary, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 42.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 42.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                LoadState.Loading -> item { LoadingScreen("Loading episodes", modifier = Modifier.fillMaxWidth().height(180.dp)) }
                is LoadState.Failed -> item { EmptyMessage("Episodes unavailable", seasonState.message) }
                is LoadState.Ready -> {
                    item { Text(seasonState.value.name, color = TextPrimary, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 42.dp)) }
                    items(seasonState.value.episodes, key = { it.number }) { episode ->
                        EpisodeRow(episode = episode, onClick = { onEpisodePlay(seasonState.value, episode.number) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: app.ncinq.tv.data.Episode, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 42.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) PanelRaised else Panel)
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        AsyncImage(
            model = episode.stillUrl,
            contentDescription = episode.name,
            modifier = Modifier.size(width = 180.dp, height = 101.dp).clip(RoundedCornerShape(4.dp)).background(PanelRaised),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${episode.number}. ${episode.name}", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(episode.overview, color = TextSecondary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            episode.runtimeMinutes?.let { Text("$it min", color = TextSecondary, fontSize = 12.sp) }
        }
        Text("Play", color = if (focused) BrandBright else TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
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

    Column(
        modifier = Modifier.fillMaxSize().background(AppBackground).padding(top = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Tracker", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 42.dp))
        Row(modifier = Modifier.padding(horizontal = 42.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
            EmptyMessage("Nothing here yet", "Add a title or start watching and it will appear here.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(154.dp),
                contentPadding = PaddingValues(horizontal = 42.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                items(visible, key = { "${it.mediaType.wireName}:${it.mediaId}" }) { trackedItem ->
                    MediaCard(
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

private fun TrackedItem.toMediaItem() = MediaItem(
    id = mediaId,
    type = mediaType.wireName,
    title = title,
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
)

@Composable
private fun LoadingScreen(message: String, modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier.background(AppBackground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(color = BrandBright)
            Text(message, color = TextSecondary, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ErrorScreen(message: String, modifier: Modifier = Modifier.fillMaxSize(), onRetry: () -> Unit) {
    Box(modifier.background(AppBackground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Unable to load", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(message, color = TextSecondary, fontSize = 15.sp)
            FocusButton("Try again", onRetry)
        }
    }
}
