package app.ncinq.tv.player

import android.annotation.SuppressLint
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
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
import app.ncinq.tv.data.MediaProxy
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
import retrofit2.HttpException
import java.io.IOException
import kotlin.math.max

private const val SEEK_INCREMENT_MS = 10_000L
private const val CONTROLS_TIMEOUT_MS = 4_500L
private const val MAX_AUTOMATIC_RETRIES = 3
private const val MAX_RESOLVER_ATTEMPTS = 2
private const val ALTERNATE_SOURCE_TIMEOUT_MS = 15_000L

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
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setPreferredAudioLanguages("en", "eng")
                    .build()
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }
    val mediaSession = remember(player) { MediaSession.Builder(context, player).build() }
    val captionResolver = remember { CaptionResolver() }
    val rootFocusRequester = remember { FocusRequester() }
    val retryFocusRequester = remember { FocusRequester() }
    val controlFocusRequester = remember { FocusRequester() }
    val captionFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var stream by remember { mutableStateOf<StreamResult?>(null) }
    var captionTracks by remember { mutableStateOf<List<ResolvedCaption>>(emptyList()) }
    var selectedCaptionId by remember { mutableStateOf<String?>(null) }
    var subtitleMenuVisible by remember { mutableStateOf(false) }
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
    var playbackFailure by remember { mutableStateOf<PlaybackFailureKind?>(null) }
    var alternateSources by remember { mutableStateOf<List<AlternateStreamSource>>(emptyList()) }
    var alternateSourceIndex by remember { mutableIntStateOf(0) }
    var alternateWebView by remember { mutableStateOf<WebView?>(null) }
    var alternateResumeApplied by remember { mutableStateOf(false) }
    var alternateCaptionsAvailable by remember { mutableStateOf(false) }
    var alternateSourceHealthy by remember { mutableStateOf(false) }
    var alternateSwitchInFlight by remember { mutableStateOf(false) }
    val activeAlternateSource = alternateSources.getOrNull(alternateSourceIndex)
    val alternateUrl = activeAlternateSource?.url

    fun showControls() {
        controlsVisible = true
        lastInteractionMs = SystemClock.elapsedRealtime()
    }

    fun saveProgress(completed: Boolean = false) {
        val duration = if (alternateUrl != null) {
            durationMs
        } else {
            player.duration.takeIf { it > 0 } ?: 0L
        }
        val position = if (alternateUrl != null) {
            positionMs
        } else {
            player.currentPosition.coerceAtLeast(0L)
        }
        viewModel.saveProgress(activeRequest, position, duration, completed)
    }

    fun startAlternatePlayback() {
        player.stop()
        player.clearMediaItems()
        alternateSources = activeRequest.alternateStreamSources()
        alternateSourceIndex = 0
        alternateWebView = null
        alternateResumeApplied = false
        alternateCaptionsAvailable = false
        alternateSourceHealthy = false
        alternateSwitchInFlight = false
        captionTracks = emptyList()
        selectedCaptionId = null
        subtitleMenuVisible = false
        captionsEnabled = false
        isPlaying = false
        positionMs = 0L
        durationMs = 0L
        loading = true
        error = null
        playbackFailure = null
        controlsVisible = true
        showControls()
    }

    fun advanceAlternateSource() {
        if (alternateSwitchInFlight || alternateSources.isEmpty()) return
        alternateSwitchInFlight = true
        if (positionMs > 0L) saveProgress()
        if (alternateSourceIndex + 1 < alternateSources.size) {
            alternateSourceIndex += 1
            alternateWebView = null
            alternateResumeApplied = false
            alternateCaptionsAvailable = false
            alternateSourceHealthy = false
            isPlaying = false
            positionMs = 0L
            durationMs = 0L
            loading = true
            controlsVisible = true
            showControls()
        } else {
            loading = false
            controlsVisible = true
            playbackFailure = PlaybackFailureKind.MEDIA_SOURCE
            error = "None of the ${alternateSources.size} alternate servers returned a playable video."
        }
    }

    fun togglePlayback() {
        if (alternateUrl != null) {
            alternateWebView?.toggleAlternatePlayback()
        } else if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
        showControls()
    }

    fun seekBy(offsetMs: Long) {
        if (alternateUrl != null) {
            alternateWebView?.seekAlternateBy(offsetMs)
        } else {
            val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
            player.seekTo((player.currentPosition + offsetMs).coerceIn(0L, duration))
        }
        val seconds = (kotlin.math.abs(offsetMs) / 1_000L).coerceAtLeast(1L)
        seekFeedback = if (offsetMs < 0) "-$seconds" else "+$seconds"
        seekFeedbackEpoch += 1
        showControls()
    }

    fun playPlayback() {
        if (alternateUrl != null) alternateWebView?.playAlternate() else player.play()
        showControls()
    }

    fun pausePlayback() {
        if (alternateUrl != null) alternateWebView?.pauseAlternate() else player.pause()
        showControls()
    }

    fun toggleCaptions() {
        if (alternateUrl != null) {
            alternateWebView?.toggleAlternateEnglishCaptions()
            showControls()
        } else if (captionTracks.isNotEmpty()) {
            subtitleMenuVisible = true
            showControls()
        }
    }

    fun selectCaption(caption: ResolvedCaption?) {
        val parameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, caption == null)
        if (caption != null) {
            fun findOverride(matches: (Format) -> Boolean): TrackSelectionOverride? {
                return player.currentTracks.groups.firstNotNullOfOrNull { group ->
                    if (group.type != C.TRACK_TYPE_TEXT) return@firstNotNullOfOrNull null
                    val trackIndex = (0 until group.length).firstOrNull { index ->
                        matches(group.getTrackFormat(index))
                    } ?: return@firstNotNullOfOrNull null
                    TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex))
                }
            }
            val match = findOverride { it.id == caption.trackId }
                ?: findOverride { it.label == caption.label }
            if (match != null) parameters.setOverrideForType(match)
        }
        player.trackSelectionParameters = parameters.build()
        selectedCaptionId = caption?.trackId
        captionsEnabled = caption != null
        subtitleMenuVisible = false
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
            subtitleMenuVisible -> subtitleMenuVisible = false
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
                            playbackFailure = PlaybackFailureKind.RUNTIME_MISMATCH
                            error = "This server returned the wrong episode (${formatTime(durationMs)} instead of about ${activeRequest.expectedRuntimeMinutes} min)."
                            saveProgress()
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
                playbackFailure = PlaybackFailureKind.MEDIA_SOURCE
                error = playbackError.friendlyMessage(statusCode)
                Log.e("NCinqPlayer", "Playback failed", playbackError)
                saveProgress()
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
        playbackFailure = null
        alternateSources = emptyList()
        alternateSourceIndex = 0
        alternateWebView = null
        alternateResumeApplied = false
        alternateCaptionsAvailable = false
        alternateSourceHealthy = false
        alternateSwitchInFlight = false
        stream = null
        captionTracks = emptyList()
        selectedCaptionId = null
        subtitleMenuVisible = false
        player.stop()
        player.clearMediaItems()

        var resolved: StreamResult? = null
        var resolveFailure: Throwable? = null
        for (attempt in 0 until MAX_RESOLVER_ATTEMPTS) {
            try {
                resolved = viewModel.resolveStream(
                    activeRequest,
                    force = reloadKey > 0 || attempt > 0,
                )
                break
            } catch (failure: Throwable) {
                resolveFailure = failure
                val statusCode = (failure as? HttpException)?.code()
                if (attempt + 1 >= MAX_RESOLVER_ATTEMPTS ||
                    !failure.isRecoverableResolverFailure(statusCode)
                ) {
                    break
                }
                Log.w(
                    "NCinqPlayer",
                    "Direct resolver failed; retrying direct source (attempt ${attempt + 2})",
                    failure,
                )
                delay((attempt + 1) * 750L)
            }
        }

        if (resolved == null) {
            loading = false
            controlsVisible = true
            playbackFailure = PlaybackFailureKind.RESOLVER
            error = resolverFailureMessage(
                (resolveFailure as? HttpException)?.code(),
                resolveFailure?.message,
            )
            return@LaunchedEffect
        }

        stream = resolved
        httpDataSourceFactory.setDefaultRequestProperties(
            if (resolved.proxyToken.isNullOrBlank()) resolved.headers else emptyMap(),
        )
        val captions = resolved.captions.flatMap { caption ->
            runCatching {
                captionResolver.resolve(context, caption, activeRequest.expectedRuntimeMinutes)
            }.getOrDefault(emptyList())
        }.distinctBy { it.trackId }
        captionTracks = captions
        selectedCaptionId = captions.firstOrNull()?.trackId
        captionsEnabled = captions.isNotEmpty()
        player.setMediaItem(resolved.toMediaItem(captions))
        val savedPosition = viewModel.resumePosition(activeRequest)
        val resumeAt = max(savedPosition, retryPositionMs)
        if (resumeAt > 30_000) player.seekTo(resumeAt)
        retryPositionMs = 0L
        player.prepare()
        player.play()
    }

    LaunchedEffect(activeRequest.key) {
        automaticRetries = 0
        controlsVisible = true
        rootFocusRequester.requestFocus()
        while (isActive) {
            delay(500)
            if (alternateUrl == null) {
                positionMs = player.contentPosition.coerceAtLeast(0L)
                durationMs = player.contentDuration.coerceAtLeast(0L)
            }
            if (positionMs > 0 && !finished && positionMs % 5_000 < 600) {
                viewModel.saveProgress(activeRequest, positionMs, durationMs)
            }
            if (controlsVisible && !subtitleMenuVisible && isPlaying && error == null && !loading &&
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

    LaunchedEffect(controlsVisible, error, loading, alternateUrl, subtitleMenuVisible) {
        if (subtitleMenuVisible) {
            delay(100)
            captionFocusRequester.requestFocus()
            return@LaunchedEffect
        }
        if (activeAlternateSource != null && loading && error == null) {
            delay(250)
            controlFocusRequester.requestFocus()
        } else if (controlsVisible && error == null && !loading) {
            delay(250)
            controlFocusRequester.requestFocus()
        }
        else if (!controlsVisible && error == null) rootFocusRequester.requestFocus()
    }

    LaunchedEffect(activeAlternateSource?.id, alternateSourceHealthy, error) {
        val source = activeAlternateSource ?: return@LaunchedEffect
        if (alternateSourceHealthy || error != null) return@LaunchedEffect
        delay(ALTERNATE_SOURCE_TIMEOUT_MS)
        if (
            alternateSources.getOrNull(alternateSourceIndex)?.id == source.id &&
            !alternateSourceHealthy &&
            error == null
        ) {
            advanceAlternateSource()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
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
                        playPlayback()
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        pausePlayback()
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
                        if (captionTracks.isNotEmpty() || alternateCaptionsAvailable) toggleCaptions()
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

        activeAlternateSource?.let { source ->
            key(source.id) {
                FallbackWebPlayer(
                    url = source.url,
                    onWebViewReady = {
                        if (alternateSources.getOrNull(alternateSourceIndex)?.id == source.id) {
                            alternateWebView = it
                            alternateSwitchInFlight = false
                            rootFocusRequester.requestFocus()
                        }
                    },
                    onPlaybackState = playbackState@ { state ->
                        if (alternateSources.getOrNull(alternateSourceIndex)?.id != source.id) {
                            return@playbackState
                        }
                        if (state.failed) {
                            advanceAlternateSource()
                            return@playbackState
                        }
                        positionMs = state.positionMs
                        durationMs = state.durationMs
                        isPlaying = state.isPlaying
                        captionsEnabled = state.captionsEnabled
                        alternateCaptionsAvailable = state.captionsAvailable
                        if (state.playable && !alternateSourceHealthy) {
                            alternateSourceHealthy = true
                            loading = false
                            showControls()
                        }
                        if (state.ready && !alternateResumeApplied) {
                            alternateResumeApplied = true
                            val savedPosition = viewModel.resumePosition(activeRequest)
                            if (savedPosition > 30_000L) {
                                alternateWebView?.seekAlternateTo(savedPosition)
                            }
                        }
                        if (state.ended && !finished) {
                            finished = true
                            saveProgress(completed = true)
                            showControls()
                        }
                    },
                    onWebViewDisposed = { disposedWebView ->
                        if (alternateWebView === disposedWebView) alternateWebView = null
                    },
                    onMainFrameFailure = {
                        if (alternateSources.getOrNull(alternateSourceIndex)?.id == source.id) {
                            advanceAlternateSource()
                        }
                    },
                )
            }
        }

        if (controlsVisible && error == null && !loading) {
            PlayerChrome(
                request = activeRequest,
                provider = activeAlternateSource?.let { source ->
                    val number = alternateSourceIndex + 1
                    if (alternateSourceHealthy) {
                        "${source.name} · server $number/${alternateSources.size}"
                    } else {
                        "Checking ${source.name} · server $number/${alternateSources.size}"
                    }
                } ?: stream?.provider,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                captionsAvailable = captionTracks.isNotEmpty() || alternateCaptionsAvailable,
                captionsEnabled = captionsEnabled,
                playFocusRequester = controlFocusRequester,
                onPlayPause = ::togglePlayback,
                onSeek = ::seekBy,
                onCaptions = ::toggleCaptions,
                onPreviousEpisode = { switchEpisode(next = false) },
                onNextEpisode = { switchEpisode(next = true) },
                onNextServer = if (activeAlternateSource != null && alternateSources.size > 1) {
                    ::advanceAlternateSource
                } else {
                    null
                },
            )
        }

        if (subtitleMenuVisible && captionTracks.isNotEmpty() && error == null && !loading) {
            SubtitleChooser(
                captions = captionTracks,
                selectedCaptionId = selectedCaptionId,
                focusRequester = captionFocusRequester,
                onSelect = ::selectCaption,
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
                        activeAlternateSource?.let { source ->
                            "Checking ${source.name} · server ${alternateSourceIndex + 1} of ${alternateSources.size}"
                        } ?: if (transitionInFlight) {
                            "Loading next episode"
                        } else if (automaticRetries > 0) {
                            "Restoring playback"
                        } else {
                            "Preparing stream"
                        },
                        color = TextPrimary,
                        fontSize = 16.sp,
                    )
                    if (activeAlternateSource != null && alternateSources.size > 1) {
                        FocusButton(
                            "Try next server",
                            onClick = ::advanceAlternateSource,
                            modifier = Modifier.focusRequester(controlFocusRequester),
                            selected = true,
                        )
                    }
                }
            }
        }

        error?.let { message ->
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Playback interrupted", color = TextPrimary, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Text(message, color = TextSecondary, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    val recoveryActions = playbackFailure?.let(::recoveryActionsFor).orEmpty()
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FocusButton(
                            "Try direct again",
                            onClick = ::retryPlayback,
                            modifier = Modifier.focusRequester(retryFocusRequester),
                            selected = true,
                        )
                        if (PlaybackRecoveryAction.ALTERNATE_SERVER in recoveryActions) {
                            FocusButton(
                                "Try alternate servers",
                                onClick = {
                                    startAlternatePlayback()
                                },
                                selected = false,
                            )
                        }
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
private fun SubtitleChooser(
    captions: List<ResolvedCaption>,
    selectedCaptionId: String?,
    focusRequester: FocusRequester,
    onSelect: (ResolvedCaption?) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.56f)).padding(42.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .width(410.dp)
                .background(
                    Color.Black.copy(alpha = 0.94f),
                    androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                )
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.12f),
                    androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                )
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Subtitles", color = TextPrimary, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(
                "Choose the English release that best matches this video.",
                color = TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(4.dp))
            FocusButton(
                "Off",
                onClick = { onSelect(null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (selectedCaptionId == null) Modifier.focusRequester(focusRequester) else Modifier),
                selected = selectedCaptionId == null,
            )
            captions.forEachIndexed { index, caption ->
                val selected = caption.trackId == selectedCaptionId
                FocusButton(
                    caption.label,
                    onClick = { onSelect(caption) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (selected || (selectedCaptionId != null && index == 0 && captions.none {
                                    it.trackId == selectedCaptionId
                                })
                            ) {
                                Modifier.focusRequester(focusRequester)
                            } else {
                                Modifier
                            },
                        ),
                    selected = selected,
                )
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
    onNextServer: (() -> Unit)?,
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
                onNextServer?.let { nextServer ->
                    Spacer(Modifier.width(32.dp))
                    PlayerControl(Icons.Rounded.SwapHoriz, "Next server", nextServer)
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

internal enum class PlaybackFailureKind {
    RESOLVER,
    MEDIA_SOURCE,
    RUNTIME_MISMATCH,
}

internal enum class PlaybackRecoveryAction {
    ALTERNATE_SERVER,
    RETRY,
    BACK,
}

internal fun recoveryActionsFor(failure: PlaybackFailureKind): Set<PlaybackRecoveryAction> = when (failure) {
    PlaybackFailureKind.RESOLVER,
    PlaybackFailureKind.MEDIA_SOURCE,
    PlaybackFailureKind.RUNTIME_MISMATCH -> setOf(
        PlaybackRecoveryAction.ALTERNATE_SERVER,
        PlaybackRecoveryAction.RETRY,
        PlaybackRecoveryAction.BACK,
    )
}

internal fun resolverFailureMessage(statusCode: Int?, detail: String?): String = when {
    statusCode != null -> "Direct server returned HTTP $statusCode. Try again or use alternate server."
    !detail.isNullOrBlank() -> "$detail Try again or use alternate server."
    else -> "No direct stream is currently available. Try again or use alternate server."
}

internal fun Throwable.isRecoverableResolverFailure(statusCode: Int?): Boolean {
    val detail = message.orEmpty()
    return this is IOException ||
        statusCode in 500..599 ||
        detail.contains("timed out", ignoreCase = true) ||
        detail.contains("temporarily unavailable", ignoreCase = true)
}

internal data class AlternateStreamSource(
    val id: String,
    val name: String,
    val url: String,
)

internal fun PlaybackRequest.alternateStreamSources(): List<AlternateStreamSource> {
    val seasonNumber = season ?: 1
    val episodeNumber = episode ?: 1
    val vidSrcPath = when (mediaType) {
        MediaType.MOVIE -> "movie/$mediaId"
        MediaType.TV -> "tv/$mediaId/$seasonNumber/$episodeNumber"
    }
    val vidLinkPath = when (mediaType) {
        MediaType.MOVIE -> "movie/$mediaId"
        MediaType.TV -> "tv/$mediaId/$seasonNumber/$episodeNumber"
    }
    val embedPath = when (mediaType) {
        MediaType.MOVIE -> "movie/$mediaId"
        MediaType.TV -> "tv/$mediaId/$seasonNumber/$episodeNumber"
    }
    return listOf(
        AlternateStreamSource(
            id = "vidsrc",
            name = "VidSrc",
            url = "https://vidsrc.ru/$vidSrcPath?autoplay=true",
        ),
        AlternateStreamSource(
            id = "vidlink",
            name = "VidLink",
            url = "https://vidlink.pro/$vidLinkPath?autoplay=true",
        ),
        AlternateStreamSource(
            id = "vidfast",
            name = "VidFast",
            url = "https://vidfast.vc/$vidLinkPath?autoPlay=true",
        ),
        AlternateStreamSource(
            id = "vidapi",
            name = "VidAPI",
            url = "https://vaplayer.ru/embed/$embedPath?autoplay=true",
        ),
        AlternateStreamSource(
            id = "vidcore",
            name = "VidCore",
            url = "https://www.vidcore.org/embed/$embedPath?autoPlay=true",
        ),
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun FallbackWebPlayer(
    url: String,
    onWebViewReady: (WebView) -> Unit,
    onPlaybackState: (AlternatePlaybackState) -> Unit,
    onWebViewDisposed: (WebView) -> Unit,
    onMainFrameFailure: () -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.setSupportMultipleWindows(false)
                settings.javaScriptCanOpenWindowsAutomatically = false
                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        if (request.isForMainFrame) view.post { onMainFrameFailure() }
                    }

                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: WebResourceResponse,
                    ) {
                        if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                            view.post { onMainFrameFailure() }
                        }
                    }
                }
                webChromeClient = WebChromeClient()
                isFocusable = false
                isFocusableInTouchMode = false
                keepScreenOn = true
                loadUrl(url)
                webView = this
                onWebViewReady(this)
            }
        },
        update = { if (it.url != url) it.loadUrl(url) },
        modifier = Modifier.fillMaxSize(),
    )
    LaunchedEffect(webView, url) {
        val activeWebView = webView ?: return@LaunchedEffect
        while (isActive) {
            activeWebView.evaluateJavascript(ALTERNATE_PLAYER_STATE_SCRIPT) { result ->
                parseAlternatePlaybackState(result)?.let(onPlaybackState)
            }
            delay(500)
        }
    }
    DisposableEffect(url) {
        onDispose {
            webView?.let { disposedWebView ->
                onWebViewDisposed(disposedWebView)
                disposedWebView.stopLoading()
                disposedWebView.destroy()
            }
        }
    }
}

