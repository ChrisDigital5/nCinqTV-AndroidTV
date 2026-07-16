package app.ncinq.tv.player

import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import app.ncinq.tv.AppViewModel
import app.ncinq.tv.data.MediaProxy
import app.ncinq.tv.data.MediaType
import app.ncinq.tv.data.PlaybackRequest
import app.ncinq.tv.data.StreamResult
import app.ncinq.tv.ui.AppBackground
import app.ncinq.tv.ui.BrandBright
import app.ncinq.tv.ui.FocusButton
import app.ncinq.tv.ui.TextPrimary
import app.ncinq.tv.ui.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val request by viewModel.activePlayback.collectAsState()
    val activeRequest = request
    if (activeRequest == null) {
        Box(Modifier.fillMaxSize().background(AppBackground), contentAlignment = Alignment.Center) {
            FocusButton("Back", onBack)
        }
        return
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }
    val captionResolver = remember { CaptionResolver() }
    val retryFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var stream by remember { mutableStateOf<StreamResult?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var automaticRetries by remember { mutableIntStateOf(0) }
    var prefetched by remember { mutableStateOf(false) }
    var transitionInFlight by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    fun saveProgress(completed: Boolean = false) {
        val duration = player.duration.takeIf { it > 0 } ?: 0L
        val position = player.currentPosition.coerceAtLeast(0L)
        viewModel.saveProgress(activeRequest, position, duration, completed)
    }

    BackHandler {
        saveProgress()
        onBack()
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    DisposableEffect(player, activeRequest.key) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> loading = false
                    Player.STATE_ENDED -> {
                        if (transitionInFlight) return
                        transitionInFlight = true
                        saveProgress(completed = true)
                        scope.launch {
                            val next = viewModel.nextPlayback(activeRequest)
                            if (next != null) {
                                viewModel.advanceTo(next)
                            } else {
                                finished = true
                                transitionInFlight = false
                            }
                        }
                    }
                }
            }

            override fun onPlayerError(playbackError: PlaybackException) {
                val statusCode = playbackError.httpStatusCode()
                if (statusCode in setOf(502, 503, 504) && automaticRetries < 2) {
                    automaticRetries += 1
                    loading = true
                    error = null
                    Log.w("NCinqPlayer", "HTTP $statusCode; refreshing stream (attempt $automaticRetries)")
                    scope.launch {
                        delay(automaticRetries * 700L)
                        reloadKey += 1
                    }
                    return
                }
                loading = false
                error = playbackError.message ?: "This stream could not be played."
                Log.e("NCinqPlayer", "Playback failed", playbackError)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            if (player.playbackState != Player.STATE_ENDED) saveProgress()
        }
    }

    LaunchedEffect(activeRequest.key, reloadKey) {
        loading = true
        error = null
        finished = false
        prefetched = false
        transitionInFlight = false
        stream = null
        player.stop()
        player.clearMediaItems()

        runCatching {
            viewModel.resolveStream(activeRequest, force = reloadKey > 0)
        }.onSuccess { resolved ->
            stream = resolved
            val captions = resolved.captions.mapNotNull { caption ->
                runCatching { captionResolver.resolve(context, caption) }.getOrNull()
            }
            val mediaItem = resolved.toMediaItem(captions)
            player.setMediaItem(mediaItem)
            val resumeAt = viewModel.resumePosition(activeRequest)
            if (resumeAt > 30_000) player.seekTo(resumeAt)
            player.prepare()
            player.play()
        }.onFailure { failure ->
            loading = false
            error = failure.message ?: "No direct stream is currently available."
        }
    }

    LaunchedEffect(activeRequest.key) {
        automaticRetries = 0
        while (isActive) {
            delay(5_000)
            val duration = player.duration.takeIf { it > 0 } ?: continue
            val position = player.currentPosition.coerceAtLeast(0L)
            if (position > 0 && player.playbackState != Player.STATE_ENDED) {
                viewModel.saveProgress(activeRequest, position, duration)
            }
            if (!prefetched && activeRequest.mediaType == MediaType.TV && position >= (duration * 0.72).toLong()) {
                prefetched = true
                viewModel.prefetchNext(activeRequest)
            }
        }
    }

    LaunchedEffect(error) {
        if (error != null) retryFocusRequester.requestFocus()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = true
                    controllerAutoShow = true
                    controllerHideOnTouch = true
                    setShowSubtitleButton(true)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    keepScreenOn = true
                }
            },
            update = { playerView ->
                playerView.player = player
                playerView.setShowSubtitleButton(stream?.captions?.isNotEmpty() == true)
            },
            modifier = Modifier.fillMaxSize(),
        )

        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopStart).background(Color.Black.copy(alpha = 0.62f)).padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FocusButton("Back", onClick = {
                saveProgress()
                onBack()
            })
            Column {
                Text(activeRequest.title, color = TextPrimary, fontSize = 19.sp)
                val subtitle = if (activeRequest.mediaType == MediaType.TV) {
                    "S${activeRequest.season} E${activeRequest.episode}  ${activeRequest.episodeTitle.orEmpty()}"
                } else {
                    stream?.provider?.let { "Playing with $it" } ?: "Movie"
                }
                Text(subtitle, color = TextSecondary, fontSize = 13.sp)
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.68f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularProgressIndicator(color = BrandBright)
                    Text(
                        if (transitionInFlight) "Loading next episode" else "Preparing direct stream",
                        color = TextPrimary,
                        fontSize = 16.sp,
                    )
                }
            }
        }

        error?.let { message ->
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.86f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Playback unavailable", color = TextPrimary, fontSize = 24.sp)
                    Text(message, color = TextSecondary, fontSize = 15.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FocusButton(
                            "Try again",
                            onClick = {
                                automaticRetries = 0
                                reloadKey += 1
                            },
                            modifier = Modifier.focusRequester(retryFocusRequester),
                            selected = true,
                        )
                        FocusButton("Back", onClick = onBack)
                    }
                }
            }
        }

        if (finished) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Finished", color = TextPrimary, fontSize = 26.sp)
                    Text("You reached the end of this title.", color = TextSecondary, fontSize = 15.sp)
                    FocusButton("Back to details", onClick = onBack, selected = true)
                }
            }
        }
    }
}

private fun Throwable.httpStatusCode(): Int? {
    var current: Throwable? = this
    while (current != null) {
        if (current is HttpDataSource.InvalidResponseCodeException) return current.responseCode
        current = current.cause
    }
    return null
}

@OptIn(UnstableApi::class)
private fun StreamResult.toMediaItem(resolvedCaptions: List<ResolvedCaption>): MediaItem {
    val subtitles = resolvedCaptions.mapIndexed { index, resolved ->
        MediaItem.SubtitleConfiguration.Builder(resolved.uri)
            .setMimeType(MimeTypes.TEXT_VTT)
            .setLanguage(resolved.caption.language)
            .setLabel(resolved.caption.language)
            .setSelectionFlags(if (index == 0) C.SELECTION_FLAG_DEFAULT else 0)
            .build()
    }
    return MediaItem.Builder()
        .setUri(Uri.parse(MediaProxy.streamUrl(this)))
        .setMimeType(if (type.equals("hls", ignoreCase = true)) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4)
        .setSubtitleConfigurations(subtitles)
        .build()
}
