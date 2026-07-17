package app.ncinq.tv.player

import android.annotation.SuppressLint
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import app.ncinq.tv.AppViewModel
import app.ncinq.tv.data.MediaType
import app.ncinq.tv.data.PlaybackRequest
import app.ncinq.tv.data.StreamResult
import app.ncinq.tv.ui.AppBackground
import app.ncinq.tv.ui.BrandBright
import app.ncinq.tv.ui.FocusButton
import app.ncinq.tv.ui.Panel
import app.ncinq.tv.ui.TextPrimary
import app.ncinq.tv.ui.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

private const val SEEK_INCREMENT_MS = 10_000L
private const val CONTROLS_TIMEOUT_MS = 4_500L
private const val MAX_AUTOMATIC_RETRIES = 3

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
    val httpDataSourceFactory = remember {
        DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(45_000)
            .setUserAgent("nCinqTV/1.1.0 (Android TV; Media3)")
    }
    val mediaSourceFactory = remember {
        DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpDataSourceFactory))
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(6))
    }
    val player = remember {
        ExoPlayer.Builder(context)
            .setRenderersFactory(
                DefaultRenderersFactory(context)
                    .setEnableDecoderFallback(true),
            )
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(20_000, 90_000, 2_500, 5_000)
                    .build(),
            )
            .build()
            .apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }
    val mediaSession = remember(player) { MediaSession.Builder(context, player).build() }
    val captionResolver = remember { CaptionResolver() }
    val rootFocusRequester = remember { FocusRequester() }
    val retryFocusRequester = remember { FocusRequester() }
    val controlFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var stream by remember { mutableStateOf<StreamResult?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var automaticRetries by remember { mutableIntStateOf(0) }
    var prefetched by remember { mutableStateOf(false) }
    var nextRequest by remember { mutableStateOf<PlaybackRequest?>(null) }
    var transitionInFlight by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var retryPositionMs by remember { mutableLongStateOf(0L) }
    var captionsEnabled by remember { mutableStateOf(true) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }
    var seekFeedbackEpoch by remember { mutableIntStateOf(0) }
    var sourceMismatch by remember { mutableStateOf(false) }
    var alternateUrl by remember { mutableStateOf<String?>(null) }

    fun showControls() {
        controlsVisible = true
        lastInteractionMs = SystemClock.elapsedRealtime()
    }

    fun saveProgress(completed: Boolean = false) {
        val duration = player.duration.takeIf { it > 0 } ?: 0L
        val position = player.currentPosition.coerceAtLeast(0L)
        viewModel.saveProgress(activeRequest, position, duration, completed)
    }

    fun togglePlayback() {
        if (player.isPlaying) player.pause() else player.play()
        showControls()
    }

    fun seekBy(offsetMs: Long) {
        val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + offsetMs).coerceIn(0L, duration))
        val seconds = (kotlin.math.abs(offsetMs) / 1_000L).coerceAtLeast(1L)
        seekFeedback = if (offsetMs < 0) "-$seconds" else "+$seconds"
        seekFeedbackEpoch += 1
        showControls()
    }

    fun toggleCaptions() {
        captionsEnabled = !captionsEnabled
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !captionsEnabled)
            .build()
        showControls()
    }

    fun retryPlayback() {
        automaticRetries = 0
        retryPositionMs = player.currentPosition.coerceAtLeast(0L)
        reloadKey += 1
    }

    fun switchEpisode(next: Boolean) {
        if (transitionInFlight || activeRequest.mediaType != MediaType.TV) return
        transitionInFlight = true
        saveProgress()
        scope.launch {
            val target = if (next) viewModel.nextPlayback(activeRequest) else viewModel.previousPlayback(activeRequest)
            if (target != null) viewModel.advanceTo(target) else transitionInFlight = false
        }
    }

    BackHandler {
        when {
            alternateUrl != null -> onBack()
            error != null -> {
                saveProgress()
                onBack()
            }
            controlsVisible -> controlsVisible = false
            else -> {
                saveProgress()
                onBack()
            }
        }
    }

    DisposableEffect(player, mediaSession) {
        onDispose {
            mediaSession.release()
            player.release()
        }
    }

    DisposableEffect(player, activeRequest.key) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        val wasPreparing = loading
                        loading = false
                        durationMs = player.duration.coerceAtLeast(0L)
                        if (hasRuntimeMismatch(activeRequest.expectedRuntimeMinutes, durationMs)) {
                            sourceMismatch = true
                            error = "This server returned the wrong episode (${formatTime(durationMs)} instead of about ${activeRequest.expectedRuntimeMinutes} min)."
                            player.pause()
                        }
                        if (wasPreparing) showControls()
                    }
                    Player.STATE_ENDED -> {
                        if (transitionInFlight) return
                        transitionInFlight = true
                        scope.launch {
                            val next = viewModel.nextPlayback(activeRequest)
                            viewModel.completeAndAdvance(activeRequest, player.duration.coerceAtLeast(0L), next)
                            if (next == null) {
                                finished = true
                                transitionInFlight = false
                                showControls()
                            }
                        }
                    }
                }
            }

            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
            }

            override fun onPlayerError(playbackError: PlaybackException) {
                val statusCode = playbackError.httpStatusCode()
                val recoverable = playbackError.isRecoverableSourceError(statusCode)
                if (recoverable && automaticRetries < MAX_AUTOMATIC_RETRIES) {
                    automaticRetries += 1
                    retryPositionMs = player.currentPosition.coerceAtLeast(0L)
                    loading = true
                    error = null
                    Log.w(
                        "NCinqPlayer",
                        "Recovering source at ${retryPositionMs}ms, HTTP $statusCode " +
                            "(attempt $automaticRetries)",
                        playbackError,
                    )
                    scope.launch {
                        delay(automaticRetries * 1_000L)
                        reloadKey += 1
                    }
                    return
                }
                loading = false
                controlsVisible = true
                error = playbackError.friendlyMessage(statusCode)
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
        nextRequest = null
        transitionInFlight = false
        sourceMismatch = false
        alternateUrl = null
        stream = null
        player.stop()
        player.clearMediaItems()

        runCatching {
            viewModel.resolveStream(activeRequest, force = reloadKey > 0)
        }.onSuccess { resolved ->
            stream = resolved
            httpDataSourceFactory.setDefaultRequestProperties(resolved.headers)
            val captions = resolved.captions.mapNotNull { caption ->
                runCatching { captionResolver.resolve(context, caption) }.getOrNull()
            }
            player.setMediaItem(resolved.toMediaItem(captions))
            val savedPosition = viewModel.resumePosition(activeRequest)
            val resumeAt = max(savedPosition, retryPositionMs)
            if (resumeAt > 30_000) player.seekTo(resumeAt)
            retryPositionMs = 0L
            player.prepare()
            player.play()
        }.onFailure { failure ->
            loading = false
            controlsVisible = true
            error = failure.message ?: "No direct stream is currently available."
        }
    }

    LaunchedEffect(activeRequest.key) {
        automaticRetries = 0
        controlsVisible = true
        rootFocusRequester.requestFocus()
        while (isActive) {
            delay(500)
            positionMs = player.contentPosition.coerceAtLeast(0L)
            durationMs = player.contentDuration.coerceAtLeast(0L)
            if (positionMs > 0 && player.playbackState != Player.STATE_ENDED && positionMs % 5_000 < 600) {
                viewModel.saveProgress(activeRequest, positionMs, durationMs)
            }
            if (controlsVisible && player.playWhenReady && error == null && !loading &&
                SystemClock.elapsedRealtime() - lastInteractionMs >= CONTROLS_TIMEOUT_MS
            ) {
                controlsVisible = false
            }
            if (!prefetched && activeRequest.mediaType == MediaType.TV && durationMs > 0 &&
                positionMs >= (durationMs * 0.72).toLong()
            ) {
                prefetched = true
                nextRequest = viewModel.nextPlayback(activeRequest)?.also { next ->
                    runCatching { viewModel.resolveStream(next) }
                }
            }
        }
    }

    LaunchedEffect(seekFeedbackEpoch) {
        if (seekFeedbackEpoch > 0) {
            delay(850)
            seekFeedback = null
        }
    }

    LaunchedEffect(error) {
        if (error != null) retryFocusRequester.requestFocus() else rootFocusRequester.requestFocus()
    }

    LaunchedEffect(controlsVisible, error, loading) {
        if (controlsVisible && error == null && !loading) {
            delay(250)
            controlFocusRequester.requestFocus()
        }
        else if (!controlsVisible && error == null) rootFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (alternateUrl != null) return@onPreviewKeyEvent false
                val native = event.nativeKeyEvent
                if (native.action != KeyEvent.ACTION_DOWN || native.repeatCount > 0) return@onPreviewKeyEvent false
                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_SPACE,
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (controlsVisible && error == null) false else {
                            if (error != null) retryPlayback() else togglePlayback()
                            true
                        }
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        player.play()
                        showControls()
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        player.pause()
                        showControls()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        if (controlsVisible && native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) false else {
                            if (error == null) seekBy(-SEEK_INCREMENT_MS)
                            true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        if (controlsVisible && native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) false else {
                            if (error == null) seekBy(SEEK_INCREMENT_MS)
                            true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (controlsVisible) false else {
                            showControls()
                            true
                        }
                    }
                    KeyEvent.KEYCODE_CAPTIONS,
                    KeyEvent.KEYCODE_MENU -> {
                        if (stream?.captions?.isNotEmpty() == true) toggleCaptions()
                        true
                    }
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    keepScreenOn = true
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        alternateUrl?.let { FallbackWebPlayer(it) }

        if (alternateUrl == null && controlsVisible && error == null && !loading) {
            PlayerChrome(
                request = activeRequest,
                provider = stream?.provider,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                captionsAvailable = stream?.captions?.isNotEmpty() == true,
                captionsEnabled = captionsEnabled,
                playFocusRequester = controlFocusRequester,
                onPlayPause = ::togglePlayback,
                onSeek = ::seekBy,
                onCaptions = ::toggleCaptions,
                onPreviousEpisode = { switchEpisode(next = false) },
                onNextEpisode = { switchEpisode(next = true) },
            )
        }

        val upcoming = nextRequest
        if (!controlsVisible && error == null && !loading && upcoming != null && durationMs > positionMs && durationMs - positionMs <= 30_000L) {
            Column(
                Modifier.align(Alignment.BottomEnd).padding(34.dp).width(330.dp)
                    .background(Color.Black.copy(alpha = 0.9f), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text("Up next in ${((durationMs - positionMs) / 1_000L).coerceAtLeast(1)}s", color = BrandBright, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("S${upcoming.season} E${upcoming.episode}  ${upcoming.episodeTitle.orEmpty()}", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                FocusButton("Play next now", onClick = { switchEpisode(next = true) }, selected = true)
            }
        }

        seekFeedback?.let { amount ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.72f), androidx.compose.foundation.shape.CircleShape)
                    .size(82.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(amount, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularProgressIndicator(color = BrandBright)
                    Text(
                        if (transitionInFlight) "Loading next episode" else if (automaticRetries > 0) "Restoring playback" else "Preparing stream",
                        color = TextPrimary,
                        fontSize = 16.sp,
                    )
                }
            }
        }

        error?.let { message ->
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Playback interrupted", color = TextPrimary, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Text(message, color = TextSecondary, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (sourceMismatch) {
                            FocusButton(
                                "Use alternate server",
                                onClick = {
                                    error = null
                                    controlsVisible = false
                                    alternateUrl = activeRequest.alternateEmbedUrl()
                                },
                                modifier = Modifier.focusRequester(retryFocusRequester),
                                selected = true,
                            )
                        }
                        FocusButton(
                            "Try again",
                            onClick = ::retryPlayback,
                            modifier = if (sourceMismatch) Modifier else Modifier.focusRequester(retryFocusRequester),
                            selected = !sourceMismatch,
                        )
                        FocusButton("Back to details", onClick = onBack)
                    }
                }
            }
        }

        if (finished) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.84f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Finished", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("You reached the end of this title.", color = TextSecondary, fontSize = 15.sp)
                    FocusButton("Back to details", onClick = onBack, selected = true)
                }
            }
        }
    }
}

