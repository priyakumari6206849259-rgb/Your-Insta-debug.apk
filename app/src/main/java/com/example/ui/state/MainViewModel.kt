package com.example.ui.state

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.CuratedVideoCatalog
import com.example.data.model.FeedVideoItem
import com.example.data.model.FollowEdge
import com.example.data.model.LiveComment
import com.example.data.model.LiveStream
import com.example.data.model.LiveStreamMode
import com.example.data.model.PublicProfile
import com.example.data.model.StoryItem
import com.example.data.model.StoryMediaType
import com.example.data.model.SubscriptionEdge
import com.example.data.model.VideoLike
import com.example.data.model.VideoType
import com.example.data.model.WithdrawalPayoutMethod
import com.example.data.model.WithdrawalRequest
import com.example.data.repository.SocialRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class SocialDashboardSnapshot(
    val myProfile: PublicProfile?,
    val profilesById: Map<String, PublicProfile>,
    val discoverableUsers: List<PublicProfile>,
    val shortsFeed: List<FeedVideoItem>,
    val fullVideosFeed: List<FeedVideoItem>,
    val activeStories: List<StoryItem>,
    val liveStreams: List<LiveStream>,
    val myFollowersCount: Int,
    val myFollowingCount: Int,
    val mySubscribersCount: Int,
    val follows: List<FollowEdge>,
    val subscriptions: List<SubscriptionEdge>,
    val likes: List<VideoLike>
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: SocialRepository,
    val currentUserId: String
) : ViewModel() {

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _activeChatPeerId = MutableStateFlow<String?>(null)
    val activeChatPeerId: StateFlow<String?> = _activeChatPeerId.asStateFlow()

    private val _activeLiveStreamId = MutableStateFlow<String?>(null)
    val activeLiveStreamId: StateFlow<String?> = _activeLiveStreamId.asStateFlow()

    private val viewedVideoIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            Firebase.auth.currentUser?.let { user ->
                if (user.uid == currentUserId) {
                    repository.ensureSignedInProfile(user)
                }
            }
        }
    }

    private val coreSocialFlow = combine(
        repository.observeAllProfiles(),
        repository.observeVideos(),
        repository.observeFollows(),
        repository.observeSubscriptions(),
        repository.observeVideoLikes()
    ) { profiles, uploadedVideos, follows, subscriptions, likes ->
        val profilesById = profiles.associateBy { it.userId.ifBlank { it.id } }
        val myProfile = profilesById[currentUserId]

        val catalogIds = CuratedVideoCatalog.featuredPosts.map { it.id }.toSet()
        val combinedVideos = (uploadedVideos + CuratedVideoCatalog.featuredPosts)
            .distinctBy { it.id }

        val likesByVideo = likes.groupBy { it.videoId }
        val followersByTarget = follows.groupBy { it.targetUserId }
        val followingByUser = follows.groupBy { it.followerId }
        val subscribersByCreator = subscriptions.groupBy { it.creatorId }

        val enriched = combinedVideos.map { video ->
            val videoLikes = likesByVideo[video.id].orEmpty()
            val creatorFollowers = followersByTarget[video.creatorId].orEmpty()
            val creatorSubs = subscribersByCreator[video.creatorId].orEmpty()
            val resolvedProfile = profilesById[video.creatorId]
            val hydratedVideo = if (resolvedProfile != null) {
                video.copy(
                    creatorName = resolvedProfile.displayName,
                    creatorUsername = resolvedProfile.username,
                    creatorPhotoUrl = resolvedProfile.photoUrl
                )
            } else video

            FeedVideoItem(
                post = hydratedVideo,
                likeCount = videoLikes.size,
                likedByMe = videoLikes.any { it.userId == currentUserId },
                followingCreator = creatorFollowers.any { it.followerId == currentUserId },
                subscribedCreator = creatorSubs.any { it.subscriberId == currentUserId },
                creatorFollowerCount = creatorFollowers.size,
                creatorSubscriberCount = creatorSubs.size,
                isBuiltInCatalog = video.id in catalogIds
            )
        }

        SocialDashboardSnapshot(
            myProfile = myProfile,
            profilesById = profilesById,
            discoverableUsers = profiles.filter { it.userId != currentUserId },
            shortsFeed = enriched.filter { VideoType.fromWire(it.post.videoType) == VideoType.SHORT },
            fullVideosFeed = enriched.filter { VideoType.fromWire(it.post.videoType) == VideoType.FULL },
            activeStories = emptyList(),
            liveStreams = emptyList(),
            myFollowersCount = followersByTarget[currentUserId].orEmpty().size,
            myFollowingCount = followingByUser[currentUserId].orEmpty().size,
            mySubscribersCount = subscribersByCreator[currentUserId].orEmpty().size,
            follows = follows,
            subscriptions = subscriptions,
            likes = likes
        )
    }

    val dashboardState: StateFlow<UiState<SocialDashboardSnapshot>> = combine(
        coreSocialFlow,
        repository.observeActiveStories(),
        repository.observeLiveStreams()
    ) { core, stories, liveStreams ->
        core.copy(
            activeStories = stories,
            liveStreams = liveStreams
        )
    }
        .toUiStateFlow("Failed to load feed")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val chatThreadsState: StateFlow<UiState<List<ChatThread>>> = repository
        .observeChatThreads(currentUserId)
        .toUiStateFlow("Failed to load chats")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val activeMessagesState: StateFlow<UiState<List<ChatMessage>>> = _activeChatPeerId
        .flatMapLatest { peerId ->
            if (peerId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeMessages(currentUserId, peerId)
                    .toUiStateFlow("Failed to load messages")
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val liveCommentsState: StateFlow<UiState<List<LiveComment>>> = _activeLiveStreamId
        .flatMapLatest { streamId ->
            if (streamId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeLiveComments(streamId)
                    .toUiStateFlow("Failed to load live chat")
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val withdrawalsState: StateFlow<UiState<List<WithdrawalRequest>>> = repository
        .observeMyWithdrawals(currentUserId)
        .toUiStateFlow("Failed to load wallet withdrawals")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    fun openChat(peerUserId: String?) {
        _activeChatPeerId.value = peerUserId
    }

    fun sendMessage(peerUserId: String, text: String, onSent: () -> Unit = {}) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val result = repository.sendMessage(peerUserId, text)
            if (result.isSuccess) {
                onSent()
            } else {
                _events.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Failed to send message")
            }
        }
    }

    fun updateProfile(
        displayName: String,
        username: String,
        bio: String,
        photoDataUri: String?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.updateMyProfile(displayName, username, bio, photoDataUri)
            _isSaving.value = false
            if (result.isSuccess) {
                _events.tryEmit("Profile updated")
                onSuccess()
            } else {
                _events.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Could not update profile")
            }
        }
    }

    fun publishVideo(
        title: String,
        caption: String,
        videoUrl: String,
        thumbnailUrl: String?,
        videoType: VideoType,
        durationSec: Long,
        creatorProfile: PublicProfile?,
        onPublished: () -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.publishVideo(
                title = title,
                caption = caption,
                videoUrl = videoUrl,
                thumbnailUrl = thumbnailUrl,
                videoType = videoType,
                durationSec = durationSec,
                creatorProfile = creatorProfile
            )
            _isSaving.value = false
            if (result.isSuccess) {
                _events.tryEmit("Video published to ${if (videoType == VideoType.SHORT) "Shorts" else "Videos"}!")
                onPublished()
            } else {
                _events.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Upload failed")
            }
        }
    }

    fun submitWithdrawal(
        amountUsd: Double,
        payoutMethod: WithdrawalPayoutMethod,
        accountHolderName: String,
        upiId: String?,
        bankName: String?,
        accountNumber: String?,
        ifscCode: String?,
        note: String?,
        availableBalanceUsd: Double,
        onSubmitted: () -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.submitWithdrawalRequest(
                amountUsd = amountUsd,
                payoutMethod = payoutMethod,
                accountHolderName = accountHolderName,
                upiId = upiId,
                bankName = bankName,
                accountNumber = accountNumber,
                ifscCode = ifscCode,
                note = note,
                availableBalanceUsd = availableBalanceUsd
            )
            _isSaving.value = false
            if (result.isSuccess) {
                _events.tryEmit("Withdrawal request submitted!")
                onSubmitted()
            } else {
                _events.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Withdrawal request failed")
            }
        }
    }

    fun cancelWithdrawal(requestId: String) {
        viewModelScope.launch {
            val result = repository.cancelPendingWithdrawal(requestId)
            if (result.isSuccess) {
                _events.tryEmit("Pending withdrawal cancelled")
            } else {
                _events.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Could not cancel request")
            }
        }
    }

    fun recordVideoView(videoId: String) {
        if (videoId.isBlank() || videoId.startsWith("catalog_") || !viewedVideoIds.add(videoId)) return
        viewModelScope.launch {
            repository.recordVideoView(videoId)
        }
    }

    fun deleteVideo(videoId: String) {
        viewModelScope.launch {
            val result = repository.deleteVideo(videoId)
            if (result.isSuccess) {
                _events.tryEmit("Video removed")
            } else {
                _events.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Could not delete video")
            }
        }
    }

    fun publishStory(
        mediaType: StoryMediaType,
        mediaUrl: String,
        caption: String,
        creatorProfile: PublicProfile?,
        onPublished: () -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.publishStory(mediaType, mediaUrl, caption, creatorProfile)
            _isSaving.value = false
            if (result.isSuccess) {
                _events.tryEmit("Story shared! Visible for 24 hours")
                onPublished()
            } else {
                _events.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Failed to post story")
            }
        }
    }

    fun deleteStory(storyId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.deleteStory(storyId)
            if (result.isSuccess) {
                _events.tryEmit("Story removed")
                onDeleted()
            } else {
                _events.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Could not remove story")
            }
        }
    }

    fun startLiveStream(
        title: String,
        streamUrl: String?,
        streamMode: LiveStreamMode = LiveStreamMode.CAMERA,
        gameTitle: String? = null,
        hostProfile: PublicProfile?,
        onStarted: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.startLiveStream(
                title = title,
                streamUrl = streamUrl,
                streamMode = streamMode,
                gameTitle = gameTitle,
                hostProfile = hostProfile
            )
            _isSaving.value = false
            result.onSuccess { streamId ->
                _activeLiveStreamId.value = streamId
                onStarted(streamId)
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Could not start live stream")
            }
        }
    }

    fun endLiveStream(streamId: String, onEnded: () -> Unit = {}) {
        viewModelScope.launch {
            repository.endLiveStream(streamId)
            if (_activeLiveStreamId.value == streamId) {
                _activeLiveStreamId.value = null
            }
            _events.tryEmit("Live stream ended")
            onEnded()
        }
    }

    fun openLiveStream(streamId: String?, isHost: Boolean = false) {
        val previous = _activeLiveStreamId.value
        _activeLiveStreamId.value = streamId
        viewModelScope.launch {
            if (!isHost && previous != null && previous != streamId) {
                repository.updateLiveViewerDelta(previous, -1L)
            }
            if (!isHost && streamId != null && streamId != previous) {
                repository.updateLiveViewerDelta(streamId, 1L)
            }
        }
    }

    fun sendLiveComment(
        streamId: String,
        text: String,
        senderProfile: PublicProfile?,
        onSent: () -> Unit = {}
    ) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val result = repository.sendLiveComment(streamId, text, senderProfile)
            if (result.isSuccess) {
                onSent()
            } else {
                _events.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Failed to send comment")
            }
        }
    }

    fun toggleLike(videoId: String, currentlyLiked: Boolean) {
        viewModelScope.launch {
            val result = repository.toggleLike(videoId, currentlyLiked)
            if (result.isFailure) {
                _events.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Action failed")
            }
        }
    }

    fun toggleFollow(targetUserId: String, currentlyFollowing: Boolean) {
        if (targetUserId == currentUserId) {
            _events.tryEmit("You cannot follow your own profile")
            return
        }
        viewModelScope.launch {
            val result = repository.toggleFollow(targetUserId, currentlyFollowing)
            if (result.isFailure) {
                _events.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Could not update follow")
            }
        }
    }

    fun toggleSubscribe(creatorId: String, currentlySubscribed: Boolean) {
        if (creatorId == currentUserId) {
            _events.tryEmit("You cannot subscribe to your own channel")
            return
        }
        viewModelScope.launch {
            val result = repository.toggleSubscription(creatorId, currentlySubscribed)
            if (result.isFailure) {
                _events.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Could not update subscription")
            }
        }
    }

    private fun <T> Flow<T>.toUiStateFlow(fallbackMessage: String): Flow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, fallbackMessage, rootError)
                emit(UiState.Error(rootError.message ?: fallbackMessage))
            }

    private companion object {
        const val TAG = "MainVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
