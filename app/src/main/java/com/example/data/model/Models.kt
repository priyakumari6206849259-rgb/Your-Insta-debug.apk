package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class VideoType(val wireValue: String) {
    SHORT("SHORT"),
    FULL("FULL");

    companion object {
        fun fromWire(raw: String?): VideoType =
            entries.firstOrNull { it.wireValue == raw } ?: SHORT
    }
}

enum class StoryMediaType(val wireValue: String) {
    IMAGE("IMAGE"),
    VIDEO("VIDEO");

    companion object {
        fun fromWire(raw: String?): StoryMediaType =
            entries.firstOrNull { it.wireValue == raw } ?: IMAGE
    }
}

enum class LiveStreamMode(val wireValue: String, val label: String) {
    CAMERA("CAMERA", "Camera Live"),
    MOBILE_GAME_SCREEN("MOBILE_GAME_SCREEN", "Mobile Game"),
    PC_GAME_STREAM("PC_GAME_STREAM", "PC / Console");

    companion object {
        fun fromWire(raw: String?): LiveStreamMode =
            entries.firstOrNull { it.wireValue == raw } ?: CAMERA
    }
}

enum class WithdrawalPayoutMethod(val wireValue: String, val label: String) {
    UPI("UPI", "UPI ID"),
    BANK_TRANSFER("BANK_TRANSFER", "Bank Transfer");

    companion object {
        fun fromWire(raw: String?): WithdrawalPayoutMethod =
            entries.firstOrNull { it.wireValue == raw } ?: UPI
    }
}

enum class WithdrawalStatus(val wireValue: String, val label: String) {
    PENDING("PENDING", "Pending"),
    PROCESSING("PROCESSING", "Processing"),
    COMPLETED("COMPLETED", "Completed"),
    REJECTED("REJECTED", "Rejected");

    companion object {
        fun fromWire(raw: String?): WithdrawalStatus =
            entries.firstOrNull { it.wireValue == raw } ?: PENDING
    }
}

