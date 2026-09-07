package com.mangotv.app.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Tracks
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.Episode
import com.mangotv.app.data.model.PlayerPreferences
import com.mangotv.app.data.model.Stream
import com.mangotv.app.ui.components.FullScreenErrorState
import com.mangotv.app.ui.player.overlay.AdvancedSettingsPanel
import com.mangotv.app.ui.player.overlay.AudioTrackMenu
import com.mangotv.app.ui.player.overlay.PlaybackErrorOverlay
import com.mangotv.app.ui.player.overlay.PlaybackSpeedMenu
import com.mangotv.app.ui.player.overlay.QualityMenu
import com.mangotv.app.ui.player.overlay.SettingsPanel
import com.mangotv.app.ui.player.overlay.SourceInfoPanel
import com.mangotv.app.ui.player.overlay.SubtitlesMenu
import kotlin.math.abs
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onChangeSource: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = viewModel()
) {
    val screenState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackPhase by viewModel.playbackPhase.collectAsStateWithLifecycle()
    val audioTracks by viewModel.audioTracks.collectAsStateWithLifecycle()
    val subtitleTracks by viewModel.subtitleTracks.collectAsStateWithLifecycle()
    val qualityOptions by viewModel.qualityOptions.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (val state = screenState) {
            is PlayerScreenUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(48.dp),
                    color = Color.White
                )
            }
            is PlayerScreenUiState.Error -> {
                FullScreenErrorState(message = state.message, onRetry = viewModel::load)
            }
            is PlayerScreenUiState.Ready -> {
                PlaybackContent(
                    content = state.content,
                    episode = state.episode,
                    stream = state.stream,
                    phase = playbackPhase,
                    audioTracks = audioTracks,
                    subtitleTracks = subtitleTracks,
                    qualityOptions = qualityOptions,
                    preferences = preferences,
                    onPhaseChanged = viewModel::onPlaybackPhaseChanged,
                    onTracksChanged = viewModel::onTracksChanged,
                    onAutoplayChange = viewModel::setAutoplayNextEpisode,
                    onSkipIntroChange = viewModel::setSkipIntroEnabled,
                    onBack = onBack,
                    onChangeSource = onChangeSource
                )
            }
        }
    }
}

