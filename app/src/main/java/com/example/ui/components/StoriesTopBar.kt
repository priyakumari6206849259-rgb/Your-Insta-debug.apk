package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.media.MediaUtils
import com.example.data.model.LiveStream
import com.example.data.model.LiveStreamMode
import com.example.data.model.PublicProfile
import com.example.data.model.StoryItem
import com.example.data.model.StoryMediaType
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.CoralPrimary
import kotlinx.coroutines.launch

@Composable
fun StoriesAndLiveTopBar(
    myProfile: PublicProfile?,
    stories: List<StoryItem>,
    liveStreams: List<LiveStream>,
    onAddStoryClick: () -> Unit,
    onGoLiveClick: () -> Unit,
    onOpenStory: (StoryItem) -> Unit,
    onOpenLiveStream: (LiveStream) -> Unit,
    modifier: Modifier = Modifier,
    translucentOverlay: Boolean = false
) {
    val groupedStories = remember(stories) {
        stories.groupBy { it.creatorId }.values.map { it.first() }
    }

    val containerColor = if (translucentOverlay) {
        Color.Black.copy(alpha = 0.42f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    }

    Surface(
        color = containerColor,
        modifier = modifier.fillMaxWidth()
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Add 24h Story Ring
            item(key = "add_story") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(68.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onAddStoryClick)
                        .padding(vertical = 2.dp)
                        .testTag("top_bar_add_story_button")
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        AvatarImage(
                            photoUrl = myProfile?.photoUrl,
                            displayName = myProfile?.displayName ?: "You",
                            size = 54.dp
                        )
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(CoralPrimary)
                                .border(2.dp, Color.Black, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add 24h Story",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Your Story",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (translucentOverlay) Color.White else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 2. Go Live Button Ring
            item(key = "go_live") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(68.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onGoLiveClick)
                        .padding(vertical = 2.dp)
                        .testTag("top_bar_go_live_button")
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(Color(0xFFFF1E56), Color(0xFFFF7A00)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Videocam,
                            contentDescription = "Go Live",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Go Live",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = CoralPrimary,
                        maxLines = 1
                    )
                }
            }

            // 3. Active Live Streams (Camera + Gaming)
            items(liveStreams, key = { "live_${it.id}" }) { stream ->
                val isGaming = LiveStreamMode.fromWire(stream.streamMode) != LiveStreamMode.CAMERA
                val badgeColor = if (isGaming) Color(0xFF7C4DFF) else Color(0xFFFF1E56)
                val badgeText = if (isGaming) "GAME" else "LIVE"
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(72.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpenLiveStream(stream) }
                        .padding(vertical = 2.dp)
                        .testTag("top_bar_live_${stream.id}")
                ) {
                    Box(contentAlignment = Alignment.BottomCenter) {
                        Box(
                            modifier = Modifier
                                .border(2.5.dp, badgeColor, CircleShape)
                                .padding(3.dp)
                        ) {
                            AvatarImage(
                                photoUrl = stream.hostPhotoUrl,
                                displayName = stream.hostName,
                                size = 48.dp
                            )
                        }
                        Surface(
                            color = badgeColor,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = badgeText,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stream.gameTitle?.takeIf { isGaming && it.isNotBlank() } ?: stream.hostName,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (translucentOverlay) Color.White else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 4. Active 24h Stories
            items(groupedStories, key = { "story_${it.creatorId}" }) { story ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(68.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpenStory(story) }
                        .padding(vertical = 2.dp)
                        .testTag("top_bar_story_${story.id}")
                ) {
                    AvatarImage(
                        photoUrl = story.creatorPhotoUrl,
                        displayName = story.creatorName,
                        size = 54.dp,
                        showRing = true
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = story.creatorName,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (translucentOverlay) Color.White else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun StoryCreatorDialog(
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onPublishStory: (mediaType: StoryMediaType, mediaUrl: String, caption: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedType by remember { mutableStateOf(StoryMediaType.IMAGE) }
    var mediaUrl by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val pickPhotoLauncher = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            isProcessing = true
            errorText = null
            scope.launch {
                MediaUtils.encodeStoryPhotoToDataUri(context, uri)
                    .onSuccess { dataUri ->
                        selectedType = StoryMediaType.IMAGE
                        mediaUrl = dataUri
                    }
                    .onFailure { err ->
                        errorText = err.localizedMessage ?: "Failed to prepare story photo"
                    }
                isProcessing = false
            }
        }
    }

    val pickVideoLauncher = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            isProcessing = true
            errorText = null
            scope.launch {
                MediaUtils.copyVideoToInternalStorage(context, uri)
                    .onSuccess { (localUri, _) ->
                        selectedType = StoryMediaType.VIDEO
                        mediaUrl = localUri
                    }
                    .onFailure { err ->
                        errorText = err.localizedMessage ?: "Failed to prepare story video"
                    }
                isProcessing = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Share 24h Story") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Stories appear at the top bar and disappear automatically after 24 hours.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            pickPhotoLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                        },
                        enabled = !isProcessing && !isSaving,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("story_pick_photo_button")
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Photo")
                    }
                    OutlinedButton(
                        onClick = {
                            pickVideoLauncher.launch(PickVisualMediaRequest(PickVisualMedia.VideoOnly))
                        },
                        enabled = !isProcessing && !isSaving,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("story_pick_video_button")
                    ) {
                        Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Video")
                    }
                }

                if (isProcessing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Preparing story media...", style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (mediaUrl.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${if (selectedType == StoryMediaType.IMAGE) "Photo" else "Video"} attached & ready for 24h story",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (errorText != null) {
                    Text(
                        text = errorText ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                OutlinedTextField(
                    value = caption,
                    onValueChange = { if (it.length <= 200) caption = it },
                    label = { Text("Story caption (optional)") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("story_caption_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onPublishStory(selectedType, mediaUrl, caption) },
                enabled = mediaUrl.isNotBlank() && !isSaving && !isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                modifier = Modifier.testTag("publish_story_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Post Story")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun StartLiveDialog(
    isStarting: Boolean,
    onDismiss: () -> Unit,
    onStartLive: (title: String, streamUrl: String?, mode: LiveStreamMode, gameTitle: String?) -> Unit
) {
    var selectedMode by remember { mutableStateOf(LiveStreamMode.MOBILE_GAME_SCREEN) }
    var title by remember { mutableStateOf("") }
    var gameTitle by remember { mutableStateOf("") }
    var streamUrl by remember { mutableStateOf("") }

    val popularGames = remember {
        listOf("BGMI", "Free Fire", "Valorant", "Minecraft", "COD Mobile")
    }

    AlertDialog(
        onDismissRequest = { if (!isStarting) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (selectedMode == LiveStreamMode.CAMERA) Icons.Filled.Videocam else Icons.Filled.SportsEsports,
                    contentDescription = null,
                    tint = if (selectedMode == LiveStreamMode.CAMERA) CoralPrimary else Color(0xFF9D7BFF),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (selectedMode == LiveStreamMode.CAMERA) "Go Live" else "Gaming Live Studio"
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LiveStreamMode.entries.forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            label = { Text(mode.label, maxLines = 1) },
                            leadingIcon = {
                                val icon = when (mode) {
                                    LiveStreamMode.CAMERA -> Icons.Filled.Videocam
                                    LiveStreamMode.MOBILE_GAME_SCREEN -> Icons.Filled.SportsEsports
                                    LiveStreamMode.PC_GAME_STREAM -> Icons.Filled.Computer
                                }
                                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.testTag("live_mode_${mode.wireValue}")
                        )
                    }
                }

                Text(
                    text = when (selectedMode) {
                        LiveStreamMode.CAMERA -> "Stream directly from your front/back camera with real-time chat."
                        LiveStreamMode.MOBILE_GAME_SCREEN -> "Broadcast your mobile screen & gameplay via Android Screen Capture + ZegoCloud SDK."
                        LiveStreamMode.PC_GAME_STREAM -> "Stream PC/Console gameplay via ZegoCloud room or an external OBS/HLS stream URL."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 120) title = it },
                    label = { Text("Stream Title") },
                    placeholder = {
                        Text(
                            if (selectedMode == LiveStreamMode.CAMERA) "Q&A, Behind the scenes..."
                            else "Solo vs Squad Ranked Push!"
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("live_title_input")
                )

                if (selectedMode != LiveStreamMode.CAMERA) {
                    OutlinedTextField(
                        value = gameTitle,
                        onValueChange = { if (it.length <= 80) gameTitle = it },
                        label = { Text("Game Name") },
                        placeholder = { Text("e.g., BGMI, Valorant, Minecraft") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("live_game_title_input")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        popularGames.forEach { game ->
                            FilterChip(
                                selected = gameTitle.equals(game, ignoreCase = true),
                                onClick = { gameTitle = game },
                                label = { Text(game, maxLines = 1) }
                            )
                        }
                    }
                }

                if (selectedMode == LiveStreamMode.PC_GAME_STREAM || selectedMode == LiveStreamMode.CAMERA) {
                    OutlinedTextField(
                        value = streamUrl,
                        onValueChange = { if (it.length <= 1000) streamUrl = it },
                        label = {
                            Text(
                                if (selectedMode == LiveStreamMode.PC_GAME_STREAM) {
                                    "PC OBS / HLS / MP4 Stream URL (optional)"
                                } else {
                                    "Optional Stream URL"
                                }
                            )
                        },
                        placeholder = { Text("https://... (or use ZegoCloud room)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("live_stream_url_input")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val defaultTitle = when (selectedMode) {
                        LiveStreamMode.CAMERA -> "Live Studio Session"
                        LiveStreamMode.MOBILE_GAME_SCREEN -> "${gameTitle.ifBlank { "Mobile Gaming" }} Live"
                        LiveStreamMode.PC_GAME_STREAM -> "${gameTitle.ifBlank { "PC Gaming" }} Stream"
                    }
                    onStartLive(
                        title.ifBlank { defaultTitle },
                        streamUrl.trim().takeIf { it.isNotBlank() },
                        selectedMode,
                        gameTitle.trim().takeIf { selectedMode != LiveStreamMode.CAMERA && it.isNotEmpty() }
                    )
                },
                enabled = !isStarting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedMode == LiveStreamMode.CAMERA) Color(0xFFFF1E56) else Color(0xFF7C4DFF)
                ),
                modifier = Modifier.testTag("confirm_go_live_button")
            ) {
                if (isStarting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(if (selectedMode == LiveStreamMode.CAMERA) "Start Live" else "Launch Stream")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isStarting) {
                Text("Cancel")
            }
        }
    )
}