internal data class AlternatePlaybackState(
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val ready: Boolean,
    val ended: Boolean,
    val captionsAvailable: Boolean,
    val captionsEnabled: Boolean,
    val playable: Boolean,
    val failed: Boolean,
)

internal fun parseAlternatePlaybackState(raw: String?): AlternatePlaybackState? {
    val values = raw
        ?.trim()
        ?.takeUnless { it == "null" }
        ?.removeSurrounding("\"")
        ?.split('|')
        ?: return null
    if (values.size != 9) return null
    val positionMs = values[0].toLongOrNull()?.coerceAtLeast(0L) ?: return null
    val durationMs = values[1].toLongOrNull()?.coerceAtLeast(0L) ?: return null
    val playing = values[2].toIntOrNull() ?: return null
    val readyState = values[3].toIntOrNull() ?: return null
    val ended = values[4].toIntOrNull() ?: return null
    val captionsAvailable = values[5].toIntOrNull() ?: return null
    val captionsEnabled = values[6].toIntOrNull() ?: return null
    val playable = values[7].toIntOrNull() ?: return null
    val failed = values[8].toIntOrNull() ?: return null
    return AlternatePlaybackState(
        positionMs = positionMs,
        durationMs = durationMs,
        isPlaying = playing == 1,
        ready = readyState >= 1 && durationMs > 0,
        ended = ended == 1,
        captionsAvailable = captionsAvailable == 1,
        captionsEnabled = captionsEnabled == 1,
        playable = playable == 1,
        failed = failed == 1,
    )
}

