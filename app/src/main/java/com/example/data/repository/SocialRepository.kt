package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.FollowEdge
import com.example.data.model.LiveComment
import com.example.data.model.LiveStream
import com.example.data.model.LiveStreamMode
import com.example.data.model.PublicProfile
import com.example.data.model.StoryItem
import com.example.data.model.StoryMediaType
import com.example.data.model.SubscriptionEdge
import com.example.data.model.VideoLike
import com.example.data.model.VideoPost
import com.example.data.model.VideoType
import com.example.data.model.WithdrawalPayoutMethod
import com.example.data.model.WithdrawalRequest
import com.example.data.model.WithdrawalStatus
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class SocialRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    fun threadIdFor(uidA: String, uidB: String): String =
        listOf(uidA, uidB).sorted().joinToString("_")

    suspend fun ensureSignedInProfile(user: FirebaseUser): Result<Unit> {
        val uid = user.uid
        val publicPath = "public_profiles/$uid"
        val privatePath = "users/$uid/private/account"
        return try {
            val publicRef = db.collection("public_profiles").document(uid)
            val publicSnap = publicRef.get().await()
            if (!publicSnap.exists()) {
                val rawName = user.displayName?.trim()?.take(60).orEmpty()
                val safeName = if (rawName.isNotBlank()) rawName else "Creator"
                val rawHandle = safeName.lowercase().replace(Regex("[^a-z0-9_]"), "")
                    .ifBlank { "creator_${uid.take(6)}" }
                    .take(30)
                    .let { if (it.length < 2) "${it}_1" else it }
                val photo = user.photoUrl?.toString()?.takeIf { it.length <= 250_000 }
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("displayName", safeName)
                    put("username", rawHandle)
                    put("bio", "Creating on Your Insta")
                    if (photo != null) put("photoUrl", photo)
                    put("discoverable", true)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                publicRef.set(payload).await()
            }

            val privateRef = db.collection("users").document(uid)
                .collection("private").document("account")
            val privateSnap = privateRef.get().await()
            if (!privateSnap.exists()) {
                val privatePayload = buildMap<String, Any> {
                    put("userId", uid)
                    val email = user.email?.trim()?.take(200)
                    if (!email.isNullOrBlank()) put("email", email)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                privateRef.set(privatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "$publicPath / $privatePath")
            Result.failure(e)
        }
    }

    suspend fun updateMyProfile(
        displayName: String,
        username: String,
        bio: String,
        photoDataUri: String?
    ): Result<Unit> {
        val cleanName = displayName.trim().take(60)
        val cleanUsername = username.trim().replace(" ", "").take(30)
        val cleanBio = bio.trim().take(240)
        require(cleanName.isNotEmpty()) { "Display name cannot be empty." }
        require(cleanUsername.length >= 2) { "Username must be at least 2 characters." }
        if (photoDataUri != null) {
            require(photoDataUri.length <= 250_000) { "Selected photo is too large." }
        }

        var path = "public_profiles/unknown"
        return try {
            val uid = requireUserId()
            val ref = db.collection("public_profiles").document(uid)
            path = ref.path
            val updateMap = buildMap<String, Any> {
                put("displayName", cleanName)
                put("username", cleanUsername)
                put("bio", cleanBio)
                if (photoDataUri != null) put("photoUrl", photoDataUri)
                put("updatedAt", FieldValue.serverTimestamp())
            }
            ref.update(updateMap).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeAllProfiles(): Flow<List<PublicProfile>> {
        val uid = requireUserId()
        val path = "public_profiles"
        return db.collection(path)
            .whereEqualTo("discoverable", true)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logStreamError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    fun observeVideos(): Flow<List<VideoPost>> {
        val uid = requireUserId()
        val path = "videos"
        return db.collection(path)
            .whereEqualTo("published", true)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(VideoPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                logStreamError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    suspend fun publishVideo(
        title: String,
        caption: String,
        videoUrl: String,
        thumbnailUrl: String? = null,
        videoType: VideoType,
        durationSec: Long,
        creatorProfile: PublicProfile?
    ): Result<Unit> {
        val cleanTitle = title.trim().take(120)
        val cleanCaption = caption.trim().take(500)
        val cleanUrl = videoUrl.trim().take(1000)
        val cleanThumb = thumbnailUrl?.trim()?.takeIf { it.isNotEmpty() }?.take(250_000)
        require(cleanTitle.isNotEmpty()) { "Video title is required." }
        require(cleanUrl.isNotEmpty()) { "Video source is required." }

        val videoId = "vid_${UUID.randomUUID().toString().replace("-", "")}"
        val path = "videos/$videoId"
        return try {
            val uid = requireUserId()
            val user = auth.currentUser
            val creatorName = (creatorProfile?.displayName?.takeIf { it.isNotBlank() }
                ?: user?.displayName?.takeIf { it.isNotBlank() }
                ?: "Creator").take(60)
            val creatorUsername = (creatorProfile?.username?.takeIf { it.length >= 2 }
                ?: "creator_${uid.take(6)}").take(30)
            val creatorPhoto = (creatorProfile?.photoUrl ?: user?.photoUrl?.toString())
                ?.takeIf { it.length <= 250_000 }

            val payload = buildMap<String, Any> {
                put("creatorId", uid)
                put("creatorName", creatorName)
                put("creatorUsername", creatorUsername)
                if (creatorPhoto != null) put("creatorPhotoUrl", creatorPhoto)
                put("title", cleanTitle)
                put("caption", cleanCaption)
                put("videoUrl", cleanUrl)
                if (cleanThumb != null) put("thumbnailUrl", cleanThumb)
                put("videoType", videoType.wireValue)
                put("durationSec", durationSec.coerceIn(0L, 86400L))
                put("viewsCount", 0L)
                put("published", true)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("videos").document(videoId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun recordVideoView(videoId: String): Result<Unit> {
        if (videoId.isBlank() || videoId.startsWith("catalog_")) return Result.success(Unit)
        val path = "videos/$videoId"
        return try {
            requireUserId()
            db.collection("videos").document(videoId)
                .update(
                    mapOf(
                        "viewsCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteVideo(videoId: String): Result<Unit> {
        val path = "videos/$videoId"
        return try {
            requireUserId()
            require(videoId.isNotBlank())
            db.collection("videos").document(videoId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- 24-Hour Stories ---
    fun observeActiveStories(): Flow<List<StoryItem>> {
        val uid = requireUserId()
        val path = "stories"
        return db.collection(path)
            .whereEqualTo("active", true)
            .snapshots()
            .map { snapshot ->
                val nowMs = System.currentTimeMillis()
                snapshot.toObjects(StoryItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { it.expiresAtEpochMs > nowMs }
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                logStreamError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    suspend fun publishStory(
        mediaType: StoryMediaType,
        mediaUrl: String,
        caption: String,
        creatorProfile: PublicProfile?
    ): Result<Unit> {
        val cleanUrl = mediaUrl.trim().take(500_000)
        val cleanCaption = caption.trim().take(200)
        require(cleanUrl.isNotEmpty()) { "Please choose a photo or video for your story." }

        val storyId = "story_${UUID.randomUUID().toString().replace("-", "")}"
        val path = "stories/$storyId"
        return try {
            val uid = requireUserId()
            val user = auth.currentUser
            val creatorName = (creatorProfile?.displayName?.takeIf { it.isNotBlank() }
                ?: user?.displayName?.takeIf { it.isNotBlank() }
                ?: "Creator").take(60)
            val creatorUsername = (creatorProfile?.username?.takeIf { it.length >= 2 }
                ?: "creator_${uid.take(6)}").take(30)
            val creatorPhoto = (creatorProfile?.photoUrl ?: user?.photoUrl?.toString())
                ?.takeIf { it.length <= 250_000 }
            val expiresAt = System.currentTimeMillis() + 24L * 60L * 60L * 1000L

            val payload = buildMap<String, Any> {
                put("creatorId", uid)
                put("creatorName", creatorName)
                put("creatorUsername", creatorUsername)
                if (creatorPhoto != null) put("creatorPhotoUrl", creatorPhoto)
                put("mediaType", mediaType.wireValue)
                put("mediaUrl", cleanUrl)
                put("caption", cleanCaption)
                put("expiresAtEpochMs", expiresAt)
                put("active", true)
                put("createdAt", FieldValue.serverTimestamp())
            }
            db.collection("stories").document(storyId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteStory(storyId: String): Result<Unit> {
        val path = "stories/$storyId"
        return try {
            requireUserId()
            require(storyId.isNotBlank())
            db.collection("stories").document(storyId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Live Streaming & Live Chat ---
    fun observeLiveStreams(): Flow<List<LiveStream>> {
        val uid = requireUserId()
        val path = "live_streams"
        return db.collection(path)
            .whereEqualTo("live", true)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(LiveStream::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                logStreamError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    suspend fun startLiveStream(
        title: String,
        streamUrl: String?,
        streamMode: LiveStreamMode = LiveStreamMode.CAMERA,
        gameTitle: String? = null,
        hostProfile: PublicProfile?
    ): Result<String> {
        val cleanTitle = title.trim().take(120).ifBlank { "Live Studio Session" }
        val cleanUrl = streamUrl?.trim()?.takeIf { it.isNotEmpty() }?.take(1000)
        val cleanGame = gameTitle?.trim()?.takeIf { it.isNotEmpty() }?.take(80)
        val streamId = "live_${UUID.randomUUID().toString().replace("-", "")}"
        val path = "live_streams/$streamId"
        return try {
            val uid = requireUserId()
            val user = auth.currentUser
            val hostName = (hostProfile?.displayName?.takeIf { it.isNotBlank() }
                ?: user?.displayName?.takeIf { it.isNotBlank() }
                ?: "Creator").take(60)
            val hostUsername = (hostProfile?.username?.takeIf { it.length >= 2 }
                ?: "creator_${uid.take(6)}").take(30)
            val hostPhoto = (hostProfile?.photoUrl ?: user?.photoUrl?.toString())
                ?.takeIf { it.length <= 250_000 }

            val payload = buildMap<String, Any> {
                put("hostId", uid)
                put("hostName", hostName)
                put("hostUsername", hostUsername)
                if (hostPhoto != null) put("hostPhotoUrl", hostPhoto)
                put("title", cleanTitle)
                if (cleanUrl != null) put("streamUrl", cleanUrl)
                put("streamMode", streamMode.wireValue)
                if (cleanGame != null) put("gameTitle", cleanGame)
                put("agoraChannel", streamId)
                put("viewerCount", 1L)
                put("live", true)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("live_streams").document(streamId).set(payload).await()
            Result.success(streamId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun endLiveStream(streamId: String): Result<Unit> {
        val path = "live_streams/$streamId"
        return try {
            requireUserId()
            require(streamId.isNotBlank())
            db.collection("live_streams").document(streamId)
                .update(
                    mapOf(
                        "live" to false,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateLiveViewerDelta(streamId: String, delta: Long): Result<Unit> {
        val path = "live_streams/$streamId"
        return try {
            requireUserId()
            require(streamId.isNotBlank() && (delta == 1L || delta == -1L))
            db.collection("live_streams").document(streamId)
                .update(
                    mapOf(
                        "viewerCount" to FieldValue.increment(delta),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeLiveComments(streamId: String): Flow<List<LiveComment>> {
        val uid = requireUserId()
        val path = "live_streams/$streamId/comments"
        return db.collection("live_streams")
            .document(streamId)
            .collection("comments")
            .whereEqualTo("visible", true)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(LiveComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                logStreamError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    suspend fun sendLiveComment(
        streamId: String,
        rawText: String,
        senderProfile: PublicProfile?
    ): Result<Unit> {
        val cleanText = rawText.trim().take(300)
        require(cleanText.isNotEmpty()) { "Comment cannot be empty." }
        val commentId = "cmt_${UUID.randomUUID().toString().replace("-", "")}"
        val path = "live_streams/$streamId/comments/$commentId"
        return try {
            val uid = requireUserId()
            val user = auth.currentUser
            val senderName = (senderProfile?.displayName?.takeIf { it.isNotBlank() }
                ?: user?.displayName?.takeIf { it.isNotBlank() }
                ?: "Viewer").take(60)
            val senderPhoto = (senderProfile?.photoUrl ?: user?.photoUrl?.toString())
                ?.takeIf { it.length <= 250_000 }

            val payload = buildMap<String, Any> {
                put("streamId", streamId)
                put("senderId", uid)
                put("senderName", senderName)
                if (senderPhoto != null) put("senderPhotoUrl", senderPhoto)
                put("text", cleanText)
                put("visible", true)
                put("createdAt", FieldValue.serverTimestamp())
            }
            db.collection("live_streams")
                .document(streamId)
                .collection("comments")
                .document(commentId)
                .set(payload)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- Social Graph & DM ---
    fun observeFollows(): Flow<List<FollowEdge>> {
        val uid = requireUserId()
        val path = "follows"
        return db.collection(path)
            .whereEqualTo("visible", true)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(FollowEdge::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logStreamError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    suspend fun toggleFollow(targetUserId: String, currentlyFollowing: Boolean): Result<Unit> {
        var path = "follows"
        return try {
            val uid = requireUserId()
            require(targetUserId.isNotBlank() && targetUserId != uid) { "Cannot follow your own account." }
            val followId = "${uid}_${targetUserId}"
            val docRef = db.collection("follows").document(followId)
            path = docRef.path
            if (currentlyFollowing) {
                docRef.delete().await()
            } else {
                docRef.set(
                    mapOf(
                        "followerId" to uid,
                        "targetUserId" to targetUserId,
                        "visible" to true,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeSubscriptions(): Flow<List<SubscriptionEdge>> {
        val uid = requireUserId()
        val path = "subscriptions"
        return db.collection(path)
            .whereEqualTo("visible", true)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(SubscriptionEdge::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logStreamError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    suspend fun toggleSubscription(creatorId: String, currentlySubscribed: Boolean): Result<Unit> {
        var path = "subscriptions"
        return try {
            val uid = requireUserId()
            require(creatorId.isNotBlank() && creatorId != uid) { "Cannot subscribe to your own account." }
            val subId = "${uid}_${creatorId}"
            val docRef = db.collection("subscriptions").document(subId)
            path = docRef.path
            if (currentlySubscribed) {
                docRef.delete().await()
            } else {
                docRef.set(
                    mapOf(
                        "subscriberId" to uid,
                        "creatorId" to creatorId,
                        "visible" to true,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeVideoLikes(): Flow<List<VideoLike>> {
        val uid = requireUserId()
        val path = "video_likes"
        return db.collection(path)
            .whereEqualTo("visible", true)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(VideoLike::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logStreamError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    suspend fun toggleLike(videoId: String, currentlyLiked: Boolean): Result<Unit> {
        var path = "video_likes"
        return try {
            val uid = requireUserId()
            require(videoId.isNotBlank())
            val likeId = "${uid}_${videoId}"
            val docRef = db.collection("video_likes").document(likeId)
            path = docRef.path
            if (currentlyLiked) {
                docRef.delete().await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "videoId" to videoId,
                        "visible" to true,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeChatThreads(uid: String): Flow<List<ChatThread>> {
        val path = "chat_threads"
        return db.collection(path)
            .whereArrayContains("participantIds", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ChatThread::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                logStreamError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    fun observeMessages(uid: String, peerUserId: String): Flow<List<ChatMessage>> {
        val threadId = threadIdFor(uid, peerUserId)
        val path = "chat_threads/$threadId/messages"
        return db.collection("chat_threads")
            .document(threadId)
            .collection("messages")
            .whereArrayContains("participantIds", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                logStreamError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    suspend fun sendMessage(peerUserId: String, rawText: String): Result<Unit> {
        val cleanText = rawText.trim().take(1000)
        require(cleanText.isNotEmpty()) { "Message cannot be empty." }
        var path = "chat_threads"
        return try {
            val uid = requireUserId()
            require(peerUserId.isNotBlank() && peerUserId != uid) { "Invalid chat recipient." }
            val threadId = threadIdFor(uid, peerUserId)
            val participants = listOf(uid, peerUserId).sorted()
            val threadRef = db.collection("chat_threads").document(threadId)
            path = threadRef.path

            val existingThread = threadRef.get().await()
            if (!existingThread.exists()) {
                threadRef.set(
                    mapOf(
                        "participantIds" to participants,
                        "lastMessage" to cleanText,
                        "lastSenderId" to uid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                threadRef.update(
                    mapOf(
                        "lastMessage" to cleanText,
                        "lastSenderId" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val messageId = "msg_${UUID.randomUUID().toString().replace("-", "")}"
            val msgRef = threadRef.collection("messages").document(messageId)
            path = msgRef.path
            msgRef.set(
                mapOf(
                    "senderId" to uid,
                    "recipientId" to peerUserId,
                    "participantIds" to participants,
                    "text" to cleanText,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- Creator Wallet & Withdrawals ---
    fun observeMyWithdrawals(uid: String): Flow<List<WithdrawalRequest>> {
        val path = "users/$uid/withdrawal_requests"
        return db.collection("users")
            .document(uid)
            .collection("withdrawal_requests")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(WithdrawalRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                logStreamError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    suspend fun submitWithdrawalRequest(
        amountUsd: Double,
        payoutMethod: WithdrawalPayoutMethod,
        accountHolderName: String,
        upiId: String?,
        bankName: String?,
        accountNumber: String?,
        ifscCode: String?,
        note: String?,
        availableBalanceUsd: Double
    ): Result<Unit> {
        val cleanHolder = accountHolderName.trim().take(80)
        val cleanUpi = upiId?.trim()?.take(100)
        val cleanBank = bankName?.trim()?.take(80)
        val cleanAcc = accountNumber?.trim()?.replace(" ", "")?.take(32)
        val cleanIfsc = ifscCode?.trim()?.uppercase()?.take(20)
        val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }?.take(160)
        val roundedAmount = kotlin.math.round(amountUsd * 100.0) / 100.0

        val reqId = "wd_${UUID.randomUUID().toString().replace("-", "")}"
        var path = "users/unknown/withdrawal_requests/$reqId"
        return try {
            val uid = requireUserId()
            require(roundedAmount >= 1.0) { "Minimum withdrawal amount is $1.00." }
            require(roundedAmount <= availableBalanceUsd + 0.001) { "Requested amount exceeds available wallet balance." }
            require(cleanHolder.length >= 2) { "Account holder name is required." }

            if (payoutMethod == WithdrawalPayoutMethod.UPI) {
                require(!cleanUpi.isNullOrBlank() && Regex("^[^\\s@]+@[^\\s@]+$").matches(cleanUpi)) {
                    "Enter a valid UPI ID (e.g., name@okaxis)."
                }
            } else {
                require(!cleanBank.isNullOrBlank() && cleanBank.length >= 2) { "Bank name is required." }
                require(!cleanAcc.isNullOrBlank() && cleanAcc.length >= 6) { "Enter a valid bank account number (min 6 digits)." }
                require(!cleanIfsc.isNullOrBlank() && cleanIfsc.length >= 4) { "Enter a valid IFSC / routing code." }
            }

            val docRef = db.collection("users").document(uid)
                .collection("withdrawal_requests").document(reqId)
            path = docRef.path

            val payload = buildMap<String, Any> {
                put("userId", uid)
                put("amountUsd", roundedAmount)
                put("payoutMethod", payoutMethod.wireValue)
                put("accountHolderName", cleanHolder)
                if (payoutMethod == WithdrawalPayoutMethod.UPI && !cleanUpi.isNullOrBlank()) {
                    put("upiId", cleanUpi)
                }
                if (payoutMethod == WithdrawalPayoutMethod.BANK_TRANSFER) {
                    if (!cleanBank.isNullOrBlank()) put("bankName", cleanBank)
                    if (!cleanAcc.isNullOrBlank()) put("accountNumber", cleanAcc)
                    if (!cleanIfsc.isNullOrBlank()) put("ifscCode", cleanIfsc)
                }
                if (cleanNote != null) put("note", cleanNote)
                put("status", WithdrawalStatus.PENDING.wireValue)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun cancelPendingWithdrawal(requestId: String): Result<Unit> {
        var path = "users/unknown/withdrawal_requests/$requestId"
        return try {
            val uid = requireUserId()
            require(requestId.isNotBlank())
            val docRef = db.collection("users").document(uid)
                .collection("withdrawal_requests").document(requestId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun logStreamError(
        error: Throwable,
        op: OperationType,
        path: String,
        uid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val toLog = firestoreError ?: (error as? Exception)
        if (toLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(toLog, op, path)
        }
    }
}
