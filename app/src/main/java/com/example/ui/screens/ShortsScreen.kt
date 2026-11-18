package com.example.ui.screens

import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.ui.components.VideoThumbnailImage
import com.example.ui.components.formatCompactCount
import com.example.ui.theme.CoralPrimary
import kotlinx.coroutines.delay

@Composable
fun ShortsScreen(
    shorts: List<FeedVideoItem>,
    currentUserId: String,
    isScreenActive: Boolean,
    topBarOverlay: @Composable () -> Unit = {},
    onVideoViewed: (String) -> Unit = {},
    onToggleLike: (String, Boolean) -> Unit,
    onToggleFollow: (String, Boolean) -> Unit,
    onToggleSubscribe: (String, Boolean) -> Unit,
    onOpenCreatorProfile: (String) -> Unit,
    onMessageCreator: (String) -> Unit,
    onOpenUpload: () -> Unit
) {
    if (shorts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .align(Alignment.TopCenter)
            ) {
                topBarOverlay()
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Text(
                    text = "No Shorts Yet",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Tap + Upload to post your first vertical video.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(16.dp))
                Surface(
                    onClick = onOpenUpload,
                    color = CoralPrimary,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(
                        text = "Upload Short",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            }
        }
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val pagerState = rememberPagerState(pageCount = { shorts.size })
    val player = remember(context) {
        MediaUtils.createSmoothExoPlayer(context).apply {
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    var isMuted by remember { mutableStateOf(false) }
    var isPausedByUser by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var playbackProgress by remember { mutableFloatStateOf(0f) }

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
                Lifecycle.Event.ON_PAUSE -> player.playWhenReady = false
                Lifecycle.Event.ON_RESUME -> if (isScreenActive && !isPausedByUser) {
                    player.playWhenReady = true
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val currentItem = shorts.getOrNull(pagerState.settledPage)
    LaunchedEffect(currentItem?.post?.id, currentItem?.post?.videoUrl, isScreenActive) {
        if (currentItem != null && isScreenActive) {
            onVideoViewed(currentItem.post.id)
            isPausedByUser = false
            player.setMediaItem(MediaItem.fromUri(Uri.parse(currentItem.post.videoUrl)))
            player.prepare()
            player.playWhenReady = true
        } else {
            player.playWhenReady = false
        }
    }

    LaunchedEffect(isMuted) {
        player.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(isScreenActive, isPausedByUser) {
        while (true) {
            val dur = player.duration.coerceAtLeast(1L)
            val pos = player.currentPosition.coerceAtLeast(0L)
            playbackProgress = if (dur > 1L) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
            delay(200L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("shorts_vertical_pager")
        ) { page ->
            val item = shorts[page]
            val isCurrentPage = page == pagerState.settledPage

            ShortPageContent(
                item = item,
                isCurrentPage = isCurrentPage,
                player = if (isCurrentPage) player else null,
                isBuffering = isCurrentPage && isBuffering,
                isPaused = isCurrentPage && isPausedByUser,
                isMuted = isMuted,
                progress = if (isCurrentPage) playbackProgress else 0f,
                isOwnVideo = item.post.creatorId == currentUserId,
                onTapVideo = {
                    if (isCurrentPage) {
                        isPausedByUser = !isPausedByUser
                        player.playWhenReady = !isPausedByUser
                    }
                },
                onToggleMute = { isMuted = !isMuted },
                onToggleLike = { onToggleLike(item.post.id, item.likedByMe) },
                onToggleFollow = { onToggleFollow(item.post.creatorId, item.followingCreator) },
                onToggleSubscribe = { onToggleSubscribe(item.post.creatorId, item.subscribedCreator) },
                onOpenCreatorProfile = { onOpenCreatorProfile(item.post.creatorId) },
                onMessageCreator = { onMessageCreator(item.post.creatorId) },
                onShare = {
                    MediaUtils.shareVideo(
                        context = context,
                        title = item.post.title,
                        creatorName = item.post.creatorName,
                        videoUrl = item.post.videoUrl
                    )
                }
            )
        }

        // Top floating header + Stories & Live Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "Your Insta • Shorts",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
                IconButton(
                    onClick = { isMuted = !isMuted },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .minimumInteractiveComponentSize()
                        .testTag("shorts_mute_button")
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isMuted) "Unmute video" else "Mute video",
                        tint = Color.White
                    )
                }
            }
            topBarOverlay()
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ShortPageContent(
    item: FeedVideoItem,
    isCurrentPage: Boolean,
    player: Player?,
    isBuffering: Boolean,
    isPaused: Boolean,
    isMuted: Boolean,
    progress: Float,
    isOwnVideo: Boolean,
    onTapVideo: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleFollow: () -> Unit,
    onToggleSubscribe: () -> Unit,
    onOpenCreatorProfile: () -> Unit,
    onMessageCreator: () -> Unit,
    onShare: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onTapVideo
            )
    ) {
        if (!item.post.thumbnailUrl.isNullOrBlank()) {
            VideoThumbnailImage(
                thumbnailUrl = item.post.thumbnailUrl,
                contentDescription = item.post.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF10121A))
            )
        }

        if (isCurrentPage && player != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    if (view.player !== player) {
                        view.player = player
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Bottom readability scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xD9000000))
                    )
                )
        )

        if (isBuffering && !isPaused) {
            CircularProgressIndicator(
                color = CoralPrimary,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        AnimatedVisibility(
            visible = isPaused,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play video",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // Right-side social action rail
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ShortActionIcon(
                icon = if (item.likedByMe) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                label = formatCompactCount(item.likeCount),
                tint = if (item.likedByMe) CoralPrimary else Color.White,
                contentDescription = "Like short",
                testTag = "short_like_button",
                onClick = onToggleLike
            )

            if (!isOwnVideo) {
                ShortActionIcon(
                    icon = Icons.Filled.PersonAdd,
                    label = if (item.followingCreator) "Following" else "Follow",
                    tint = if (item.followingCreator) CoralPrimary else Color.White,
                    contentDescription = "Follow creator",
                    testTag = "short_follow_button",
                    onClick = onToggleFollow
                )
                ShortActionIcon(
                    icon = if (item.subscribedCreator) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsNone,
                    label = if (item.subscribedCreator) "Subbed" else "Subscribe",
                    tint = if (item.subscribedCreator) Color(0xFFFF9F43) else Color.White,
                    contentDescription = "Subscribe to creator",
                    testTag = "short_subscribe_button",
                    onClick = onToggleSubscribe
                )
                ShortActionIcon(
                    icon = Icons.AutoMirrored.Filled.Send,
                    label = "DM",
                    tint = Color.White,
                    contentDescription = "Message creator",
                    testTag = "short_dm_button",
                    onClick = onMessageCreator
                )
            }

            ShortActionIcon(
                icon = Icons.Filled.Share,
                label = "Share",
                tint = Color.White,
                contentDescription = "Share short",
                testTag = "short_share_button",
                onClick = onShare
            )
        }

        // Bottom-left creator & caption info
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.76f)
                .padding(start = 16.dp, bottom = 22.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onOpenCreatorProfile)
                    .padding(vertical = 4.dp)
            ) {
                AvatarImage(
                    photoUrl = item.post.creatorPhotoUrl,
                    displayName = item.post.creatorName,
                    size = 40.dp,
                    showRing = true
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = item.post.creatorName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "@${item.post.creatorUsername} • ${formatCompactCount(item.post.viewsCount)} views • ${formatCompactCount(item.creatorSubscriberCount)} subs",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = item.post.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.post.caption.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.post.caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.88f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        LinearProgressIndicator(
            progress = { progress },
            color = CoralPrimary,
            trackColor = Color.White.copy(alpha = 0.2f),
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun ShortActionIcon(
    icon: ImageVector,
    label: String,
    tint: Color,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f))
                .minimumInteractiveComponentSize()
                .testTag(testTag)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}
