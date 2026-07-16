package app.ncinq.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import app.ncinq.tv.ui.BrandBright
import app.ncinq.tv.ui.CatalogScreen
import app.ncinq.tv.ui.DetailsScreen
import app.ncinq.tv.ui.FocusButton
import app.ncinq.tv.ui.HomeScreen
import app.ncinq.tv.ui.Panel
import app.ncinq.tv.ui.SearchScreen
import app.ncinq.tv.ui.TextPrimary
import app.ncinq.tv.ui.TextSecondary
import app.ncinq.tv.ui.TrackerScreen

private object Routes {
    const val HOME = "home"
    const val MOVIES = "movies"
    const val SHOWS = "shows"
    const val SEARCH = "search"
    const val TRACKER = "tracker"
    const val PLAYER = "player"
    const val DETAILS = "details/{type}/{id}"
}

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
        Column(Modifier.fillMaxSize()) {
            if (route != Routes.PLAYER) {
                TopNavigation(navController = navController, currentRoute = route)
            }
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
                composable(Routes.PLAYER) {
                    PlayerScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                }
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

private fun NavHostController.openDetails(item: MediaItem) {
    navigate("details/${item.type}/${item.id}")
}

@Composable
private fun TopNavigation(navController: NavHostController, currentRoute: String?) {
    val destinations = listOf(
        Routes.HOME to "Home",
        Routes.MOVIES to "Movies",
        Routes.SHOWS to "TV Shows",
        Routes.SEARCH to "Search",
        Routes.TRACKER to "Tracker",
    )
    Row(
        modifier = Modifier.fillMaxWidth().height(72.dp).background(Panel).padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.app_icon),
            contentDescription = "nCinqTV",
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(10.dp))
        destinations.forEach { (route, label) ->
            FocusButton(
                label = label,
                selected = currentRoute == route,
                onClick = {
                    navController.navigate(route) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
        Spacer(Modifier.weight(1f))
        Text("Guest", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun UpdateDialog(info: UpdateInfo, onLater: () -> Unit, onUpdate: () -> Unit) {
    Dialog(onDismissRequest = onLater) {
        Column(
            modifier = Modifier.width(600.dp).clip(RoundedCornerShape(8.dp)).background(Panel).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("nCinqTV ${info.versionName} is ready", color = TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text(
                info.releaseNotes.ifBlank { "A new Android TV update is available." },
                color = TextSecondary,
                fontSize = 15.sp,
                maxLines = 8,
            )
            Text(
                "Android will ask you to confirm the signed APK installation.",
                color = BrandBright,
                fontSize = 13.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FocusButton("Download update", onUpdate, selected = true)
                FocusButton("Later", onLater)
            }
        }
    }
}