@Composable
private fun PlayerChrome(
    request: PlaybackRequest,
    provider: String?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    captionsAvailable: Boolean,
    captionsEnabled: Boolean,
    playFocusRequester: FocusRequester,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onCaptions: () -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
        Column(
            modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.82f)).padding(horizontal = 42.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(request.title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    val subtitle = if (request.mediaType == MediaType.TV) {
                        "S${request.season} E${request.episode}  ${request.episodeTitle.orEmpty()}"
                    } else {
                        provider?.replaceFirstChar { it.uppercase() } ?: "Movie"
                    }
                    Text(subtitle, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                }
                Text("${formatTime(positionMs)}  /  ${formatTime(durationMs)}", color = TextSecondary, fontSize = 12.sp)
            }
            ScrubRail(positionMs, durationMs, onSeek)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (request.mediaType == MediaType.TV) {
                    PlayerControl(Icons.Rounded.SkipPrevious, "Previous", onPreviousEpisode)
                    Spacer(Modifier.width(24.dp))
                }
                PlayerControl(Icons.Rounded.Replay, "10 sec", { onSeek(-SEEK_INCREMENT_MS) })
                Spacer(Modifier.width(24.dp))
                PlayerControl(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (isPlaying) "Pause" else "Play",
                    onPlayPause,
                    primary = true,
                    modifier = Modifier.focusRequester(playFocusRequester),
                )
                Spacer(Modifier.width(24.dp))
                PlayerControl(Icons.Rounded.FastForward, "10 sec", { onSeek(SEEK_INCREMENT_MS) })
                if (request.mediaType == MediaType.TV) {
                    Spacer(Modifier.width(24.dp))
                    PlayerControl(Icons.Rounded.SkipNext, "Next", onNextEpisode)
                }
                if (captionsAvailable) {
                    Spacer(Modifier.width(40.dp))
                    PlayerControl(
                        Icons.Rounded.ClosedCaption,
                        if (captionsEnabled) "Captions on" else "Captions off",
                        onCaptions,
                        active = captionsEnabled,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerControl(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    active: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (primary) 54.dp else 42.dp)
                .border(if (focused) 3.dp else 0.dp, Color.White, androidx.compose.foundation.shape.CircleShape)
                .background(
                    if (focused) Color.White else if (primary || active) BrandBright else Panel.copy(alpha = 0.9f),
                    androidx.compose.foundation.shape.CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = if (focused || primary || active) Color.Black else Color.White, modifier = Modifier.size(25.dp))
        }
        Text(label, color = if (focused) Color.White else TextSecondary, fontSize = 11.sp, fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun ScrubRail(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(22.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { onSeek(-30_000L); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { onSeek(30_000L); true }
                    else -> false
                }
            }
            .background(Color.Transparent),
        contentAlignment = Alignment.CenterStart,
    ) {
        val fraction = playbackFraction(positionMs, durationMs)
        val railHeight = if (focused) 9.dp else 5.dp
        val thumbSize = if (focused) 14.dp else 9.dp
        Box(Modifier.fillMaxWidth().height(railHeight).background(Color.White.copy(alpha = 0.28f))) {
            Box(Modifier.fillMaxWidth(fraction).height(railHeight).background(BrandBright))
        }
        Box(
            Modifier
                .offset(x = (maxWidth - thumbSize) * fraction)
                .size(thumbSize)
                .background(Color.White, androidx.compose.foundation.shape.CircleShape)
                .border(
                    if (focused) 2.dp else 0.dp,
                    BrandBright,
                    androidx.compose.foundation.shape.CircleShape,
                ),
        )
    }
}

internal fun playbackFraction(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0) return 0f
    return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

internal fun hasRuntimeMismatch(expectedMinutes: Int?, actualDurationMs: Long): Boolean {
    if (expectedMinutes == null || expectedMinutes < 30 || actualDurationMs <= 0) return false
    return actualDurationMs < expectedMinutes * 60_000L * 0.65
}

private fun PlaybackRequest.alternateEmbedUrl(): String = when (mediaType) {
    MediaType.MOVIE -> "https://vidlink.pro/movie/$mediaId?autoplay=true"
    MediaType.TV -> "https://vidlink.pro/tv/$mediaId/${season ?: 1}/${episode ?: 1}?autoplay=true"
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun FallbackWebPlayer(url: String) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                isFocusable = true
                isFocusableInTouchMode = true
                loadUrl(url)
                requestFocus()
                webView = this
            }
        },
        update = { if (it.url != url) it.loadUrl(url) },
        modifier = Modifier.fillMaxSize(),
    )
    DisposableEffect(url) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
        }
    }
}

internal fun formatTime(timeMs: Long): String {
    val totalSeconds = (timeMs.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

internal fun PlaybackException.isRecoverableSourceError(statusCode: Int? = httpStatusCode()): Boolean {
    return isRecoverableSourceErrorCode(errorCode, statusCode)
}

internal fun isRecoverableSourceErrorCode(errorCode: Int, statusCode: Int? = null): Boolean {
    return statusCode in setOf(408, 425, 429, 500, 502, 503, 504) || errorCode in setOf(
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
    )
}

private fun PlaybackException.friendlyMessage(statusCode: Int?): String = when {
    statusCode != null -> "The video source stopped responding. Your position is saved."
    errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED -> "This video format is not supported by this TV."
    else -> "The video source was interrupted. Your position is saved."
}

internal fun Throwable.httpStatusCode(): Int? {
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
        .setUri(Uri.parse(url))
        .setMimeType(if (type.equals("hls", ignoreCase = true)) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4)
        .setSubtitleConfigurations(subtitles)
        .build()
}
