package com.example.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.media.MediaUtils
import com.example.data.model.StoryItem
import com.example.data.model.StoryMediaType
import com.example.ui.components.AvatarImage
import com.example.ui.theme.CoralPrimary
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun StoryViewerScreen(
    stories: List<StoryItem>,
    initialStoryId: String,
    currentUserId: String,
    onClose: () -> Unit,
    onDeleteStory: (String) -> Unit,
    onMessageCreator: (String) -> Unit
) {
    BackHandler(onBack = onClose)

    if (stories.isEmpty()) {
        onClose()
        return
    }

    val startIdx = remember(initialStoryId, stories) {
        stories.indexOfFirst { it.id == initialStoryId }.coerceAtLeast(0)
    }
    var currentIndex by remember(startIdx) { mutableIntStateOf(startIdx) }
    val currentStory = stories.getOrNull(currentIndex) ?: stories.first()

    val context = LocalContext.current
    val player = remember(context) {
        MediaUtils.createSmoothExoPlayer(context).apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    var segmentProgress by remember(currentStory.id) { mutableFloatStateOf(0f) }

    val mediaType = StoryMediaType.fromWire(currentStory.mediaType)
    LaunchedEffect(currentStory.id) {
        segmentProgress = 0f
        if (mediaType == StoryMediaType.VIDEO) {
            player.setMediaItem(MediaItem.fromUri(Uri.parse(currentStory.mediaUrl)))
            player.prepare()
            player.playWhenReady = true
            while (true) {
                val dur = player.duration.coerceAtLeast(1L)
                val pos = player.currentPosition.coerceAtLeast(0L)
                segmentProgress = if (dur > 1L) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
                if (player.playbackState == Player.STATE_ENDED) {
                    if (currentIndex < stories.lastIndex) currentIndex++ else onClose()
                    break
                }
                delay(100L)
            }
        } else {
            player.stop()
            val totalTicks = 50
            for (tick in 1..totalTicks) {
                segmentProgress = tick.toFloat() / totalTicks.toFloat()
                delay(100L)
            }
            if (currentIndex < stories.lastIndex) currentIndex++ else onClose()
        }
    }

    val decodedBitmap = remember(currentStory.mediaUrl) {
        if (mediaType == StoryMediaType.IMAGE && currentStory.mediaUrl.startsWith("data:image")) {
            runCatching {
                val b64 = currentStory.mediaUrl.substringAfter("base64,", "")
                if (b64.isNotEmpty()) {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                } else null
            }.getOrNull()
        } else null
    }

    val hoursRemaining = remember(currentStory.expiresAtEpochMs) {
        val diffMs = (currentStory.expiresAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
        val hrs = (diffMs / (60L * 60L * 1000L)).toInt()
        if (hrs >= 1) "${hrs}h left" else "Expiring soon"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (mediaType == StoryMediaType.VIDEO) {
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
        } else if (decodedBitmap != null) {
            Image(
                bitmap = decodedBitmap,
                contentDescription = currentStory.caption.ifBlank { "Story photo" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = currentStory.mediaUrl,
                contentDescription = currentStory.caption.ifBlank { "Story photo" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Left / Right tap zones for previous / next story
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (currentIndex > 0) currentIndex-- else onClose()
                    }
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (currentIndex < stories.lastIndex) currentIndex++ else onClose()
                    }
            )
        }

        // Top gradient + segmented progress + creator info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))
                )
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                stories.forEachIndexed { idx, _ ->
                    val progress = when {
                        idx < currentIndex -> 1f
                        idx == currentIndex -> segmentProgress
                        else -> 0f
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarImage(
                        photoUrl = currentStory.creatorPhotoUrl,
                        displayName = currentStory.creatorName,
                        size = 38.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = currentStory.creatorName,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "@${currentStory.creatorUsername} • $hoursRemaining",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (currentStory.creatorId == currentUserId) {
                        IconButton(
                            onClick = { onDeleteStory(currentStory.id) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("delete_story_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Delete story",
                                tint = Color.White
                            )
                        }
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("close_story_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close story",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Bottom caption & quick DM reply
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))
                )
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            if (currentStory.caption.isNotBlank()) {
                Text(
                    text = currentStory.caption,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
                Spacer(Modifier.height(10.dp))
            }
            if (currentStory.creatorId != currentUserId) {
                Surface(
                    onClick = { onMessageCreator(currentStory.creatorId) },
                    color = Color.White.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("story_reply_dm_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = CoralPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Reply to ${currentStory.creatorName}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}
