package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.media.MediaUtils
import com.example.data.model.CreatorEarningsCalculator
import com.example.data.model.FeedVideoItem
import com.example.data.model.PublicProfile
import com.example.data.model.VideoType
import com.example.ui.components.VideoThumbnailImage
import com.example.ui.components.formatCompactCount
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.MintTertiary

@Composable
fun CreatorDashboardScreen(
    myProfile: PublicProfile?,
    myShorts: List<FeedVideoItem>,
    myFullVideos: List<FeedVideoItem>,
    followersCount: Int,
    subscribersCount: Int,
    availableBalanceUsd: Double = 0.0,
    onOpenWallet: (() -> Unit)? = null,
    onBack: () -> Unit,
    onOpenVideo: (FeedVideoItem) -> Unit
) {
    BackHandler(onBack = onBack)

    val allMyVideos = remember(myShorts, myFullVideos) {
        (myShorts + myFullVideos).sortedByDescending { it.post.viewsCount }
    }

    val summary = remember(myShorts, myFullVideos, followersCount, subscribersCount) {
        CreatorEarningsCalculator.compute(
            myShorts = myShorts,
            myFullVideos = myFullVideos,
            followersCount = followersCount,
            subscribersCount = subscribersCount
        )
    }
    val shortsViews = summary.shortsViews
    val fullViews = summary.fullViews
    val totalViews = summary.totalViews
    val totalLikes = summary.totalLikes

    val viewsEarnings = summary.viewsEarningsUsd
    val likesEarnings = summary.likesEarningsUsd
    val subsEarnings = summary.subsEarningsUsd
    val totalEarnings = summary.grossEarningsUsd

    val partnerTargetViews = 10_000L
    val tierName = when {
        totalViews >= 10_000L && subscribersCount >= 50 -> "Partner Pro"
        totalViews >= 1_000L || subscribersCount >= 5 -> "Rising Creator"
        else -> "Starter Studio"
    }
    val tierProgress = (totalViews.toFloat() / partnerTargetViews.toFloat()).coerceIn(0.02f, 1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("dashboard_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to profile"
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Creator Studio",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "@${myProfile?.username ?: "creator"} • Analytics & Earnings",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = tierName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Estimated Earnings Hero Card
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF1D132B), Color(0xFF14272B))
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Estimated Creator Earnings",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                                Icon(
                                    imageVector = Icons.Filled.AttachMoney,
                                    contentDescription = null,
                                    tint = MintTertiary
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = formatUsd(totalEarnings),
                                style = MaterialTheme.typography.displayLarge,
                                color = MintTertiary,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.testTag("dashboard_total_earnings")
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                EarningsBreakdownChip(
                                    label = "Views RPM",
                                    amount = formatUsd(viewsEarnings),
                                    modifier = Modifier.weight(1f)
                                )
                                EarningsBreakdownChip(
                                    label = "Likes Bonus",
                                    amount = formatUsd(likesEarnings),
                                    modifier = Modifier.weight(1f)
                                )
                                EarningsBreakdownChip(
                                    label = "Subs Bonus",
                                    amount = formatUsd(subsEarnings),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Rate: $2.50 / 1K views • $0.02 / like • $0.50 / subscriber",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.68f)
                            )

                            if (onOpenWallet != null) {
                                Spacer(Modifier.height(14.dp))
                                Button(
                                    onClick = onOpenWallet,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MintTertiary,
                                        contentColor = Color(0xFF002116)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .testTag("dashboard_open_wallet_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AccountBalanceWallet,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Wallet & Withdraw (${formatUsd(availableBalanceUsd)} available)",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. KPI 2x2 Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        KpiStatCard(
                            icon = Icons.Filled.Visibility,
                            label = "Total Views",
                            value = formatCompactCount(totalViews),
                            tint = CoralPrimary,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dashboard_total_views")
                        )
                        KpiStatCard(
                            icon = Icons.Filled.Favorite,
                            label = "Total Likes",
                            value = formatCompactCount(totalLikes),
                            tint = AmberSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        KpiStatCard(
                            icon = Icons.Filled.Subscriptions,
                            label = "Subscribers",
                            value = formatCompactCount(subscribersCount),
                            tint = MintTertiary,
                            modifier = Modifier.weight(1f)
                        )
                        KpiStatCard(
                            icon = Icons.Filled.Group,
                            label = "Followers",
                            value = formatCompactCount(followersCount),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 3. Partner Milestone Progress
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = null,
                                    tint = AmberSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Partner Pro Milestone",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "${formatCompactCount(totalViews)} / 10K views",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { tierProgress },
                            color = MintTertiary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(50))
                        )
                    }
                }
            }

            // 4. Views Comparison Chart (Shorts vs Full Videos)
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Views by Format",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Shorts (${formatCompactCount(shortsViews)}) vs Full Videos (${formatCompactCount(fullViews)})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))

                        val maxFormatViews = maxOf(shortsViews, fullViews, 1L).toFloat()
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                        ) {
                            val barWidth = (size.width * 0.24f).coerceAtMost(96.dp.toPx())
                            val gap = size.width * 0.18f
                            val startX = (size.width - (barWidth * 2 + gap)) / 2f
                            val maxH = size.height - 14.dp.toPx()

                            val shortsH = (shortsViews / maxFormatViews) * maxH
                            val fullH = (fullViews / maxFormatViews) * maxH

                            // Baseline track
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.25f),
                                start = Offset(0f, size.height - 2.dp.toPx()),
                                end = Offset(size.width, size.height - 2.dp.toPx()),
                                strokeWidth = 2.dp.toPx()
                            )

                            // Shorts Bar
                            drawRoundRect(
                                color = CoralPrimary,
                                topLeft = Offset(startX, size.height - 4.dp.toPx() - shortsH.coerceAtLeast(8.dp.toPx())),
                                size = Size(barWidth, shortsH.coerceAtLeast(8.dp.toPx())),
                                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                            )

                            // Full Videos Bar
                            drawRoundRect(
                                color = MintTertiary,
                                topLeft = Offset(
                                    startX + barWidth + gap,
                                    size.height - 4.dp.toPx() - fullH.coerceAtLeast(8.dp.toPx())
                                ),
                                size = Size(barWidth, fullH.coerceAtLeast(8.dp.toPx())),
                                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Text(
                                text = "Shorts (${myShorts.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = CoralPrimary
                            )
                            Text(
                                text = "Full Videos (${myFullVideos.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MintTertiary
                            )
                        }
                    }
                }
            }

            // 5. Per-Video Performance Breakdown
            item {
                Text(
                    text = "Video Performance (${allMyVideos.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (allMyVideos.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = "No Uploaded Videos Yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Upload Shorts or Full Videos using the + Upload tab to start earning from views and likes.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(allMyVideos, key = { it.post.id }) { item ->
                    val estVideoUsd = (item.post.viewsCount / 1000.0) * CreatorEarningsCalculator.RPM_PER_1K_VIEWS +
                        (item.likeCount * CreatorEarningsCalculator.BONUS_PER_LIKE)
                    val isShort = VideoType.fromWire(item.post.videoType) == VideoType.SHORT

                    Surface(
                        onClick = { onOpenVideo(item) },
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_video_row_${item.post.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 58.dp, height = 44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isShort) CoralPrimary.copy(alpha = 0.16f)
                                        else MintTertiary.copy(alpha = 0.16f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                VideoThumbnailImage(
                                    thumbnailUrl = item.post.thumbnailUrl,
                                    contentDescription = item.post.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.Black.copy(alpha = 0.55f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.post.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${if (isShort) "Short" else "Full"} • ${MediaUtils.formatDurationSec(item.post.durationSec)} • ${formatCompactCount(item.post.viewsCount)} views • ${item.likeCount} likes",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = formatUsd(estVideoUsd),
                                style = MaterialTheme.typography.titleMedium,
                                color = MintTertiary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EarningsBreakdownChip(
    label: String,
    amount: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1
            )
            Text(
                text = amount,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun KpiStatCard(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatUsd(amount: Double): String = CreatorEarningsCalculator.formatUsd(amount)