@Keep
@IgnoreExtraProperties
data class PublicProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val username: String = "",
    val bio: String = "",
    val photoUrl: String? = null,
    val discoverable: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VideoPost(
    @DocumentId val id: String = "",
    val creatorId: String = "",
    val creatorName: String = "",
    val creatorUsername: String = "",
    val creatorPhotoUrl: String? = null,
    val title: String = "",
    val caption: String = "",
    val videoUrl: String = "",
    val thumbnailUrl: String? = null,
    val videoType: String = VideoType.SHORT.wireValue,
    val durationSec: Long = 0L,
    val viewsCount: Long = 0L,
    val published: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StoryItem(
    @DocumentId val id: String = "",
    val creatorId: String = "",
    val creatorName: String = "",
    val creatorUsername: String = "",
    val creatorPhotoUrl: String? = null,
    val mediaType: String = StoryMediaType.IMAGE.wireValue,
    val mediaUrl: String = "",
    val caption: String = "",
    val expiresAtEpochMs: Long = 0L,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LiveStream(
    @DocumentId val id: String = "",
    val hostId: String = "",
    val hostName: String = "",
    val hostUsername: String = "",
    val hostPhotoUrl: String? = null,
    val title: String = "",
    val streamUrl: String? = null,
    val streamMode: String = LiveStreamMode.CAMERA.wireValue,
    val gameTitle: String? = null,
    val agoraChannel: String? = null,
    val viewerCount: Long = 1L,
    val live: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LiveComment(
    @DocumentId val id: String = "",
    val streamId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderPhotoUrl: String? = null,
    val text: String = "",
    val visible: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FollowEdge(
    @DocumentId val id: String = "",
    val followerId: String = "",
    val targetUserId: String = "",
    val visible: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SubscriptionEdge(
    @DocumentId val id: String = "",
    val subscriberId: String = "",
    val creatorId: String = "",
    val visible: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VideoLike(
    @DocumentId val id: String = "",
    val userId: String = "",
    val videoId: String = "",
    val visible: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatThread(
    @DocumentId val id: String = "",
    val participantIds: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastSenderId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val recipientId: String = "",
    val participantIds: List<String> = emptyList(),
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class WithdrawalRequest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val amountUsd: Double = 0.0,
    val payoutMethod: String = WithdrawalPayoutMethod.UPI.wireValue,
    val accountHolderName: String = "",
    val upiId: String? = null,
    val bankName: String? = null,
    val accountNumber: String? = null,
    val ifscCode: String? = null,
    val note: String? = null,
    val status: String = WithdrawalStatus.PENDING.wireValue,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class FeedVideoItem(
    val post: VideoPost,
    val likeCount: Int,
    val likedByMe: Boolean,
    val followingCreator: Boolean,
    val subscribedCreator: Boolean,
    val creatorFollowerCount: Int,
    val creatorSubscriberCount: Int,
    val isBuiltInCatalog: Boolean = false
)

object CuratedVideoCatalog {
    val featuredPosts: List<VideoPost> = listOf(
        VideoPost(
            id = "catalog_short_blazes",
            creatorId = "creator_arjun_films",
            creatorName = "Arjun Visuals",
            creatorUsername = "arjunvisuals",
            creatorPhotoUrl = null,
            title = "Neon Drift Reels",
            caption = "High-contrast night action reel streaming in crisp HD. #shorts #cinema",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            thumbnailUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerBlazes.jpg",
            videoType = VideoType.SHORT.wireValue,
            durationSec = 15L,
            viewsCount = 18400L,
            published = true
        ),
        VideoPost(
            id = "catalog_short_escapes",
            creatorId = "creator_meera_studio",
            creatorName = "Meera Studio",
            creatorUsername = "meerastudio",
            creatorPhotoUrl = null,
            title = "Weekend Escape Cut",
            caption = "Quick travel transition reel shot on mobile. #travel #reels",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
            thumbnailUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerEscapes.jpg",
            videoType = VideoType.SHORT.wireValue,
            durationSec = 15L,
            viewsCount = 9250L,
            published = true
        ),
        VideoPost(
            id = "catalog_short_joy",
            creatorId = "creator_rohan_beats",
            creatorName = "Rohan Beats",
            creatorUsername = "rohanbeats",
            creatorPhotoUrl = null,
            title = "Morning Energy Loop",
            caption = "Feel-good rhythm montage for your morning feed. #vibes #shorts",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoy.mp4",
            thumbnailUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerJoy.jpg",
            videoType = VideoType.SHORT.wireValue,
            durationSec = 15L,
            viewsCount = 12300L,
            published = true
        ),
        VideoPost(
            id = "catalog_short_fun",
            creatorId = "creator_arjun_films",
            creatorName = "Arjun Visuals",
            creatorUsername = "arjunvisuals",
            creatorPhotoUrl = null,
            title = "Color Pop Story",
            caption = "Vibrant motion design breakdown in 60 seconds. #motion #design",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
            thumbnailUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerFun.jpg",
            videoType = VideoType.SHORT.wireValue,
            durationSec = 60L,
            viewsCount = 15600L,
            published = true
        ),
        VideoPost(
            id = "catalog_full_bunny",
            creatorId = "creator_blender_open",
            creatorName = "Open Cinema Hub",
            creatorUsername = "opencinemahub",
            creatorPhotoUrl = null,
            title = "Big Buck Bunny — Full Animated Short Film (HD)",
            caption = "Watch the full open-source animated comedy film with seamless adaptive buffering and seek controls.",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            thumbnailUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
            videoType = VideoType.FULL.wireValue,
            durationSec = 596L,
            viewsCount = 54200L,
            published = true
        ),
        VideoPost(
            id = "catalog_full_sintel",
            creatorId = "creator_meera_studio",
            creatorName = "Meera Studio",
            creatorUsername = "meerastudio",
            creatorPhotoUrl = null,
            title = "Sintel — Epic Fantasy Film (Full Feature)",
            caption = "A lone wanderer searches for a baby dragon across sweeping landscapes. Full-length streaming feature.",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
            thumbnailUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/Sintel.jpg",
            videoType = VideoType.FULL.wireValue,
            durationSec = 888L,
            viewsCount = 41800L,
            published = true
        ),
        VideoPost(
            id = "catalog_full_tears",
            creatorId = "creator_arjun_films",
            creatorName = "Arjun Visuals",
            creatorUsername = "arjunvisuals",
            creatorPhotoUrl = null,
            title = "Tears of Steel — Sci-Fi Live Action & VFX",
            caption = "Full sci-fi film exploring robotics, memory, and futuristic Amsterdam VFX.",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
            thumbnailUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/TearsOfSteel.jpg",
            videoType = VideoType.FULL.wireValue,
            durationSec = 734L,
            viewsCount = 31900L,
            published = true
        ),
        VideoPost(
            id = "catalog_full_subaru",
            creatorId = "creator_rohan_beats",
            creatorName = "Rohan Beats",
            creatorUsername = "rohanbeats",
            creatorPhotoUrl = null,
            title = "Street & Dirt Road Documentary",
            caption = "Full outdoor adventure documentary with crisp audio and smooth playback.",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4",
            thumbnailUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/SubaruOutbackOnStreetAndDirt.jpg",
            videoType = VideoType.FULL.wireValue,
            durationSec = 594L,
            viewsCount = 22400L,
            published = true
        )
    )
}
