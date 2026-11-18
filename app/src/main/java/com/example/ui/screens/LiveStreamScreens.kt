package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.view.TextureView
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.media.MediaUtils
import com.example.data.model.LiveComment
import com.example.data.model.LiveStream
import com.example.data.model.LiveStreamMode
import com.example.live.ScreenCaptureForegroundService
import com.example.live.ZegoConnectionState
import com.example.live.ZegoLiveManager
import com.example.ui.components.AvatarImage
import com.example.ui.components.FollowSubscribeActionRow
import com.example.ui.components.formatCompactCount
import com.example.ui.state.UiState
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.MintTertiary

private val GamingPurple = Color(0xFF7C4DFF)

@OptIn(UnstableApi::class)
@Composable
fun LiveBroadcasterScreen(
    stream: LiveStream,
    commentsState: UiState<List<LiveComment>>,
    onEndLive: () -> Unit,
    onSendComment: (String, () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val clipboard = LocalClipboardManager.current

    val streamMode = remember(stream.streamMode) { LiveStreamMode.fromWire(stream.streamMode) }
    val roomId = remember(stream.id, stream.agoraChannel) {
        stream.agoraChannel?.takeIf { it.isNotBlank() } ?: stream.id
    }

    val zegoManager = remember(context) { ZegoLiveManager(context) }
    val zegoState by zegoManager.connectionState.collectAsStateWithLifecycle()
    val zegoStatusNote by zegoManager.statusNote.collectAsStateWithLifecycle()
    val zegoRemoteStreamId by zegoManager.remoteStreamId.collectAsStateWithLifecycle()
    val isZegoCapturing by zegoManager.isScreenCapturing.collectAsStateWithLifecycle()
    val isServiceCapturing by ScreenCaptureForegroundService.isRunning.collectAsStateWithLifecycle()

    val isScreenSharingActive = isZegoCapturing || isServiceCapturing

    val cleanupAndEnd: () -> Unit = {
        zegoManager.leaveAndDestroy()
        ScreenCaptureForegroundService.stop(context)
        onEndLive()
    }

    BackHandler(onBack = cleanupAndEnd)

    DisposableEffect(zegoManager, roomId, streamMode) {
        val enableCamera = streamMode == LiveStreamMode.CAMERA
        zegoManager.joinAsBroadcaster(
            roomId = roomId,
            userId = stream.hostId,
            userName = stream.hostName,
            enableCamera = enableCamera
        )
        onDispose {
            zegoManager.leaveAndDestroy()
            ScreenCaptureForegroundService.stop(context)
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var useFrontCamera by remember { mutableStateOf(true) }
    var cameraUnavailable by remember { mutableStateOf(false) }
    var micEnabled by remember { mutableStateOf(hasAudioPermission) }
    var gameAudioEnabled by remember { mutableStateOf(true) }
    var targetFps by remember { mutableIntStateOf(60) }
    var showFacecamPip by remember { mutableStateOf(false) }
    var copiedChannelNotice by remember { mutableStateOf(false) }
    var screenCaptureFeedback by remember { mutableStateOf<String?>(null) }
    var commentDraft by remember { mutableStateOf("") }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val audioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        micEnabled = granted
        zegoManager.setMicEnabled(granted)
    }

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ScreenCaptureForegroundService.start(context, stream.title)
            screenCaptureFeedback = "Screen & Gameplay Broadcast Active (${targetFps} FPS) • Minimize app to play your game!"
        } else {
            screenCaptureFeedback = "Screen capture permission cancelled."
        }
    }

    LaunchedEffect(streamMode) {
        if (streamMode == LiveStreamMode.CAMERA && !hasCameraPermission) {
            cameraLauncher.launch(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!notifGranted && streamMode == LiveStreamMode.MOBILE_GAME_SCREEN) {
                notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val needCameraPreview = streamMode == LiveStreamMode.CAMERA ||
        (streamMode == LiveStreamMode.MOBILE_GAME_SCREEN && showFacecamPip)

    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    DisposableEffect(needCameraPreview, hasCameraPermission, useFrontCamera, lifecycleOwner) {
        if (!needCameraPreview || !hasCameraPermission || zegoManager.isZegoConfigured() && streamMode == LiveStreamMode.CAMERA) {
            onDispose { }
        } else {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            var boundProvider: ProcessCameraProvider? = null
            val executor = ContextCompat.getMainExecutor(context)
            val listener = Runnable {
                runCatching {
                    val provider = cameraProviderFuture.get()
                    boundProvider = provider
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val selector = if (useFrontCamera) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, selector, preview)
                    cameraUnavailable = false
                }.onFailure {
                    cameraUnavailable = true
                }
            }
            cameraProviderFuture.addListener(listener, executor)
            onDispose {
                runCatching { boundProvider?.unbindAll() }
            }
        }
    }

    val startOrStopScreenShare: () -> Unit = {
        if (isScreenSharingActive) {
            zegoManager.stopGamingScreenCapture(resumeCamera = false)
            ScreenCaptureForegroundService.stop(context)
            screenCaptureFeedback = "Screen capture paused."
        } else {
            if (zegoManager.isZegoConfigured()) {
                zegoManager.startGamingScreenCapture(
                    roomId = roomId,
                    fps = targetFps,
                    captureSystemAudio = gameAudioEnabled,
                    captureMic = micEnabled
                ).onSuccess {
                    screenCaptureFeedback = "ZegoCloud ${targetFps} FPS Gaming Screen Live!"
                }.onFailure { err ->
                    screenCaptureFeedback = err.localizedMessage ?: "Screen capture failed"
                }
            } else {
                val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                    as? MediaProjectionManager
                if (projectionManager != null) {
                    mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
                } else {
                    screenCaptureFeedback = "MediaProjection service unavailable on this device."
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0B12))
    ) {
        // 1. Background / Broadcast Visual Surface
        when (streamMode) {
            LiveStreamMode.CAMERA -> {
                if (zegoManager.isZegoConfigured() && hasCameraPermission) {
                    AndroidView(
                        factory = { ctx ->
                            TextureView(ctx).also { tv ->
                                zegoManager.attachLocalVideo(tv)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (hasCameraPermission && !cameraUnavailable) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LiveStageFallback(
                        title = stream.title,
                        hostName = stream.hostName,
                        hostPhotoUrl = stream.hostPhotoUrl,
                        gameTitle = null,
                        subtitle = if (!hasCameraPermission) {
                            "Camera permission needed for video preview. Live chat is active."
                        } else {
                            "Live Studio Audio & Real-Time Follower Chat Active"
                        },
                        actionLabel = if (!hasCameraPermission) "Enable Camera" else null,
                        onAction = if (!hasCameraPermission) {
                            { cameraLauncher.launch(Manifest.permission.CAMERA) }
                        } else null
                    )
                }
            }

            LiveStreamMode.MOBILE_GAME_SCREEN -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF28134D), Color(0xFF101324), Color(0xFF080910))
                            )
                        )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 86.dp, start = 20.dp, end = 20.dp)
                    ) {
                        Surface(
                            color = if (isScreenSharingActive) MintTertiary.copy(alpha = 0.18f)
                            else GamingPurple.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    if (isScreenSharingActive) MintTertiary.copy(alpha = 0.6f)
                                    else GamingPurple.copy(alpha = 0.5f),
                                    RoundedCornerShape(20.dp)
                                )
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = if (isScreenSharingActive) Icons.Filled.CastConnected else Icons.Filled.SportsEsports,
                                    contentDescription = null,
                                    tint = if (isScreenSharingActive) MintTertiary else Color(0xFFB39DFF),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = if (isScreenSharingActive) {
                                        "Screen Broadcast LIVE (${targetFps} FPS)"
                                    } else {
                                        "Mobile Gaming Broadcast Studio"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = screenCaptureFeedback ?: zegoStatusNote
                                        ?: "Tap Share Screen below, then open your game to stream gameplay live.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.78f),
                                    textAlign = TextAlign.Center
                                )

                                Spacer(Modifier.height(12.dp))

                                Button(
                                    onClick = startOrStopScreenShare,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isScreenSharingActive) Color(0xFFFF1E56) else GamingPurple
                                    ),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .testTag("toggle_screen_capture_button")
                                ) {
                                    Icon(
                                        imageVector = if (isScreenSharingActive) Icons.Filled.StopCircle else Icons.Filled.CastConnected,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (isScreenSharingActive) "Stop Game Screen Share" else "Start Game Screen Share",
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(Modifier.height(10.dp))

                                // Gaming Stream Controls Row: FPS, Mic, Game Audio, Facecam
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilterChip(
                                        selected = targetFps == 60,
                                        onClick = { targetFps = if (targetFps == 60) 30 else 60 },
                                        label = { Text("${targetFps} FPS") },
                                        modifier = Modifier.testTag("gaming_fps_toggle")
                                    )
                                    FilterChip(
                                        selected = micEnabled,
                                        onClick = {
                                            if (!hasAudioPermission) {
                                                audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            } else {
                                                micEnabled = !micEnabled
                                                zegoManager.setMicEnabled(micEnabled)
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (micEnabled) Icons.Filled.Mic else Icons.Filled.MicOff,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = { Text(if (micEnabled) "Mic On" else "Mic Off") },
                                        modifier = Modifier.testTag("gaming_mic_toggle")
                                    )
                                    FilterChip(
                                        selected = showFacecamPip,
                                        onClick = {
                                            if (!hasCameraPermission) {
                                                cameraLauncher.launch(Manifest.permission.CAMERA)
                                            }
                                            showFacecamPip = !showFacecamPip
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (showFacecamPip) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = { Text("Cam") },
                                        modifier = Modifier.testTag("gaming_facecam_toggle")
                                    )
                                }
                            }
                        }
                    }

                    // Optional Floating Facecam PiP
                    if (showFacecamPip && hasCameraPermission && !cameraUnavailable) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(top = 64.dp, end = 16.dp)
                                .size(width = 88.dp, height = 122.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(2.dp, GamingPurple, RoundedCornerShape(14.dp))
                        ) {
                            AndroidView(
                                factory = { previewView },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            LiveStreamMode.PC_GAME_STREAM -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF15294A), Color(0xFF0E1322), Color(0xFF080910))
                            )
                        )
                ) {
                    val remoteId = zegoRemoteStreamId
                    if (remoteId != null && zegoManager.isZegoConfigured()) {
                        AndroidView(
                            factory = { ctx ->
                                TextureView(ctx).also { tv ->
                                    zegoManager.attachRemoteVideo(tv, remoteId)
                                }
                            },
                            update = { tv -> zegoManager.attachRemoteVideo(tv, remoteId) },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (!stream.streamUrl.isNullOrBlank()) {
                        ExoLiveStreamView(streamUrl = stream.streamUrl, isMuted = false)
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(top = 92.dp, start = 20.dp, end = 20.dp)
                        ) {
                            Surface(
                                color = Color(0xFF141B30).copy(alpha = 0.9f),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, AmberSecondary.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(18.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Computer,
                                        contentDescription = null,
                                        tint = AmberSecondary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "PC / Console Gaming Relay Active",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Join this ZegoCloud Room from your PC / OBS Zego client to push desktop gameplay to viewers:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.78f),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Surface(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(roomId))
                                            copiedChannelNotice = true
                                        },
                                        color = Color.Black.copy(alpha = 0.45f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Room ID: $roomId",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MintTertiary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.Filled.ContentCopy,
                                                contentDescription = "Copy ZegoCloud Room ID",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    if (copiedChannelNotice) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = "Room ID copied!",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MintTertiary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Top & Bottom Scrims
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
        )

        // Top Broadcaster Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PulsingLiveBadge(isGaming = streamMode != LiveStreamMode.CAMERA)
                    ViewerCountPill(stream.viewerCount)
                    if (!stream.gameTitle.isNullOrBlank()) {
                        GameTitlePill(stream.gameTitle)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (streamMode == LiveStreamMode.CAMERA && hasCameraPermission && !cameraUnavailable) {
                        IconButton(
                            onClick = {
                                useFrontCamera = !useFrontCamera
                                if (zegoManager.isZegoConfigured()) {
                                    zegoManager.flipCamera()
                                }
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .minimumInteractiveComponentSize()
                                .testTag("live_flip_camera_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Cameraswitch,
                                contentDescription = "Switch camera",
                                tint = Color.White
                            )
                        }
                    }

                    Button(
                        onClick = cleanupAndEnd,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1E56)),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("end_live_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("End Live", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = stream.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val sdkLabel = when (zegoState) {
                ZegoConnectionState.CONNECTED -> "ZegoCloud HD Connected"
                ZegoConnectionState.CONNECTING -> "Connecting to ZegoCloud..."
                ZegoConnectionState.LOCAL_STUDIO_ONLY -> "Local Studio Preview • Configure ZEGO_APP_ID & ZEGO_APP_SIGN in Secrets"
                ZegoConnectionState.ERROR -> zegoStatusNote ?: "ZegoCloud Connection Error"
                ZegoConnectionState.IDLE -> "Studio Initializing..."
            }
            Text(
                text = "@${stream.hostUsername} • $sdkLabel",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Bottom Real-time Live Chat Overlay
        LiveChatOverlay(
            commentsState = commentsState,
            draft = commentDraft,
            onDraftChange = { if (it.length <= 300) commentDraft = it },
            onSend = {
                val msg = commentDraft.trim()
                if (msg.isNotEmpty()) {
                    onSendComment(msg) { commentDraft = "" }
                }
            },
            inputTag = "live_broadcaster_chat_input",
            sendTag = "live_broadcaster_send_button",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun LiveViewerScreen(
    stream: LiveStream,
    commentsState: UiState<List<LiveComment>>,
    isFollowingHost: Boolean,
    isSubscribedHost: Boolean,
    isOwnStream: Boolean,
    onBack: () -> Unit,
    onToggleFollow: () -> Unit,
    onToggleSubscribe: () -> Unit,
    onOpenHostProfile: () -> Unit,
    onSendComment: (String, () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val streamMode = remember(stream.streamMode) { LiveStreamMode.fromWire(stream.streamMode) }
    val isGaming = streamMode != LiveStreamMode.CAMERA
    val roomId = remember(stream.id, stream.agoraChannel) {
        stream.agoraChannel?.takeIf { it.isNotBlank() } ?: stream.id
    }

    val zegoManager = remember(context) { ZegoLiveManager(context) }
    val remoteStreamId by zegoManager.remoteStreamId.collectAsStateWithLifecycle()

    val leaveViewer: () -> Unit = {
        zegoManager.leaveAndDestroy()
        onBack()
    }
    BackHandler(onBack = leaveViewer)

    DisposableEffect(zegoManager, roomId) {
        zegoManager.joinAsViewer(
            roomId = roomId,
            userId = "viewer_${System.currentTimeMillis()}",
            userName = "Viewer"
        )
        onDispose { zegoManager.leaveAndDestroy() }
    }

    val hasStreamUrl = !stream.streamUrl.isNullOrBlank()
    var isMuted by remember { mutableStateOf(false) }
    var commentDraft by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0B10))
    ) {
        val activeRemoteStreamId = remoteStreamId
        when {
            activeRemoteStreamId != null && zegoManager.isZegoConfigured() -> {
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).also { tv ->
                            zegoManager.attachRemoteVideo(tv, activeRemoteStreamId)
                        }
                    },
                    update = { tv ->
                        zegoManager.attachRemoteVideo(tv, activeRemoteStreamId)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            hasStreamUrl -> {
                ExoLiveStreamView(
                    streamUrl = stream.streamUrl ?: "",
                    isMuted = isMuted
                )
            }

            else -> {
                LiveStageFallback(
                    title = stream.title,
                    hostName = stream.hostName,
                    hostPhotoUrl = stream.hostPhotoUrl,
                    gameTitle = stream.gameTitle,
                    subtitle = when {
                        !stream.live -> "This live broadcast has ended"
                        isGaming -> "Gaming Live Room • Room: $roomId\nReal-time gamer chat is active below"
                        else -> "Live Creator Room • Chat in real time below"
                    }
                )
            }
        }

        // Top & Bottom Scrims
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp)
                .background(Brush.verticalGradient(listOf(Color(0xD9000000), Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
        )

        // Top Bar: Back, Host info, Follow/Subscribe, Live badge
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = leaveViewer,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .minimumInteractiveComponentSize()
                            .testTag("live_viewer_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Leave live stream",
                            tint = Color.White
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onOpenHostProfile)
                            .padding(end = 6.dp)
                    ) {
                        AvatarImage(
                            photoUrl = stream.hostPhotoUrl,
                            displayName = stream.hostName,
                            size = 38.dp,
                            showRing = true
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stream.hostName,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stream.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (stream.live) {
                        PulsingLiveBadge(isGaming = isGaming)
                    } else {
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "ENDED",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    ViewerCountPill(stream.viewerCount)
                    if (hasStreamUrl) {
                        IconButton(
                            onClick = { isMuted = !isMuted },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isMuted) "Unmute live" else "Mute live",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                if (!stream.gameTitle.isNullOrBlank()) {
                    GameTitlePill(stream.gameTitle)
                }
                if (!isOwnStream) {
                    FollowSubscribeActionRow(
                        isFollowing = isFollowingHost,
                        isSubscribed = isSubscribedHost,
                        isOwnContent = false,
                        onToggleFollow = onToggleFollow,
                        onToggleSubscribe = onToggleSubscribe
                    )
                }
            }
        }

        // Bottom Real-Time Live Chat
        LiveChatOverlay(
            commentsState = commentsState,
            draft = commentDraft,
            onDraftChange = { if (it.length <= 300) commentDraft = it },
            onSend = {
                val msg = commentDraft.trim()
                if (msg.isNotEmpty()) {
                    onSendComment(msg) { commentDraft = "" }
                }
            },
            inputTag = "live_viewer_chat_input",
            sendTag = "live_viewer_send_button",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ExoLiveStreamView(
    streamUrl: String,
    isMuted: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isBuffering by remember { mutableStateOf(true) }
    val player = remember(context) { MediaUtils.createSmoothExoPlayer(context) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_RESUME -> player.playWhenReady = true
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(streamUrl) {
        player.setMediaItem(MediaItem.fromUri(Uri.parse(streamUrl)))
        player.prepare()
        player.playWhenReady = true
    }

    LaunchedEffect(isMuted) {
        player.volume = if (isMuted) 0f else 1f
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setPlayer(player)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        if (isBuffering) {
            CircularProgressIndicator(
                color = CoralPrimary,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun LiveStageFallback(
    title: String,
    hostName: String,
    hostPhotoUrl: String?,
    gameTitle: String?,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val gradientColors = if (!gameTitle.isNullOrBlank()) {
        listOf(Color(0xFF28134D), Color(0xFF14182E), Color(0xFF0A0B10))
    } else {
        listOf(Color(0xFF3E1028), Color(0xFF151828), Color(0xFF0A0B10))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(colors = gradientColors)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .border(
                        width = 3.dp,
                        brush = Brush.linearGradient(
                            if (!gameTitle.isNullOrBlank()) listOf(GamingPurple, MintTertiary)
                            else listOf(CoralPrimary, AmberSecondary)
                        ),
                        shape = CircleShape
                    )
                    .padding(6.dp)
            ) {
                AvatarImage(
                    photoUrl = hostPhotoUrl,
                    displayName = hostName,
                    size = 96.dp
                )
            }
            if (!gameTitle.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                GameTitlePill(gameTitle)
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (!gameTitle.isNullOrBlank()) Icons.Filled.SportsEsports else Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = if (!gameTitle.isNullOrBlank()) MintTertiary else CoralPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.78f),
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun PulsingLiveBadge(isGaming: Boolean = false) {
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )
    Surface(
        color = if (isGaming) GamingPurple else Color(0xFFFF1E56),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .alpha(dotAlpha)
                    .clip(CircleShape)
                    .background(Color.White)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = if (isGaming) "GAME LIVE" else "LIVE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun GameTitlePill(gameTitle: String) {
    Surface(
        color = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.border(1.dp, GamingPurple.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.SportsEsports,
                contentDescription = "Game",
                tint = Color(0xFFB39DFF),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = gameTitle,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ViewerCountPill(viewerCount: Long) {
    Surface(
        color = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Visibility,
                contentDescription = "Live viewers",
                tint = MintTertiary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = formatCompactCount(viewerCount.coerceAtLeast(1L)),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun LiveChatOverlay(
    commentsState: UiState<List<LiveComment>>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    inputTag: String,
    sendTag: String,
    modifier: Modifier = Modifier
) {
    val comments = (commentsState as? UiState.Success)?.data.orEmpty()
    val listState = rememberLazyListState()

    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty()) {
            listState.animateScrollToItem(comments.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp)
        ) {
            if (comments.isEmpty()) {
                item {
                    Surface(
                        color = Color.Black.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "GG! Real-time gaming & stream comments appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            } else {
                items(comments, key = { it.id }) { comment ->
                    Surface(
                        color = Color.Black.copy(alpha = 0.48f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AvatarImage(
                                photoUrl = comment.senderPhotoUrl,
                                displayName = comment.senderName,
                                size = 24.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = comment.senderName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AmberSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = comment.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = {
                    Text("Send a live comment...", color = Color.White.copy(alpha = 0.6f))
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color.Black.copy(alpha = 0.55f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.45f),
                    focusedBorderColor = CoralPrimary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.28f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag(inputTag)
            )
            IconButton(
                onClick = onSend,
                enabled = draft.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (draft.isNotBlank()) CoralPrimary else Color.White.copy(alpha = 0.2f)
                    )
                    .minimumInteractiveComponentSize()
                    .testTag(sendTag)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send live comment",
                    tint = Color.White
                )
            }
        }
    }
}
