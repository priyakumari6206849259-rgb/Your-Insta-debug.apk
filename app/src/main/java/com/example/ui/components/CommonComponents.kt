package com.example.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.CoralPrimary

@Composable
fun AvatarImage(
    photoUrl: String?,
    displayName: String,
    size: Dp = 44.dp,
    showRing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val ringModifier = if (showRing) {
        Modifier
            .border(
                width = 2.dp,
                brush = Brush.linearGradient(listOf(CoralPrimary, AmberSecondary)),
                shape = CircleShape
            )
            .padding(3.dp)
    } else Modifier

    val decodedBitmap = remember(photoUrl) {
        if (photoUrl != null && photoUrl.startsWith("data:image")) {
            runCatching {
                val base64Part = photoUrl.substringAfter("base64,", "")
                if (base64Part.isNotEmpty()) {
                    val bytes = Base64.decode(base64Part, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                } else null
            }.getOrNull()
        } else null
    }

    Box(
        modifier = modifier
            .size(size)
            .then(ringModifier)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            decodedBitmap != null -> {
                Image(
                    bitmap = decodedBitmap,
                    contentDescription = "$displayName avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            !photoUrl.isNullOrBlank() -> {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "$displayName avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                val initial = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "Y"
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun StatCounter(
    count: Int,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else Modifier

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .then(clickableModifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = formatCompactCount(count),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun FollowSubscribeActionRow(
    isFollowing: Boolean,
    isSubscribed: Boolean,
    isOwnContent: Boolean,
    onToggleFollow: () -> Unit,
    onToggleSubscribe: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isOwnContent) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isFollowing) {
            OutlinedButton(
                onClick = onToggleFollow,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("unfollow_button"),
                shape = RoundedCornerShape(50)
            ) {
                Text("Following", maxLines = 1)
            }
        } else {
            FilledTonalButton(
                onClick = onToggleFollow,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("follow_button"),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("Follow", maxLines = 1)
            }
        }

        if (isSubscribed) {
            OutlinedButton(
                onClick = onToggleSubscribe,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("unsubscribe_button"),
                shape = RoundedCornerShape(50)
            ) {
                Text("Subscribed", maxLines = 1)
            }
        } else {
            FilledTonalButton(
                onClick = onToggleSubscribe,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("subscribe_button"),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            ) {
                Text("Subscribe", maxLines = 1)
            }
        }
    }
}

@Composable
fun VideoThumbnailImage(
    thumbnailUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val decodedBitmap = remember(thumbnailUrl) {
        if (!thumbnailUrl.isNullOrBlank() && thumbnailUrl.startsWith("data:image")) {
            runCatching {
                val base64Part = thumbnailUrl.substringAfter("base64,", "")
                if (base64Part.isNotEmpty()) {
                    val bytes = Base64.decode(base64Part, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                } else null
            }.getOrNull()
        } else null
    }

    when {
        decodedBitmap != null -> {
            Image(
                bitmap = decodedBitmap,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = modifier
            )
        }
        !thumbnailUrl.isNullOrBlank() && !thumbnailUrl.startsWith("data:image") -> {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = modifier
            )
        }
        else -> {
            Box(
                modifier = modifier.background(
                    Brush.linearGradient(
                        listOf(Color(0xFF2B1325), Color(0xFF14182B))
                    )
                )
            )
        }
    }
}

fun formatCompactCount(count: Int): String = formatCompactCount(count.toLong())

fun formatCompactCount(count: Long): String = when {
    count >= 1_000_000L -> "%.1fM".format(count / 1_000_000f)
    count >= 1_000L -> "%.1fK".format(count / 1_000f)
    else -> count.toString()
}
