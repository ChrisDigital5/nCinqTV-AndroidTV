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
import androidx.compose.material.icons.rounded.Bookmarks
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import app.ncinq.tv.data.UpdateInfo
import app.ncinq.tv.player.PlayerScreen
import app.ncinq.tv.ui.AppBackground
import app.ncinq.tv.ui.Brand
import app.ncinq.tv.ui.BrandBright
import app.ncinq.tv.ui.CatalogScreen
import app.ncinq.tv.ui.DetailsScreen
import app.ncinq.tv.ui.FocusButton
import app.ncinq.tv.ui.HomeScreen
import app.ncinq.tv.ui.Panel
import app.ncinq.tv.ui.SearchScreen
import app.ncinq.tv.ui.SettingsScreen
import app.ncinq.tv.ui.TextPrimary
import app.ncinq.tv.ui.TextSecondary
import app.ncinq.tv.ui.TrackerScreen

private object Routes {
    const val HOME = "home"
    const val MOVIES = "movies"
    const val SHOWS = "shows"
    const val SEARCH = "search"
    const val TRACKER = "tracker"
    const val SETTINGS = "settings"
    const val PLAYER = "player"
    const val DETAILS = "details/{type}/{id}"
}

private data class Destination(val route: String, val label: String, val icon: ImageVector)

@Composable
fun NCinqTvApp(
    viewModel: AppViewModel,
    onInstallUpdate: (UpdateInfo) -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val update by viewModel.update.collectAsState()

    Box(Modifier.fillMaxSize().background(AppBackground)) {
        if (route == Routes.PLAYER) {
            PlayerScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        } else {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(navController = navController, currentRoute = route)
                AppNavHost(navController = navController, viewModel = viewModel)
            }
        }

        update?.let { info ->
            UpdateDialog(
                info = info,
                onLater = viewModel::dismissUpdate,
                onUpdate = {
                    onInstallUpdate(info)
                    viewModel.dismissUpdate()
                },
            )
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController, viewModel: AppViewModel) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(Routes.HOME) {
            HomeScreen(viewModel, onOpen = { navController.openDetails(it) })
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
        composable(Routes.TRACKER) {
            TrackerScreen(viewModel, onResume = { navController.navigate(Routes.PLAYER) })
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
            )
        }
    }
}

private fun NavHostController.openDetails(item: MediaItem) {
    navigate("details/${item.type}/${item.id}")
}

@Composable
private fun NavigationRail(navController: NavHostController, currentRoute: String?) {
    val destinations = listOf(
        Destination(Routes.HOME, "Home", Icons.Rounded.Home),
        Destination(Routes.MOVIES, "Movies", Icons.Rounded.LocalMovies),
        Destination(Routes.SHOWS, "TV Shows", Icons.Rounded.LiveTv),
        Destination(Routes.SEARCH, "Search", Icons.Rounded.Search),
        Destination(Routes.TRACKER, "Tracker", Icons.Rounded.Bookmarks),
        Destination(Routes.SETTINGS, "Settings", Icons.Rounded.Settings),
    )
    var expanded by remember { mutableStateOf(false) }
    val railWidth by animateDpAsState(if (expanded) 196.dp else 74.dp, label = "railWidth")

    Column(
        modifier = Modifier
            .width(railWidth)
            .fillMaxHeight()
            .background(Color(0xFF0D0E11))
            .border(width = 0.5.dp, color = Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.app_icon),
                contentDescription = "nCinqTV",
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
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
                onFocus = { expanded = it },
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.height(48.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Person, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(23.dp))
            if (expanded) {
                Text("Guest", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(start = 15.dp), maxLines = 1)
            }
        }
    }
}

@Composable
private fun RailItem(
    destination: Destination,
    expanded: Boolean,
    selected: Boolean,
    onFocus: (Boolean) -> Unit,
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
            .onFocusChanged {
                focused = it.isFocused
                onFocus(it.isFocused)
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
private fun UpdateDialog(info: UpdateInfo, onLater: () -> Unit, onUpdate: () -> Unit) {
    Dialog(onDismissRequest = onLater) {
        Column(
            modifier = Modifier.width(570.dp).clip(RoundedCornerShape(8.dp)).background(Panel).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("nCinqTV ${info.versionName} is ready", color = TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text(
                info.releaseNotes.ifBlank { "A new Android TV update is available." },
                color = TextSecondary,
                fontSize = 14.sp,
                maxLines = 7,
            )
            Text("Android will ask you to confirm installation.", color = BrandBright, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FocusButton("Download update", onUpdate, selected = true)
                FocusButton("Later", onLater)
            }
        }
    }
}
