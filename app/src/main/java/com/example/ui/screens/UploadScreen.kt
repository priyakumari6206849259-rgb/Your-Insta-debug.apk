package com.example.ui.screens

import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.data.media.MediaUtils
import com.example.data.model.PublicProfile
import com.example.data.model.VideoType
import com.example.ui.components.VideoThumbnailImage
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.MintTertiary
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun UploadScreen(
    myProfile: PublicProfile?,
    isSaving: Boolean,
    onPublishVideo: (
        title: String,
        caption: String,
        videoUrl: String,
        thumbnailUrl: String?,
        videoType: VideoType,
        durationSec: Long,
        onDone: () -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedType by remember { mutableStateOf(VideoType.SHORT) }
    var useUrlInput by remember { mutableStateOf(false) }
    var videoUrl by remember { mutableStateOf("") }
    var durationSec by remember { mutableLongStateOf(15L) }
    var title by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    var isImportingMedia by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Thumbnail / Cover State (for both Shorts & Full Videos)
    var thumbnailUrl by remember { mutableStateOf<String?>(null) }
    var selectedThumbnailSource by remember { mutableStateOf<String?>(null) }
    var extractedFrames by remember { mutableStateOf<List<MediaUtils.ExtractedVideoFrame>>(emptyList()) }
    var isExtractingFrames by remember { mutableStateOf(false) }
    var isCapturingCustomFrame by remember { mutableStateOf(false) }
    var isProcessingThumbnail by remember { mutableStateOf(false) }
    var frameSliderMs by remember { mutableFloatStateOf(0f) }

    val previewPlayer = remember(context) {
        MediaUtils.createSmoothExoPlayer(context).apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
        }
    }

    DisposableEffect(previewPlayer) {
        onDispose { previewPlayer.release() }
    }

    LaunchedEffect(videoUrl, durationSec) {
        if (videoUrl.isNotBlank()) {
            previewPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            previewPlayer.prepare()
            previewPlayer.playWhenReady = true

            isExtractingFrames = true
            val framesRes = MediaUtils.extractVideoFrames(
                context = context,
                videoUrl = videoUrl,
                durationSec = durationSec,
                count = 6
            )
            isExtractingFrames = false
            framesRes.onSuccess { frames ->
                extractedFrames = frames
                if (thumbnailUrl == null && frames.isNotEmpty()) {
                    val first = frames.first()
                    thumbnailUrl = first.dataUri
                    selectedThumbnailSource = "Video Frame (${MediaUtils.formatDurationSec(first.timeMs / 1000L)})"
                    frameSliderMs = first.timeMs.toFloat()
                }
            }.onFailure {
                extractedFrames = emptyList()
            }
        } else {
            previewPlayer.stop()
            extractedFrames = emptyList()
            isExtractingFrames = false
        }
    }

    val pickVideoLauncher = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            isImportingMedia = true
            statusMessage = null
            thumbnailUrl = null
            selectedThumbnailSource = null
            scope.launch {
                val res = MediaUtils.copyVideoToInternalStorage(context, uri)
                isImportingMedia = false
                res.onSuccess { (localFileUri, dur) ->
                    videoUrl = localFileUri
                    durationSec = dur
                    useUrlInput = false
                    frameSliderMs = 0f
                    statusMessage = "Video ready (${MediaUtils.formatDurationSec(dur)})"
                }.onFailure { err ->
                    statusMessage = err.localizedMessage ?: "Failed to load selected video"
                }
            }
        }
    }

    val pickThumbnailLauncher = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            isProcessingThumbnail = true
            scope.launch {
                val res = MediaUtils.encodeThumbnailToDataUri(context, uri)
                isProcessingThumbnail = false
                res.onSuccess { dataUri ->
                    thumbnailUrl = dataUri
                    selectedThumbnailSource = "Custom Gallery Cover"
                }.onFailure { err ->
                    statusMessage = err.localizedMessage ?: "Could not process selected cover image"
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Create & Publish",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Upload a Short Reel or Full Video with a custom cover thumbnail",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Format",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = selectedType == VideoType.SHORT,
                    onClick = { selectedType = VideoType.SHORT },
                    label = { Text("Short Reel (Vertical)") },
                    leadingIcon = { Icon(Icons.Filled.Movie, contentDescription = null) },
                    modifier = Modifier.testTag("upload_type_short")
                )
                FilterChip(
                    selected = selectedType == VideoType.FULL,
                    onClick = { selectedType = VideoType.FULL },
                    label = { Text("Full Video") },
                    leadingIcon = { Icon(Icons.Filled.VideoLibrary, contentDescription = null) },
                    modifier = Modifier.testTag("upload_type_full")
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        pickVideoLauncher.launch(PickVisualMediaRequest(PickVisualMedia.VideoOnly))
                    },
                    enabled = !isImportingMedia && !isSaving,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("pick_gallery_video_button")
                ) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Gallery Video")
                }

                OutlinedButton(
                    onClick = { useUrlInput = !useUrlInput },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_url_upload_button")
                ) {
                    Icon(Icons.Filled.Link, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (useUrlInput) "Hide URL" else "Stream URL")
                }
            }

            if (useUrlInput) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = videoUrl,
                    onValueChange = { videoUrl = it },
                    label = { Text("HTTPS MP4 / HLS Stream URL") },
                    placeholder = { Text("https://.../video.mp4") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("upload_video_url_input")
                )
            }

            if (isImportingMedia) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Preparing video from gallery...", style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (statusMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = statusMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            if (videoUrl.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(if (selectedType == VideoType.SHORT) 9f / 10f else 16f / 9f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = true
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setPlayer(previewPlayer)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Cover Thumbnail Studio (Shorts & Full Videos)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cover Thumbnail",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (selectedType == VideoType.SHORT) {
                                    "Choose a custom cover image or pick any frame from your Short"
                                } else {
                                    "Pick a custom gallery cover or frame from your Full Video"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (thumbnailUrl != null) {
                            TextButton(
                                onClick = {
                                    thumbnailUrl = null
                                    selectedThumbnailSource = null
                                },
                                modifier = Modifier.testTag("clear_thumbnail_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Clear thumbnail",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Clear")
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Live Cover Preview + Gallery Picker Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .then(
                                    if (selectedType == VideoType.SHORT) {
                                        Modifier.size(width = 76.dp, height = 104.dp)
                                    } else {
                                        Modifier.size(width = 132.dp, height = 76.dp)
                                    }
                                )
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (thumbnailUrl != null) 2.dp else 1.dp,
                                    color = if (thumbnailUrl != null) CoralPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (thumbnailUrl != null) {
                                VideoThumbnailImage(
                                    thumbnailUrl = thumbnailUrl,
                                    contentDescription = "Selected cover thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Image,
                                    contentDescription = "No cover selected",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isProcessingThumbnail || isCapturingCustomFrame) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.45f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = CoralPrimary,
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    pickThumbnailLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                                },
                                enabled = !isProcessingThumbnail && !isSaving,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("pick_thumbnail_gallery_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PhotoCamera,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Gallery Cover")
                            }

                            Text(
                                text = selectedThumbnailSource
                                    ?: "No cover selected yet",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (thumbnailUrl != null) MintTertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (thumbnailUrl != null) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }

                    // Video Frame Selector (available once a video is chosen)
                    if (videoUrl.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.BurstMode,
                                contentDescription = null,
                                tint = CoralPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Pick a Frame from Video",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (isExtractingFrames) {
                                Spacer(Modifier.width(8.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }

                        if (extractedFrames.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 2.dp)
                            ) {
                                itemsIndexed(extractedFrames) { idx, frame ->
                                    val isSelected = thumbnailUrl == frame.dataUri
                                    Box(
                                        modifier = Modifier
                                            .size(
                                                width = if (selectedType == VideoType.SHORT) 58.dp else 88.dp,
                                                height = if (selectedType == VideoType.SHORT) 78.dp else 54.dp
                                            )
                                            .clip(RoundedCornerShape(10.dp))
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) CoralPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                thumbnailUrl = frame.dataUri
                                                frameSliderMs = frame.timeMs.toFloat()
                                                selectedThumbnailSource = "Video Frame (${MediaUtils.formatDurationSec(frame.timeMs / 1000L)})"
                                            }
                                            .testTag("frame_thumbnail_$idx")
                                    ) {
                                        VideoThumbnailImage(
                                            thumbnailUrl = frame.dataUri,
                                            contentDescription = "Frame at ${MediaUtils.formatDurationSec(frame.timeMs / 1000L)}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.7f),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(3.dp)
                                        ) {
                                            Text(
                                                text = MediaUtils.formatDurationSec(frame.timeMs / 1000L),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = "Selected frame",
                                                tint = CoralPrimary,
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(3.dp)
                                                    .size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Timestamp Scrubber to capture any exact frame
                        val maxMs = (durationSec.coerceAtLeast(2L) * 1000L).toFloat()
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Scrub Frame: ${MediaUtils.formatDurationSec(frameSliderMs.toLong() / 1000L)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = {
                                    isCapturingCustomFrame = true
                                    val targetMs = frameSliderMs.toLong()
                                    scope.launch {
                                        val res = MediaUtils.extractVideoFrameAtMs(
                                            context = context,
                                            videoUrl = videoUrl,
                                            timeMs = targetMs
                                        )
                                        isCapturingCustomFrame = false
                                        res.onSuccess { extracted ->
                                            thumbnailUrl = extracted.dataUri
                                            selectedThumbnailSource = "Captured Frame (${MediaUtils.formatDurationSec(targetMs / 1000L)})"
                                        }.onFailure { err ->
                                            statusMessage = err.localizedMessage ?: "Failed to extract frame"
                                        }
                                    }
                                },
                                enabled = !isCapturingCustomFrame && !isSaving,
                                modifier = Modifier.testTag("capture_slider_frame_button")
                            ) {
                                Text("Use Frame @ ${MediaUtils.formatDurationSec(frameSliderMs.toLong() / 1000L)}")
                            }
                        }
                        Slider(
                            value = frameSliderMs.coerceIn(0f, maxMs),
                            onValueChange = { frameSliderMs = it },
                            valueRange = 0f..maxMs,
                            colors = SliderDefaults.colors(
                                thumbColor = CoralPrimary,
                                activeTrackColor = CoralPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("thumbnail_frame_slider")
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 120) title = it },
                label = { Text("Video Title") },
                placeholder = { Text("Give your video a catchy title") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("upload_title_input")
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = caption,
                onValueChange = { if (it.length <= 500) caption = it },
                label = { Text("Caption & Hashtags") },
                placeholder = { Text("Tell viewers what this video is about...") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("upload_caption_input")
            )

            Spacer(Modifier.height(10.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Tip: Gallery covers and captured video frames are synced across devices immediately. Uploading as @${myProfile?.username ?: "creator"}.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(Modifier.height(18.dp))

            val canSubmit = title.isNotBlank() &&
                videoUrl.isNotBlank() &&
                !isSaving &&
                !isImportingMedia &&
                !isProcessingThumbnail &&
                !isCapturingCustomFrame

            Button(
                onClick = {
                    onPublishVideo(
                        title,
                        caption,
                        videoUrl,
                        thumbnailUrl,
                        selectedType,
                        durationSec
                    ) {
                        title = ""
                        caption = ""
                        videoUrl = ""
                        thumbnailUrl = null
                        selectedThumbnailSource = null
                        extractedFrames = emptyList()
                        frameSliderMs = 0f
                        statusMessage = null
                    }
                },
                enabled = canSubmit,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CoralPrimary,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("publish_video_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Publishing...", fontWeight = FontWeight.Bold)
                } else {
                    Text(
                        text = "Publish ${if (selectedType == VideoType.SHORT) "Short" else "Full Video"}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