@Composable
private fun PlaybackContent(
    content: Content,
    episode: Episode?,
    stream: Stream,
    phase: PlaybackPhase,
    audioTracks: List<AudioTrackOption>,
    subtitleTracks: List<SubtitleTrackOption>,
    qualityOptions: List<QualityOption>,
    preferences: PlayerPreferences,
    onPhaseChanged: (PlaybackPhase) -> Unit,
    onTracksChanged: (Tracks) -> Unit,
    onAutoplayChange: (Boolean) -> Unit,
    onSkipIntroChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onChangeSource: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val exoPlayer = remember { buildExoPlayer(context) }

    DisposableEffect(exoPlayer) {
        val listener = PlayerListenerBridge(onPhaseChanged, onTracksChanged)
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Pause (not release) when the Activity stops — simpler and faster to
    // resume than a full release/reprepare; the DisposableEffect above
    // still handles a genuine release when this screen leaves composition.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) exoPlayer.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun startPlayback() {
        val mediaItem = stream.toMediaItemOrNull()
        if (mediaItem == null) {
            onPhaseChanged(
                PlaybackPhase.Error(
                    PlaybackErrorType.TORRENT_UNSUPPORTED,
                    "This source requires torrent streaming, which isn't supported yet. Try a different source."
                )
            )
        } else {
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    LaunchedEffect(stream.id) { startPlayback() }

    // -- Controls visibility, focus zone, and the interaction-resets-the-
    // -- auto-hide-timer bookkeeping.
    var controlsVisible by remember { mutableStateOf(false) }
    var focusZone by remember { mutableStateOf(PlayerFocusZone.NONE) }
    var interactionTick by remember { mutableIntStateOf(0) }
    fun bumpInteraction() { interactionTick++ }
    fun onFocusZoneChanged(zone: PlayerFocusZone) {
        focusZone = zone
        bumpInteraction()
    }

    // Merely focusing the timeline no longer captures LEFT/RIGHT — the user
    // has to actively select it (DPAD_CENTER/Enter) to start scrubbing, or
    // LEFT/RIGHT would fall through to normal focus traversal and never let
    // the user move focus past the timeline to reach the icon row. Reset
    // whenever focus leaves the timeline by any path (UP, BACK, controls
    // hiding), so a stale "still scrubbing" state can never survive a zone
    // change.
    var timelineScrubbing by remember { mutableStateOf(false) }
    LaunchedEffect(focusZone) {
        if (focusZone != PlayerFocusZone.TIMELINE) timelineScrubbing = false
    }

    // A stack, not a single nullable value: Subtitles/Audio/Quality/Source
    // Info can be reached either directly from their own bottom-row icon
    // (dismissing straight back to plain controls) or via Settings
    // (dismissing back to Settings instead) — popping one level handles
    // both without hardcoding where each menu "returns to".
    var overlayStack by remember { mutableStateOf<List<PlayerOverlay>>(emptyList()) }
    val activeOverlay = overlayStack.lastOrNull()
    fun pushOverlay(overlay: PlayerOverlay) {
        overlayStack = overlayStack + overlay
        bumpInteraction()
    }
    fun popOverlay() {
        overlayStack = overlayStack.dropLast(1)
        bumpInteraction()
    }

    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    fun changePlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        exoPlayer.playbackParameters = PlaybackParameters(speed)
        bumpInteraction()
    }

    // Only worth a menu when there's a real choice — matches the spec's
    // "don't show a fake quality/options menu" instruction. Subtitles
    // always has a synthetic "Off" entry (see toSubtitleTrackOptions), so
    // size > 1 means at least one real track exists; audio can't be "off"
    // so size > 1 means more than the single track already playing.
    val showSubtitles = subtitleTracks.size > 1
    val showAudio = audioTracks.size > 1
    val showQuality = qualityOptions.count { it.trackGroup != null } > 1
    val subtitleLabel = subtitleTracks.firstOrNull { it.isSelected }?.label ?: "Off"
    val audioLabel = audioTracks.firstOrNull { it.isSelected }?.label ?: "Auto"
    val qualityLabel = qualityOptions.firstOrNull { it.isSelected }?.label ?: "Auto"

    val rootFocusRequester = remember { FocusRequester() }
    val backFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val rewindFocusRequester = remember { FocusRequester() }
    val forwardFocusRequester = remember { FocusRequester() }
    val subtitleFocusRequester = remember { FocusRequester() }
    val audioFocusRequester = remember { FocusRequester() }
    val qualityFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }
    val nextEpisodeFocusRequester = remember { FocusRequester() }
    val timelineFocusRequester = remember { FocusRequester() }

    // Moves focus in both directions: onto play/pause when controls appear
    // (or they'd stay stuck on the invisible root anchor and be visible but
    // unreachable), and back onto the root anchor when controls hide again
    // (their buttons leave composition, so focus would otherwise be lost
    // entirely and stop receiving key events at all). Also covers the very
    // first focus request on initial composition, since controlsVisible
    // starts false.
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            runCatching { playPauseFocusRequester.requestFocus() }
        } else {
            focusZone = PlayerFocusZone.NONE
            runCatching { rootFocusRequester.requestFocus() }
        }
    }

    // Auto-hide after a few seconds of inactivity — re-armed by any bumped
    // interaction (seeking, toggling play/pause, moving focus) so it never
    // fires while the user is actively using the controls, and suspended
    // entirely while a menu is open (it shouldn't vanish while the user is
    // reading options).
    LaunchedEffect(controlsVisible, interactionTick, activeOverlay) {
        if (controlsVisible && activeOverlay == null) {
            delay(4000)
            controlsVisible = false
        }
    }

    var seekIndicatorText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(seekIndicatorText) {
        if (seekIndicatorText != null) {
            delay(900)
            seekIndicatorText = null
        }
    }

    var playPauseFlashVisible by remember { mutableStateOf(false) }
    var playPauseFlashIsPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(playPauseFlashVisible) {
        if (playPauseFlashVisible) {
            delay(700)
            playPauseFlashVisible = false
        }
    }

    fun togglePlayPause() {
        val willPlay = phase !is PlaybackPhase.Playing
        if (willPlay) exoPlayer.play() else exoPlayer.pause()
        playPauseFlashIsPlaying = willPlay
        playPauseFlashVisible = true
        bumpInteraction()
    }

    fun seekPillText(deltaMs: Long): String {
        val seconds = abs(deltaMs) / 1000
        return if (deltaMs < 0) "«« $seconds seconds" else "$seconds seconds »»"
    }

    fun clampedSeekTarget(targetMs: Long): Long {
        val duration = exoPlayer.duration
        return if (duration > 0) targetMs.coerceIn(0, duration) else targetMs.coerceAtLeast(0)
    }

    // Explicit rewind/forward button clicks — a flat, immediate 10s seek,
    // distinct from the accelerating hold gesture below (which is driven by
    // held D-pad LEFT/RIGHT, not a click).
    fun seekByClick(deltaMs: Long) {
        exoPlayer.seekTo(clampedSeekTarget(exoPlayer.currentPosition + deltaMs))
        seekIndicatorText = seekPillText(deltaMs)
        bumpInteraction()
    }

    // Preview-then-commit-on-release hold-to-seek: KeyDown repeats only
    // grow the pending delta and update the pill (no seekTo() per tick, to
    // avoid stutter from repeated flush/rebuffer); KeyUp commits once. This
    // also means a single tap (KeyDown then immediate KeyUp) still performs
    // a normal flat 10s seek, since pendingSeekDeltaMs starts at ±10s.
    var seekAnchorMs by remember { mutableStateOf<Long?>(null) }
    var pendingSeekDeltaMs by remember { mutableStateOf(0L) }

    fun beginOrContinueHoldSeek(direction: Int, isFreshPress: Boolean) {
        if (isFreshPress || seekAnchorMs == null) {
            seekAnchorMs = exoPlayer.currentPosition
            pendingSeekDeltaMs = 10_000L * direction
        } else {
            val magnitude = abs(pendingSeekDeltaMs)
            val step = when {
                magnitude < 30_000L -> 10_000L
                magnitude < 90_000L -> 30_000L
                else -> 60_000L
            }
            val maxMagnitude = 120_000L
            pendingSeekDeltaMs = (pendingSeekDeltaMs + step * direction).coerceIn(-maxMagnitude, maxMagnitude)
        }
        seekIndicatorText = seekPillText(pendingSeekDeltaMs)
        bumpInteraction()
    }

    fun commitHoldSeek() {
        val anchor = seekAnchorMs ?: return
        exoPlayer.seekTo(clampedSeekTarget(anchor + pendingSeekDeltaMs))
        seekAnchorMs = null
        pendingSeekDeltaMs = 0L
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                // The Fire TV remote's dedicated Play/Pause hardware button
                // is a transport control, not a D-pad direction — it should
                // always work (menu open or not, controls hidden or not),
                // so it's handled before anything else below gets a say.
                if (event.key == Key.MediaPlayPause || event.key == Key.MediaPlay || event.key == Key.MediaPause) {
                    if (event.type == KeyEventType.KeyDown) {
                        val isPlaying = phase is PlaybackPhase.Playing
                        val shouldToggle = event.key == Key.MediaPlayPause ||
                            (event.key == Key.MediaPlay && !isPlaying) ||
                            (event.key == Key.MediaPause && isPlaying)
                        if (shouldToggle) togglePlayPause()
                    }
                    return@onPreviewKeyEvent true
                }
                // While a menu is open, it owns all key handling itself
                // (its own list navigation/selection) — don't fight it with
                // the player's own global seek/reveal shortcuts.
                if (activeOverlay != null) return@onPreviewKeyEvent false
                // LEFT/RIGHT only seeks while the timeline itself has focus
                // AND the user has actively selected it (DPAD_CENTER/Enter,
                // toggled below) — deliberately not just "has focus", so
                // LEFT/RIGHT can still move focus past the timeline onto the
                // next button instead of getting stuck seeking the instant
                // it's merely highlighted. Rewind10/Forward10 remain
                // reachable as explicit buttons (a click, not a D-pad
                // direction) regardless of scrub state.
                val seekEligible = focusZone == PlayerFocusZone.TIMELINE && timelineScrubbing
                when (event.key) {
                    Key.DirectionLeft, Key.DirectionRight -> {
                        if (!seekEligible) return@onPreviewKeyEvent false
                        val direction = if (event.key == Key.DirectionLeft) -1 else 1
                        when (event.type) {
                            KeyEventType.KeyDown -> {
                                beginOrContinueHoldSeek(direction, isFreshPress = event.nativeKeyEvent.repeatCount == 0)
                                true
                            }
                            KeyEventType.KeyUp -> {
                                commitHoldSeek()
                                true
                            }
                            else -> false
                        }
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        when {
                            event.type != KeyEventType.KeyDown -> false
                            // Selecting the timeline toggles scrub mode
                            // in-place, rather than firing a click (it has
                            // no onClick — TvFocusSurface isn't used here
                            // since LEFT/RIGHT, not a click, is what drives
                            // seeking once selected).
                            focusZone == PlayerFocusZone.TIMELINE -> {
                                timelineScrubbing = !timelineScrubbing
                                bumpInteraction()
                                true
                            }
                            !controlsVisible && focusZone == PlayerFocusZone.NONE -> {
                                controlsVisible = true
                                true
                            }
                            else -> false
                        }
                    }
                    else -> false
                }
            }
    ) {
        PlayerSurface(exoPlayer = exoPlayer, modifier = Modifier.fillMaxSize())

        if (phase is PlaybackPhase.Loading || phase is PlaybackPhase.Buffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(48.dp),
                color = Color.White
            )
        }

        if (phase is PlaybackPhase.Error) {
            PlaybackErrorOverlay(
                message = phase.message,
                onTryAgain = ::startPlayback,
                onChangeSource = onChangeSource,
                onBack = onBack
            )
        }

        seekIndicatorText?.let { text ->
            SeekIndicatorPill(text = text, modifier = Modifier.align(Alignment.Center))
        }

        if (playPauseFlashVisible) {
            PlayPauseIndicator(isPlaying = playPauseFlashIsPlaying, modifier = Modifier.align(Alignment.Center))
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                PlayerControlsScrim(modifier = Modifier.fillMaxSize())

                Column(modifier = Modifier.fillMaxSize()) {
                    PlayerTopBar(
                        content = content,
                        episode = episode,
                        onBack = onBack,
                        backFocusRequester = backFocusRequester,
                        backFocusDown = playPauseFocusRequester,
                        onBackFocusChanged = { focused -> if (focused) onFocusZoneChanged(PlayerFocusZone.TOP_BAR) }
                    )

                    Spacer(Modifier.weight(1f))

                    PlayerBottomControls(
                        exoPlayer = exoPlayer,
                        phase = phase,
                        showNextEpisode = episode != null,
                        showSubtitles = showSubtitles,
                        showAudio = showAudio,
                        showQuality = showQuality,
                        onPlayPause = ::togglePlayPause,
                        onSeek = ::seekByClick,
                        onSubtitles = { pushOverlay(PlayerOverlay.SUBTITLES) },
                        onAudio = { pushOverlay(PlayerOverlay.AUDIO) },
                        onQuality = { pushOverlay(PlayerOverlay.QUALITY) },
                        onSettings = { pushOverlay(PlayerOverlay.SETTINGS) },
                        onNextEpisode = {},
                        onFocusZoneChanged = ::onFocusZoneChanged,
                        isTimelineScrubbing = timelineScrubbing,
                        playPauseFocusRequester = playPauseFocusRequester,
                        rewindFocusRequester = rewindFocusRequester,
                        forwardFocusRequester = forwardFocusRequester,
                        subtitleFocusRequester = subtitleFocusRequester,
                        audioFocusRequester = audioFocusRequester,
                        qualityFocusRequester = qualityFocusRequester,
                        settingsFocusRequester = settingsFocusRequester,
                        nextEpisodeFocusRequester = nextEpisodeFocusRequester,
                        timelineFocusRequester = timelineFocusRequester
                    )
                }
            }
        }

        when (activeOverlay) {
            PlayerOverlay.SUBTITLES -> SubtitlesMenu(
                options = subtitleTracks,
                onSelect = { option -> exoPlayer.selectSubtitleTrack(option); popOverlay() }
            )
            PlayerOverlay.AUDIO -> AudioTrackMenu(
                options = audioTracks,
                onSelect = { option -> exoPlayer.selectAudioTrack(option); popOverlay() }
            )
            PlayerOverlay.QUALITY -> QualityMenu(
                options = qualityOptions,
                onSelect = { option -> exoPlayer.selectQuality(option); popOverlay() }
            )
            PlayerOverlay.SETTINGS -> SettingsPanel(
                playbackSpeed = playbackSpeed,
                preferences = preferences,
                onAutoplayChange = onAutoplayChange,
                showSubtitles = showSubtitles,
                showAudio = showAudio,
                showQuality = showQuality,
                subtitleLabel = subtitleLabel,
                audioLabel = audioLabel,
                qualityLabel = qualityLabel,
                onOpenSubtitles = { pushOverlay(PlayerOverlay.SUBTITLES) },
                onOpenAudio = { pushOverlay(PlayerOverlay.AUDIO) },
                onOpenQuality = { pushOverlay(PlayerOverlay.QUALITY) },
                onOpenPlaybackSpeed = { pushOverlay(PlayerOverlay.PLAYBACK_SPEED) },
                onOpenAdvanced = { pushOverlay(PlayerOverlay.ADVANCED) }
            )
            PlayerOverlay.PLAYBACK_SPEED -> PlaybackSpeedMenu(
                playbackSpeed = playbackSpeed,
                onSelect = { speed -> changePlaybackSpeed(speed); popOverlay() }
            )
            PlayerOverlay.ADVANCED -> AdvancedSettingsPanel(
                skipIntroEnabled = preferences.skipIntroEnabled,
                onSkipIntroChange = onSkipIntroChange,
                onOpenSourceInfo = { pushOverlay(PlayerOverlay.SOURCE_INFO) },
                onChangeSource = onChangeSource
            )
            PlayerOverlay.SOURCE_INFO -> SourceInfoPanel(
                stream = stream,
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks
            )
            null -> Unit
        }
    }

    BackHandler {
        when {
            timelineScrubbing -> timelineScrubbing = false
            overlayStack.isNotEmpty() -> {
                popOverlay()
                // Only once the whole stack is closed (not a single level of
                // a nested menu, e.g. Advanced -> Settings) — landing back
                // on a known, predictable button rather than wherever focus
                // happens to end up once the overlay leaves composition.
                // Settings/Playback Speed/Advanced back to each other
                // re-focus their own first row on remount instead.
                if (overlayStack.isEmpty()) {
                    runCatching { playPauseFocusRequester.requestFocus() }
                }
            }
            controlsVisible -> controlsVisible = false
            else -> onBack()
        }
    }
}
