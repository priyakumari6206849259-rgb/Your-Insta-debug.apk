package com.example.ui.screens

import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.media.MediaUtils
import com.example.data.model.FeedVideoItem
import com.example.ui.components.AvatarImage
import com.example.ui.components.FollowSubscribeActionRow
import com.example.ui.components.VideoThumbnailImage
import com.example.ui.components.formatCompactCount
import com.example.ui.theme.CoralPrimary
import kotlinx.coroutines.delay

@Composable
fun VideosScreen(
    videos: List<FeedVideoItem>,
    selectedVideo: FeedVideoItem?,
    currentUserId: String,
    storiesTopBar: @Composable () -> Unit = {},
    onVideoViewed: (String) -> Unit = {},
    onSelectVideo: (FeedVideoItem?) -> Unit,
    onToggleLike: (String, Boolean) -> Unit,
    onToggleFollow: (String, Boolean) -> Unit,
    onToggleSubscribe: (String, Boolean) -> Unit,
    onOpenCreatorProfile: (String) -> Unit,
    onMessageCreator: (String) -> Unit
) {
    if (selectedVideo != null) {
        val freshItem = videos.find { it.post.id == selectedVideo.post.id } ?: selectedVideo
        FullVideoPlayerScreen(
            item = freshItem,
            currentUserId = currentUserId,
            onVideoViewed = onVideoViewed,
            onBack = { onSelectVideo(null) },
            onToggleLike = { onToggleLike(freshItem.post.id, freshItem.likedByMe) },
            onToggleFollow = { onToggleFollow(freshItem.post.creatorId, freshItem.followingCreator) },
            onToggleSubscribe = { onToggleSubscribe(freshItem.post.creatorId, freshItem.subscribedCreator) },
            onOpenCreatorProfile = { onOpenCreatorProfile(freshItem.post.creatorId) },
            onMessageCreator = { onMessageCreator(freshItem.post.creatorId) }
        )
        return
    }

    var filterIndex by remember { mutableStateOf(0) }
    val filteredVideos = remember(videos, filterIndex, currentUserId) {
        when (filterIndex) {
            1 -> videos.filter { it.post.creatorId == currentUserId }
            2 -> videos.filter { it.isBuiltInCatalog }
            else -> videos
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        storiesTopBar()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Watch Videos",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Long-form HD streaming with seek controls",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "${videos.size} Videos",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "My Uploads", "Featured").forEachIndexed { idx, label ->
                FilterChip(
                    selected = filterIndex == idx,
                    onClick = { filterIndex = idx },
                    label = { Text(label) },
                    modifier = Modifier.testTag("videos_filter_$idx")
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (filteredVideos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.VideoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "No full videos in this filter yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredVideos, key = { it.post.id }) { item ->
                    FullVideoCard(
                        item = item,
                        onClick = { onSelectVideo(item) },
                        onOpenCreator = { onOpenCreatorProfile(item.post.creatorId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FullVideoCard(
    item: FeedVideoItem,
    onClick: () -> Unit,
    onOpenCreator: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("full_video_card_${item.post.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color(0xFF141724))
            ) {
                VideoThumbnailImage(
                    thumbnailUrl = item.post.thumbnailUrl,
                    contentDescription = item.post.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play full video",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                ) {
                    Text(
                        text = MediaUtils.formatDurationSec(item.post.durationSec),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarImage(
                    photoUrl = item.post.creatorPhotoUrl,
                    displayName = item.post.creatorName,
                    size = 42.dp,
                    modifier = Modifier.clickable(onClick = onOpenCreator)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.post.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${item.post.creatorName} • ${formatCompactCount(item.post.viewsCount)} views • ${formatCompactCount(item.likeCount)} likes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun FullVideoPlayerScreen(
    item: FeedVideoItem,
    currentUserId: String,
    onVideoViewed: (String) -> Unit = {},
    onBack: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleFollow: () -> Unit,
    onToggleSubscribe: () -> Unit,
    onOpenCreatorProfile: () -> Unit,
    onMessageCreator: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember(context) { MediaUtils.createSmoothExoPlayer(context) }

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf((item.post.durationSec * 1000L).coerceAtLeast(1000L)) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPreviewMs by remember { mutableLongStateOf(0L) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY && player.duration > 0L) {
                    durationMs = player.duration
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
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
            if (event == Lifecycle.Event.ON_PAUSE) {
                player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(item.post.id, item.post.videoUrl) {
        onVideoViewed(item.post.id)
        player.setMediaItem(MediaItem.fromUri(Uri.parse(item.post.videoUrl)))
        player.prepare()
        player.playWhenReady = true
    }

    LaunchedEffect(isMuted) {
        player.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(player) {
        while (true) {
            if (!isSeeking) {
                currentPosMs = player.currentPosition.coerceAtLeast(0L)
                if (player.duration > 0L) {
                    durationMs = player.duration
                }
            }
            delay(250L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Player top header + video viewport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setPlayer(player)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .minimumInteractiveComponentSize()
                    .testTag("full_player_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to videos",
                    tint = Color.White
                )
            }

            IconButton(
                onClick = { isMuted = !isMuted },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .minimumInteractiveComponentSize()
                    .testTag("full_player_mute_button")
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    tint = Color.White
                )
            }

            if (isBuffering) {
                CircularProgressIndicator(
                    color = CoralPrimary,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // Custom responsive playback controls + seek bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                val displayPos = if (isSeeking) seekPreviewMs else currentPosMs
                val maxDur = durationMs.coerceAtLeast(1000L)

                Slider(
                    value = displayPos.toFloat().coerceIn(0f, maxDur.toFloat()),
                    onValueChange = { newVal ->
                        isSeeking = true
                        seekPreviewMs = newVal.toLong()
                    },
                    onValueChangeFinished = {
                        player.seekTo(seekPreviewMs)
                        currentPosMs = seekPreviewMs
                        isSeeking = false
                    },
                    valueRange = 0f..maxDur.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = CoralPrimary,
                        activeTrackColor = CoralPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("full_player_seekbar")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${MediaUtils.formatDurationSec(displayPos / 1000L)} / ${MediaUtils.formatDurationSec(maxDur / 1000L)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val target = (player.currentPosition - 10_000L).coerceAtLeast(0L)
                                player.seekTo(target)
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("full_player_rewind_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Replay10,
                                contentDescription = "Rewind 10 seconds",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Surface(
                            onClick = {
                                if (isPlaying) player.pause() else player.play()
                            },
                            color = CoralPrimary,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("full_player_play_pause_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                val target = (player.currentPosition + 10_000L).coerceAtMost(maxDur)
                                player.seekTo(target)
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("full_player_forward_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Forward10,
                                contentDescription = "Forward 10 seconds",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

        // Metadata, Social Actions & Creator Card
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .widthIn(max = 720.dp)
        ) {
            Text(
                text = item.post.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${formatCompactCount(item.post.viewsCount)} views • ${formatCompactCount(item.likeCount)} likes",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (item.post.caption.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.post.caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    onClick = onToggleLike,
                    color = if (item.likedByMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("full_player_like_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (item.likedByMe) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Like video",
                            tint = if (item.likedByMe) CoralPrimary else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = formatCompactCount(item.likeCount),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                Surface(
                    onClick = {
                        MediaUtils.shareVideo(
                            context = context,
                            title = item.post.title,
                            creatorName = item.post.creatorName,
                            videoUrl = item.post.videoUrl
                        )
                    },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("full_player_share_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share video"
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Share", style = MaterialTheme.typography.labelLarge)
                    }
                }

                if (item.post.creatorId != currentUserId) {
                    Surface(
                        onClick = onMessageCreator,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("full_player_dm_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Message creator"
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("DM", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenCreatorProfile)
                    ) {
                        AvatarImage(
                            photoUrl = item.post.creatorPhotoUrl,
                            displayName = item.post.creatorName,
                            size = 48.dp,
                            showRing = true
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.post.creatorName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "@${item.post.creatorUsername} • ${formatCompactCount(item.creatorFollowerCount)} followers • ${formatCompactCount(item.creatorSubscriberCount)} subs",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (item.post.creatorId != currentUserId) {
                        Spacer(Modifier.height(12.dp))
                        FollowSubscribeActionRow(
                            isFollowing = item.followingCreator,
                            isSubscribed = item.subscribedCreator,
                            isOwnContent = false,
                            onToggleFollow = onToggleFollow,
                            onToggleSubscribe = onToggleSubscribe
                        )
                    }
                }
            }
        }
    }
}