private val ALTERNATE_PLAYER_STATE_SCRIPT = """
    (function() {
        function finiteNumber() {
            for (var i = 0; i < arguments.length; i++) {
                if (arguments[i] === null || arguments[i] === undefined || arguments[i] === '') continue;
                var value = Number(arguments[i]);
                if (Number.isFinite(value) && value >= 0) return value;
            }
            return 0;
        }
        function milliseconds(value) {
            return value > 100000 ? Math.round(value) : Math.round(value * 1000);
        }
        function findVideo(view) {
            try {
                var videos = Array.prototype.slice.call(view.document.querySelectorAll('video'));
                if (videos.length) {
                    videos.sort(function(a, b) {
                        var aScore = (Number.isFinite(a.duration) ? a.duration : 0) + (a.readyState || 0);
                        var bScore = (Number.isFinite(b.duration) ? b.duration : 0) + (b.readyState || 0);
                        return bScore - aScore;
                    });
                    return videos[0];
                }
                for (var i = 0; i < view.frames.length; i++) {
                    var nestedVideo = findVideo(view.frames[i]);
                    if (nestedVideo) return nestedVideo;
                }
            } catch (_) {}
            return null;
        }
        if (!window.__ncinqBridgeInstalled) {
            window.__ncinqBridgeInstalled = true;
            window.__ncinqMessageState = null;
            window.addEventListener('message', function(event) {
                var message = event.data;
                if (typeof message === 'string') {
                    try { message = JSON.parse(message); } catch (_) { return; }
                }
                if (!message || typeof message !== 'object') return;
                var payload = message.data && typeof message.data === 'object' ? message.data : message;
                var progress = payload.progress && typeof payload.progress === 'object' ? payload.progress : {};
                var duration = finiteNumber(
                    payload.duration,
                    payload.totalDuration,
                    progress.duration,
                    progress.total,
                    message.duration
                );
                var currentTime = finiteNumber(
                    payload.currentTime,
                    payload.position,
                    progress.currentTime,
                    progress.watchedTime,
                    progress.position,
                    message.currentTime
                );
                var eventName = String(
                    payload.event || payload.status || message.event || message.status || ''
                ).toLowerCase();
                if (duration > 0) {
                    window.__ncinqMessageState = {
                        positionMs: milliseconds(currentTime),
                        durationMs: milliseconds(duration),
                        isPlaying: eventName.indexOf('pause') < 0 && eventName.indexOf('ended') < 0,
                        ended: eventName.indexOf('ended') >= 0,
                        updatedAt: Date.now()
                    };
                }
            });
        }
        var bodyText = '';
        try {
            bodyText = ((document.body && document.body.innerText) || '').toLowerCase().slice(0, 8000);
        } catch (_) {}
        var failurePhrases = [
            "couldn't find this content",
            'could not find this content',
            'content not found',
            'no sources available',
            'no source available',
            'video not found',
            'media not found',
            'failed to load video',
            'playback unavailable',
            'playback restricted',
            'bad gateway',
            'internal server error',
            'error 404',
            'error 500'
        ];
        var explicitFailure = failurePhrases.some(function(phrase) {
            return bodyText.indexOf(phrase) >= 0;
        });
        var video = findVideo(window);
        if (!video) {
            var cached = window.__ncinqMessageState;
            if (cached && Date.now() - cached.updatedAt < 15000 && cached.durationMs > 0) {
                return [
                    cached.positionMs,
                    cached.durationMs,
                    cached.isPlaying ? 1 : 0,
                    1,
                    cached.ended ? 1 : 0,
                    0,
                    0,
                    1,
                    0
                ].join('|');
            }
            return ['0', '0', '0', '0', '0', '0', '0', '0', explicitFailure ? '1' : '0'].join('|');
        }
        var tracks = Array.prototype.slice.call(video.textTracks || []);
        var englishTracks = tracks.filter(function(track) {
            var name = ((track.language || '') + ' ' + (track.label || '')).toLowerCase();
            return name === 'en' || name.indexOf('english') >= 0 || name.indexOf('en-') >= 0;
        });
        var duration = Number.isFinite(video.duration) ? Math.round(video.duration * 1000) : 0;
        var playable = duration > 0 && video.readyState >= 1 && !video.error;
        var failed = !playable && (explicitFailure || !!video.error);
        return [
            Math.round((video.currentTime || 0) * 1000),
            duration,
            !video.paused && !video.ended ? 1 : 0,
            video.readyState || 0,
            video.ended ? 1 : 0,
            englishTracks.length > 0 ? 1 : 0,
            englishTracks.some(function(track) { return track.mode === 'showing'; }) ? 1 : 0,
            playable ? 1 : 0,
            failed ? 1 : 0
        ].join('|');
    })()
""".trimIndent()

