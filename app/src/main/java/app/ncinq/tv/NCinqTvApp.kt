package app.ncinq.tv

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.LocalMovies
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Text
import app.ncinq.tv.data.MediaItem
import app.ncinq.tv.data.MediaType
import app.ncinq.tv.data.ProfileRepository
import app.ncinq.tv.data.ViewerProfile
import app.ncinq.tv.data.UpdateInfo
import app.ncinq.tv.data.UpdateInstallState
import app.ncinq.tv.player.PlayerScreen
import app.ncinq.tv.ui.AppBackground
import app.ncinq.tv.ui.Brand
import app.ncinq.tv.ui.BrandBright
import app.ncinq.tv.ui.CatalogScreen
import app.ncinq.tv.ui.DetailsScreen
import app.ncinq.tv.ui.FocusButton
import app.ncinq.tv.ui.FavoritesScreen
import app.ncinq.tv.ui.HistoryScreen
import app.ncinq.tv.ui.HomeScreen
import app.ncinq.tv.ui.NetworkCatalogScreen
import app.ncinq.tv.ui.ProfileSelector
import app.ncinq.tv.ui.profileAvatarIcon
import app.ncinq.tv.ui.Panel
import app.ncinq.tv.ui.OverscanVertical
import app.ncinq.tv.ui.SearchScreen
import app.ncinq.tv.ui.SettingsScreen
import app.ncinq.tv.ui.TextPrimary
import app.ncinq.tv.ui.TextSecondary

private object Routes {
    const val HOME = "home"
    const val MOVIES = "movies"
    const val SHOWS = "shows"
    const val SEARCH = "search"
    const val HISTORY = "history"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
    const val PLAYER = "player"
    const val DETAILS = "details/{type}/{id}"
}

private data class Destination(val route: String, val label: String, val icon: ImageVector)

private class NavigationRailFocusGate {
    var allowEntry by mutableStateOf(false)
    var railHasFocus by mutableStateOf(false)
    val acceptsFocus: Boolean get() = allowEntry || railHasFocus
}

@Composable
fun NCinqTvApp(
    viewModel: AppViewModel,
    onInstallUpdate: (UpdateInfo) -> Unit,
) {
    val context = LocalContext.current
    val profileRepository = remember { ProfileRepository(context) }
    var profiles by remember { mutableStateOf(profileRepository.profiles()) }
    val lastProfileId = remember { profileRepository.lastProfileId() }
    var activeProfile by remember { mutableStateOf<ViewerProfile?>(null) }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val update by viewModel.update.collectAsState()
    val installState by viewModel.updateInstallState.collectAsState()
    val railFocusGate = remember { NavigationRailFocusGate() }

    Box(Modifier.fillMaxSize().background(AppBackground)) {
        if (activeProfile == null) {
            ProfileSelector(
                profiles = profiles,
                lastProfileId = lastProfileId,
                onSelect = { profile ->
                    profileRepository.select(profile)
                    viewModel.setActiveProfile(profile)
                    activeProfile = profile
                },
                onSave = { profile -> profiles = profileRepository.save(profile) },
                onDelete = { profile -> profiles = profileRepository.delete(profile.id) },
                onMove = { profile, offset -> profiles = profileRepository.move(profile.id, offset) },
            )
        } else if (route == Routes.PLAYER) {
            PlayerScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        } else {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(
                    navController = navController,
                    currentRoute = route,
                    focusGate = railFocusGate,
                    activeProfile = activeProfile ?: profiles.first(),
                    onSwitchProfile = { activeProfile = null },
                )
                AppNavHost(navController = navController, viewModel = viewModel, railFocusGate = railFocusGate)
            }
        }

        update?.let { info ->
            UpdateDialog(
                info = info,
                installState = installState,
                onLater = viewModel::dismissUpdate,
                onUpdate = {
                    onInstallUpdate(info)
                },
            )
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    viewModel: AppViewModel,
    railFocusGate: NavigationRailFocusGate,
) {
    val focusManager = LocalFocusManager.current
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val direction = when (event.key) {
                    Key.DirectionUp -> FocusDirection.Up
                    Key.DirectionDown -> FocusDirection.Down
                    Key.DirectionLeft -> FocusDirection.Left
                    else -> return@onPreviewKeyEvent false
                }
                if (event.type == KeyEventType.KeyDown) {
                    // The rail is not a focus candidate during loading, clicks, or vertical
                    // traversal. It is exposed only for an explicit Left move from content.
                    railFocusGate.allowEntry = direction == FocusDirection.Left
                    try {
                        focusManager.moveFocus(direction)
                    } finally {
                        railFocusGate.allowEntry = false
                    }
                }
                true
            },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel,
                onOpen = { navController.openDetails(it) },
                onPlay = { item ->
                    viewModel.playFeatured(item) { navController.navigate(Routes.PLAYER) }
                },
                onNetwork = { navController.navigate("network/${it.id}") },
            )
        }
        composable(Routes.MOVIES) {
            CatalogScreen(viewModel, MediaType.MOVIE, onOpen = { navController.openDetails(it) })
        }
        composable(Routes.SHOWS) {
            CatalogScreen(viewModel, MediaType.TV, onOpen = { navController.openDetails(it) })
        }
        composable(Routes.SEARCH) {
            SearchScreen(viewModel, onOpen = { navController.openDetails(it) })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(viewModel, onResume = { navController.navigate(Routes.PLAYER) })
        }
        composable(Routes.FAVORITES) {
            FavoritesScreen(viewModel, onOpen = { navController.openDetails(it) })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(viewModel)
        }
        composable(Routes.PLAYER) {
            // The app shell replaces this destination with the fullscreen player.
        }
        composable(Routes.DETAILS) { entry ->
            val type = MediaType.fromWire(entry.arguments?.getString("type") ?: "movie")
            val id = entry.arguments?.getString("id")?.toIntOrNull() ?: return@composable
            DetailsScreen(
                viewModel = viewModel,
                mediaType = type,
                mediaId = id,
                onPlay = { navController.navigate(Routes.PLAYER) },
                onOpenRelated = { navController.openDetails(it) },
            )
        }
        composable("network/{id}") { entry ->
            val networkId = entry.arguments?.getString("id")?.toIntOrNull() ?: return@composable
            NetworkCatalogScreen(viewModel, networkId, onOpen = { navController.openDetails(it) })
        }
    }
}