private val FIND_ALTERNATE_VIDEO_SCRIPT = """
    function findVideo(view) {
        try {
            var videos = Array.prototype.slice.call(view.document.querySelectorAll('video'));
            if (videos.length) {
                videos.sort(function(a, b) {
                    var aScore = (Number.isFinite(a.duration) ? a.duration : 0) + (a.readyState || 0);
                    var bScore = (Number.isFinite(b.duration) ? b.duration : 0) + (b.readyState || 0);
                    return bScore - aScore;
                });
                return videos[0];
            }
            for (var i = 0; i < view.frames.length; i++) {
                var nestedVideo = findVideo(view.frames[i]);
                if (nestedVideo) return nestedVideo;
            }
        } catch (_) {}
        return null;
    }
    var video = findVideo(window);
""".trimIndent()

private fun WebView.runAlternateCommand(
    commandName: String,
    localCommand: String,
    value: Double? = null,
) {
    val valueLiteral = value?.toString() ?: "null"
    evaluateJavascript(
        """
        (function() {
            $FIND_ALTERNATE_VIDEO_SCRIPT
            if (video) {
                $localCommand
                return true;
            }
            var message = {
                type: 'PLAYER_COMMAND',
                data: { command: '$commandName', value: $valueLiteral }
            };
            var sent = false;
            function notifyFrames(view) {
                try {
                    var frames = view.document.querySelectorAll('iframe');
                    Array.prototype.forEach.call(frames, function(frame) {
                        if (!frame.contentWindow) return;
                        frame.contentWindow.postMessage(message, '*');
                        frame.contentWindow.postMessage(message.data, '*');
                        sent = true;
                        try { notifyFrames(frame.contentWindow); } catch (_) {}
                    });
                } catch (_) {}
            }
            notifyFrames(window);
            return sent;
        })()
        """.trimIndent(),
        null,
    )
}