private fun NavHostController.openDetails(item: MediaItem) {
    navigate("details/${item.type}/${item.id}")
}

@Composable
private fun NavigationRail(
    navController: NavHostController,
    currentRoute: String?,
    focusGate: NavigationRailFocusGate,
    activeProfile: ViewerProfile,
    onSwitchProfile: () -> Unit,
) {
    val destinations = listOf(
        Destination(Routes.HOME, "Home", Icons.Rounded.Home),
        Destination(Routes.MOVIES, "Movies", Icons.Rounded.LocalMovies),
        Destination(Routes.SHOWS, "TV Shows", Icons.Rounded.LiveTv),
        Destination(Routes.SEARCH, "Search", Icons.Rounded.Search),
        Destination(Routes.HISTORY, "History", Icons.Rounded.History),
        Destination(Routes.FAVORITES, "Favorites", Icons.Rounded.Favorite),
        Destination(Routes.SETTINGS, "Settings", Icons.Rounded.Settings),
    ).filterNot { activeProfile.kidsMode && it.route == Routes.SEARCH }
    var expanded by remember { mutableStateOf(false) }
    val railWidth by animateDpAsState(if (expanded) 238.dp else 112.dp, label = "railWidth")

    Column(
        modifier = Modifier
            .width(railWidth)
            .fillMaxHeight()
            .background(Color(0xFF0D0E11))
            .border(width = 0.5.dp, color = Color.White.copy(alpha = 0.08f))
            .onFocusChanged {
                expanded = it.hasFocus
                focusGate.railHasFocus = it.hasFocus
            }
            .padding(start = 48.dp, end = 10.dp, top = OverscanVertical, bottom = OverscanVertical),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.app_icon),
                contentDescription = "nCinqTV",
                modifier = Modifier.size(width = 54.dp, height = 34.dp).clip(RoundedCornerShape(5.dp)),
            )
            if (expanded) {
                Text(
                    "nCinqTV",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 12.dp),
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.size(10.dp))
        destinations.forEach { destination ->
            RailItem(
                destination = destination,
                expanded = expanded,
                selected = currentRoute == destination.route,
                canFocus = { focusGate.acceptsFocus },
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(Routes.HOME) { saveState = false }
                        launchSingleTop = true
                        restoreState = false
                    }
                },
            )
        }
        Spacer(Modifier.weight(1f))
        RailItem(
            destination = Destination("profiles", activeProfile.name, profileAvatarIcon(activeProfile.avatar)),
            expanded = expanded,
            selected = false,
            canFocus = { focusGate.acceptsFocus },
            onClick = onSwitchProfile,
        )
    }
}

@Composable
private fun RailItem(
    destination: Destination,
    expanded: Boolean,
    selected: Boolean,
    canFocus: () -> Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> Color.White
        selected -> Brand
        else -> Color.Transparent
    }
    val contentColor = if (focused) Color.Black else if (selected) Color.White else TextSecondary
    Row(
        modifier = Modifier
            .height(48.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .focusProperties { this.canFocus = canFocus() }
            .onFocusChanged {
                focused = it.isFocused
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(destination.icon, contentDescription = destination.label, tint = contentColor, modifier = Modifier.size(23.dp))
        if (expanded) {
            Text(
                destination.label,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.padding(start = 15.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun UpdateDialog(info: UpdateInfo, installState: UpdateInstallState, onLater: () -> Unit, onUpdate: () -> Unit) {
    Dialog(onDismissRequest = onLater) {
        Column(
            modifier = Modifier.width(570.dp).clip(RoundedCornerShape(8.dp)).background(Panel).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("nCinqTV ${info.versionName} is ready", color = TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text(
                info.releaseNotes.toDisplayChangelog(),
                color = TextSecondary,
                fontSize = 14.sp,
                maxLines = 7,
            )
            Text(
                if (installState.active) installState.message else "Android will ask you to confirm installation.",
                color = BrandBright,
                fontSize = 13.sp,
            )
            if (installState.active) {
                Box(Modifier.fillMaxWidth().height(7.dp).background(Color.White.copy(alpha = 0.15f))) {
                    Box(Modifier.fillMaxWidth(installState.progress / 100f).height(7.dp).background(BrandBright))
                }
                Text("${installState.progress}%", color = TextSecondary, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FocusButton(if (installState.active) "Downloading" else "Download update", onUpdate, selected = true, enabled = !installState.active)
                FocusButton("Later", onLater)
            }
        }
    }
}

private fun String.toDisplayChangelog(): String {
    val readableNotes = lineSequence()
        .map { it.trim() }
        .filterNot { line ->
            line.contains("Full Changelog", ignoreCase = true) &&
                line.contains("/compare/", ignoreCase = true)
        }
        .joinToString("\n")
        .replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
        .replace("**", "")
        .replace(Regex("\\[([^]]+)]\\(https?://[^)]+\\)"), "$1")
        .trim()
    return readableNotes.ifBlank { "A new Android TV update is available." }
}