private fun WebView.toggleAlternatePlayback() {
    runAlternateCommand(
        commandName = "toggle",
        localCommand = """
            if (video.paused) {
                var playResult = video.play();
                if (playResult && playResult.catch) playResult.catch(function() {});
            } else {
                video.pause();
            }
        """.trimIndent(),
    )
}

private fun WebView.playAlternate() {
    runAlternateCommand(
        commandName = "play",
        localCommand = """
            var playResult = video.play();
            if (playResult && playResult.catch) playResult.catch(function() {});
        """.trimIndent(),
    )
}

private fun WebView.pauseAlternate() {
    runAlternateCommand(commandName = "pause", localCommand = "video.pause();")
}

private fun WebView.seekAlternateBy(offsetMs: Long) {
    val offsetSeconds = offsetMs / 1_000.0
    runAlternateCommand(
        commandName = "seekBy",
        value = offsetSeconds,
        localCommand = """
            var end = Number.isFinite(video.duration) ? video.duration : Number.MAX_SAFE_INTEGER;
            video.currentTime = Math.max(0, Math.min(end, video.currentTime + $offsetSeconds));
        """.trimIndent(),
    )
}

private fun WebView.seekAlternateTo(positionMs: Long) {
    val positionSeconds = positionMs.coerceAtLeast(0L) / 1_000.0
    runAlternateCommand(
        commandName = "seek",
        value = positionSeconds,
        localCommand = "video.currentTime = $positionSeconds;",
    )
}

private fun WebView.toggleAlternateEnglishCaptions() {
    runAlternateCommand(
        commandName = "toggleCaptions",
        localCommand = """
            var tracks = Array.prototype.slice.call(video.textTracks || []);
            var englishTracks = tracks.filter(function(track) {
                var name = ((track.language || '') + ' ' + (track.label || '')).toLowerCase();
                return name === 'en' || name.indexOf('english') >= 0 || name.indexOf('en-') >= 0;
            });
            if (englishTracks.length) {
                var enable = !englishTracks.some(function(track) { return track.mode === 'showing'; });
                tracks.forEach(function(track) { track.mode = 'disabled'; });
                if (enable) englishTracks[0].mode = 'showing';
            }
        """.trimIndent(),
    )
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
            .setId(resolved.trackId)
            .setLabel(resolved.label)
            .setSelectionFlags(if (index == 0) C.SELECTION_FLAG_DEFAULT else 0)
            .build()
    }
    return MediaItem.Builder()
        .setUri(Uri.parse(MediaProxy.playbackUrl(this)))
        .setMimeType(if (type.equals("hls", ignoreCase = true)) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4)
        .setSubtitleConfigurations(subtitles)
        .build()
}
