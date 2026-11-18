package com.example.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.auth.authStateFlow
import com.example.data.auth.signOut
import com.example.data.model.CreatorEarningsCalculator
import com.example.data.model.FeedVideoItem
import com.example.data.model.LiveStream
import com.example.data.model.PublicProfile
import com.example.data.model.VideoType
import com.example.data.repository.SocialRepository
import com.example.ui.components.StartLiveDialog
import com.example.ui.components.StoriesAndLiveTopBar
import com.example.ui.components.StoryCreatorDialog
import com.example.ui.state.MainViewModel
import com.example.ui.state.SocialDashboardSnapshot
import com.example.ui.state.UiState
import com.example.ui.theme.CoralPrimary
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private enum class AppTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    SHORTS(R.string.nav_shorts, Icons.Filled.Whatshot, Icons.Outlined.Whatshot, "nav_shorts"),
    VIDEOS(R.string.nav_videos, Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle, "nav_videos"),
    UPLOAD(R.string.nav_upload, Icons.Filled.AddCircle, Icons.Outlined.AddCircleOutline, "nav_upload"),
    CHAT(R.string.nav_chat, Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat, "nav_chat"),
    PROFILE(R.string.nav_profile, Icons.Filled.Person, Icons.Outlined.Person, "nav_profile")
}

@Composable
fun AppRoot() {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedShell(currentUserId = user.uid)
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun AuthenticatedShell(currentUserId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: MainViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                MainViewModel(SocialRepository(db), currentUserId)
            }
        }
    )

    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val threadsState by viewModel.chatThreadsState.collectAsStateWithLifecycle()
    val activePeerId by viewModel.activeChatPeerId.collectAsStateWithLifecycle()
    val messagesState by viewModel.activeMessagesState.collectAsStateWithLifecycle()
    val liveCommentsState by viewModel.liveCommentsState.collectAsStateWithLifecycle()
    val withdrawalsState by viewModel.withdrawalsState.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(AppTab.SHORTS) }
    var selectedFullVideo by remember { mutableStateOf<FeedVideoItem?>(null) }
    var inspectedCreatorId by remember { mutableStateOf<String?>(null) }
    var showStoryCreator by remember { mutableStateOf(false) }
    var showStartLiveDialog by remember { mutableStateOf(false) }
    var activeStoryId by remember { mutableStateOf<String?>(null) }
    var broadcasterStreamId by remember { mutableStateOf<String?>(null) }
    var viewerStreamId by remember { mutableStateOf<String?>(null) }
    var showCreatorDashboard by remember { mutableStateOf(false) }
    var showWalletScreen by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!isWide) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        AppTab.entries.forEach { tab ->
                            val selected = currentTab == tab && inspectedCreatorId == null && !showCreatorDashboard && !showWalletScreen
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    inspectedCreatorId = null
                                    showCreatorDashboard = false
                                    showWalletScreen = false
                                    if (tab != AppTab.VIDEOS) selectedFullVideo = null
                                    if (tab != AppTab.CHAT) viewModel.openChat(null)
                                    currentTab = tab
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes), maxLines = 1) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWide) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        AppTab.entries.forEach { tab ->
                            val selected = currentTab == tab && inspectedCreatorId == null && !showCreatorDashboard && !showWalletScreen
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    inspectedCreatorId = null
                                    showCreatorDashboard = false
                                    showWalletScreen = false
                                    if (tab != AppTab.VIDEOS) selectedFullVideo = null
                                    if (tab != AppTab.CHAT) viewModel.openChat(null)
                                    currentTab = tab
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes), maxLines = 1) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (val state = dashboardState) {
                        is UiState.Loading -> {
                            CircularProgressIndicator(
                                color = CoralPrimary,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        is UiState.Error -> {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp)
                            )
                        }
                        is UiState.Success -> {
                            val snap = state.data
                            val myShortsOwned = snap.shortsFeed.filter { it.post.creatorId == currentUserId }
                            val myFullOwned = snap.fullVideosFeed.filter { it.post.creatorId == currentUserId }
                            val myWithdrawals = (withdrawalsState as? UiState.Success)?.data.orEmpty()
                            val myEarningsSummary = remember(
                                myShortsOwned,
                                myFullOwned,
                                snap.myFollowersCount,
                                snap.mySubscribersCount,
                                myWithdrawals
                            ) {
                                CreatorEarningsCalculator.compute(
                                    myShorts = myShortsOwned,
                                    myFullVideos = myFullOwned,
                                    followersCount = snap.myFollowersCount,
                                    subscribersCount = snap.mySubscribersCount,
                                    withdrawals = myWithdrawals
                                )
                            }

                            val creatorId = inspectedCreatorId
                            if (showWalletScreen) {
                                WalletEarningsScreen(
                                    myProfile = snap.myProfile,
                                    summary = myEarningsSummary,
                                    withdrawalsState = withdrawalsState,
                                    isSaving = isSaving,
                                    onBack = { showWalletScreen = false },
                                    onSubmitWithdrawal = { amountUsd, method, holder, upi, bankName, accNum, ifsc, note, onDone ->
                                        viewModel.submitWithdrawal(
                                            amountUsd = amountUsd,
                                            payoutMethod = method,
                                            accountHolderName = holder,
                                            upiId = upi,
                                            bankName = bankName,
                                            accountNumber = accNum,
                                            ifscCode = ifsc,
                                            note = note,
                                            availableBalanceUsd = myEarningsSummary.availableBalanceUsd,
                                            onSubmitted = onDone
                                        )
                                    },
                                    onCancelWithdrawal = viewModel::cancelWithdrawal
                                )
                            } else if (showCreatorDashboard) {
                                CreatorDashboardScreen(
                                    myProfile = snap.myProfile,
                                    myShorts = myShortsOwned,
                                    myFullVideos = myFullOwned,
                                    followersCount = snap.myFollowersCount,
                                    subscribersCount = snap.mySubscribersCount,
                                    availableBalanceUsd = myEarningsSummary.availableBalanceUsd,
                                    onOpenWallet = {
                                        showCreatorDashboard = false
                                        showWalletScreen = true
                                    },
                                    onBack = { showCreatorDashboard = false },
                                    onOpenVideo = { item ->
                                        showCreatorDashboard = false
                                        if (VideoType.fromWire(item.post.videoType) == VideoType.FULL) {
                                            selectedFullVideo = item
                                            currentTab = AppTab.VIDEOS
                                        } else {
                                            currentTab = AppTab.SHORTS
                                        }
                                    }
                                )
                            } else if (creatorId != null) {
                                CreatorProfileOverlay(
                                    creatorId = creatorId,
                                    currentUserId = currentUserId,
                                    snapshot = snap,
                                    isSaving = isSaving,
                                    availableBalanceUsd = myEarningsSummary.availableBalanceUsd,
                                    onBack = { inspectedCreatorId = null },
                                    onOpenCreatorDashboard = if (creatorId == currentUserId) {
                                        {
                                            inspectedCreatorId = null
                                            showCreatorDashboard = true
                                        }
                                    } else null,
                                    onOpenWallet = if (creatorId == currentUserId) {
                                        {
                                            inspectedCreatorId = null
                                            showWalletScreen = true
                                        }
                                    } else null,
                                    onUpdateProfile = { n, u, b, p, done ->
                                        viewModel.updateProfile(n, u, b, p, done)
                                    },
                                    onToggleFollow = { target, cur -> viewModel.toggleFollow(target, cur) },
                                    onToggleSubscribe = { target, cur -> viewModel.toggleSubscribe(target, cur) },
                                    onMessageCreator = { target ->
                                        inspectedCreatorId = null
                                        viewModel.openChat(target)
                                        currentTab = AppTab.CHAT
                                    },
                                    onOpenVideo = { item ->
                                        inspectedCreatorId = null
                                        if (VideoType.fromWire(item.post.videoType) == VideoType.FULL) {
                                            selectedFullVideo = item
                                            currentTab = AppTab.VIDEOS
                                        } else {
                                            currentTab = AppTab.SHORTS
                                        }
                                    },
                                    onDeleteVideo = { vid -> viewModel.deleteVideo(vid) },
                                    onSignOut = {
                                        signOut(credentialManager, scope)
                                    }
                                )
                            } else {
                                val topBarShared: @Composable (Boolean) -> Unit = { translucent ->
                                    StoriesAndLiveTopBar(
                                        myProfile = snap.myProfile,
                                        stories = snap.activeStories,
                                        liveStreams = snap.liveStreams,
                                        onAddStoryClick = { showStoryCreator = true },
                                        onGoLiveClick = { showStartLiveDialog = true },
                                        onOpenStory = { story -> activeStoryId = story.id },
                                        onOpenLiveStream = { stream ->
                                            if (stream.hostId == currentUserId) {
                                                viewModel.openLiveStream(stream.id, isHost = true)
                                                broadcasterStreamId = stream.id
                                            } else {
                                                viewModel.openLiveStream(stream.id, isHost = false)
                                                viewerStreamId = stream.id
                                            }
                                        },
                                        translucentOverlay = translucent
                                    )
                                }

                                val noFullScreenOverlay = activeStoryId == null &&
                                    broadcasterStreamId == null &&
                                    viewerStreamId == null

                                when (currentTab) {
                                    AppTab.SHORTS -> ShortsScreen(
                                        shorts = snap.shortsFeed,
                                        currentUserId = currentUserId,
                                        isScreenActive = noFullScreenOverlay,
                                        topBarOverlay = { topBarShared(true) },
                                        onVideoViewed = viewModel::recordVideoView,
                                        onToggleLike = viewModel::toggleLike,
                                        onToggleFollow = viewModel::toggleFollow,
                                        onToggleSubscribe = viewModel::toggleSubscribe,
                                        onOpenCreatorProfile = { cid -> inspectedCreatorId = cid },
                                        onMessageCreator = { cid ->
                                            viewModel.openChat(cid)
                                            currentTab = AppTab.CHAT
                                        },
                                        onOpenUpload = { currentTab = AppTab.UPLOAD }
                                    )

                                    AppTab.VIDEOS -> VideosScreen(
                                        videos = snap.fullVideosFeed,
                                        selectedVideo = selectedFullVideo,
                                        currentUserId = currentUserId,
                                        storiesTopBar = { topBarShared(false) },
                                        onVideoViewed = viewModel::recordVideoView,
                                        onSelectVideo = { selectedFullVideo = it },
                                        onToggleLike = viewModel::toggleLike,
                                        onToggleFollow = viewModel::toggleFollow,
                                        onToggleSubscribe = viewModel::toggleSubscribe,
                                        onOpenCreatorProfile = { cid -> inspectedCreatorId = cid },
                                        onMessageCreator = { cid ->
                                            selectedFullVideo = null
                                            viewModel.openChat(cid)
                                            currentTab = AppTab.CHAT
                                        }
                                    )

                                    AppTab.UPLOAD -> UploadScreen(
                                        myProfile = snap.myProfile,
                                        isSaving = isSaving,
                                        onPublishVideo = { title, caption, url, thumbnailUrl, type, dur, onDone ->
                                            viewModel.publishVideo(
                                                title = title,
                                                caption = caption,
                                                videoUrl = url,
                                                thumbnailUrl = thumbnailUrl,
                                                videoType = type,
                                                durationSec = dur,
                                                creatorProfile = snap.myProfile
                                            ) {
                                                onDone()
                                                currentTab = if (type == VideoType.SHORT) AppTab.SHORTS else AppTab.VIDEOS
                                            }
                                        }
                                    )

                                    AppTab.CHAT -> ChatScreen(
                                        currentUserId = currentUserId,
                                        discoverableUsers = snap.discoverableUsers,
                                        profilesById = snap.profilesById,
                                        threadsState = threadsState,
                                        activePeerId = activePeerId,
                                        messagesState = messagesState,
                                        onOpenChat = viewModel::openChat,
                                        onSendMessage = viewModel::sendMessage
                                    )

                                    AppTab.PROFILE -> {
                                        ProfileScreen(
                                            profile = snap.myProfile,
                                            isOwnProfile = true,
                                            followersCount = snap.myFollowersCount,
                                            followingCount = snap.myFollowingCount,
                                            subscribersCount = snap.mySubscribersCount,
                                            isFollowingCreator = false,
                                            isSubscribedCreator = false,
                                            userShorts = myShortsOwned,
                                            userFullVideos = myFullOwned,
                                            isSaving = isSaving,
                                            availableBalanceUsd = myEarningsSummary.availableBalanceUsd,
                                            onOpenCreatorDashboard = { showCreatorDashboard = true },
                                            onOpenWallet = { showWalletScreen = true },
                                            onUpdateProfile = viewModel::updateProfile,
                                            onToggleFollow = {},
                                            onToggleSubscribe = {},
                                            onMessageCreator = {},
                                            onOpenVideo = { item ->
                                                if (VideoType.fromWire(item.post.videoType) == VideoType.FULL) {
                                                    selectedFullVideo = item
                                                    currentTab = AppTab.VIDEOS
                                                } else {
                                                    currentTab = AppTab.SHORTS
                                                }
                                            },
                                            onDeleteVideo = viewModel::deleteVideo,
                                            onSignOut = {
                                                signOut(credentialManager, scope)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val currentSnap = (dashboardState as? UiState.Success)?.data
        if (currentSnap != null) {
            val openStoryId = activeStoryId
            if (openStoryId != null) {
                StoryViewerScreen(
                    stories = currentSnap.activeStories,
                    initialStoryId = openStoryId,
                    currentUserId = currentUserId,
                    onClose = { activeStoryId = null },
                    onDeleteStory = { sid ->
                        viewModel.deleteStory(sid) {
                            if (currentSnap.activeStories.size <= 1) {
                                activeStoryId = null
                            }
                        }
                    },
                    onMessageCreator = { cid ->
                        activeStoryId = null
                        viewModel.openChat(cid)
                        currentTab = AppTab.CHAT
                    }
                )
            }

            val hostStreamId = broadcasterStreamId
            if (hostStreamId != null) {
                val stream = currentSnap.liveStreams.find { it.id == hostStreamId }
                    ?: LiveStream(
                        id = hostStreamId,
                        hostId = currentUserId,
                        hostName = currentSnap.myProfile?.displayName ?: "Creator",
                        hostUsername = currentSnap.myProfile?.username ?: "creator",
                        hostPhotoUrl = currentSnap.myProfile?.photoUrl,
                        title = "Live Broadcast",
                        viewerCount = 1L,
                        live = true
                    )
                LiveBroadcasterScreen(
                    stream = stream,
                    commentsState = liveCommentsState,
                    onEndLive = {
                        viewModel.endLiveStream(hostStreamId) {
                            broadcasterStreamId = null
                        }
                    },
                    onSendComment = { text, onSent ->
                        viewModel.sendLiveComment(hostStreamId, text, currentSnap.myProfile, onSent)
                    }
                )
            }

            val viewStreamId = viewerStreamId
            if (viewStreamId != null) {
                val stream = currentSnap.liveStreams.find { it.id == viewStreamId }
                    ?: LiveStream(
                        id = viewStreamId,
                        hostId = "",
                        hostName = "Creator",
                        hostUsername = "creator",
                        title = "Live Stream",
                        viewerCount = 1L,
                        live = false
                    )
                val isFollowingHost = currentSnap.follows.any {
                    it.followerId == currentUserId && it.targetUserId == stream.hostId
                }
                val isSubscribedHost = currentSnap.subscriptions.any {
                    it.subscriberId == currentUserId && it.creatorId == stream.hostId
                }
                LiveViewerScreen(
                    stream = stream,
                    commentsState = liveCommentsState,
                    isFollowingHost = isFollowingHost,
                    isSubscribedHost = isSubscribedHost,
                    isOwnStream = stream.hostId == currentUserId,
                    onBack = {
                        viewModel.openLiveStream(null, isHost = false)
                        viewerStreamId = null
                    },
                    onToggleFollow = {
                        if (stream.hostId.isNotBlank()) {
                            viewModel.toggleFollow(stream.hostId, isFollowingHost)
                        }
                    },
                    onToggleSubscribe = {
                        if (stream.hostId.isNotBlank()) {
                            viewModel.toggleSubscribe(stream.hostId, isSubscribedHost)
                        }
                    },
                    onOpenHostProfile = {
                        if (stream.hostId.isNotBlank()) {
                            viewModel.openLiveStream(null, isHost = false)
                            viewerStreamId = null
                            inspectedCreatorId = stream.hostId
                        }
                    },
                    onSendComment = { text, onSent ->
                        viewModel.sendLiveComment(viewStreamId, text, currentSnap.myProfile, onSent)
                    }
                )
            }

            if (showStoryCreator) {
                StoryCreatorDialog(
                    isSaving = isSaving,
                    onDismiss = { showStoryCreator = false },
                    onPublishStory = { mediaType, mediaUrl, caption ->
                        viewModel.publishStory(
                            mediaType = mediaType,
                            mediaUrl = mediaUrl,
                            caption = caption,
                            creatorProfile = currentSnap.myProfile
                        ) {
                            showStoryCreator = false
                        }
                    }
                )
            }

            if (showStartLiveDialog) {
                StartLiveDialog(
                    isStarting = isSaving,
                    onDismiss = { showStartLiveDialog = false },
                    onStartLive = { title, streamUrl, mode, gameTitle ->
                        viewModel.startLiveStream(
                            title = title,
                            streamUrl = streamUrl,
                            streamMode = mode,
                            gameTitle = gameTitle,
                            hostProfile = currentSnap.myProfile
                        ) { streamId ->
                            showStartLiveDialog = false
                            broadcasterStreamId = streamId
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CreatorProfileOverlay(
    creatorId: String,
    currentUserId: String,
    snapshot: SocialDashboardSnapshot,
    isSaving: Boolean,
    availableBalanceUsd: Double = 0.0,
    onBack: () -> Unit,
    onOpenCreatorDashboard: (() -> Unit)? = null,
    onOpenWallet: (() -> Unit)? = null,
    onUpdateProfile: (String, String, String, String?, () -> Unit) -> Unit,
    onToggleFollow: (String, Boolean) -> Unit,
    onToggleSubscribe: (String, Boolean) -> Unit,
    onMessageCreator: (String) -> Unit,
    onOpenVideo: (FeedVideoItem) -> Unit,
    onDeleteVideo: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val isOwn = creatorId == currentUserId
    val creatorShorts = snapshot.shortsFeed.filter { it.post.creatorId == creatorId }
    val creatorFull = snapshot.fullVideosFeed.filter { it.post.creatorId == creatorId }
    val sampleVideoPost = (creatorShorts + creatorFull).firstOrNull()?.post

    val profile = snapshot.profilesById[creatorId] ?: PublicProfile(
        userId = creatorId,
        displayName = sampleVideoPost?.creatorName ?: "Creator",
        username = sampleVideoPost?.creatorUsername ?: "creator",
        bio = "Featured creator on Your Insta",
        photoUrl = sampleVideoPost?.creatorPhotoUrl
    )

    val followersCount = snapshot.follows.count { it.targetUserId == creatorId }
    val followingCount = snapshot.follows.count { it.followerId == creatorId }
    val subscribersCount = snapshot.subscriptions.count { it.creatorId == creatorId }
    val isFollowing = snapshot.follows.any { it.followerId == currentUserId && it.targetUserId == creatorId }
    val isSubscribed = snapshot.subscriptions.any { it.subscriberId == currentUserId && it.creatorId == creatorId }

    ProfileScreen(
        profile = profile,
        isOwnProfile = isOwn,
        followersCount = followersCount,
        followingCount = followingCount,
        subscribersCount = subscribersCount,
        isFollowingCreator = isFollowing,
        isSubscribedCreator = isSubscribed,
        userShorts = creatorShorts,
        userFullVideos = creatorFull,
        isSaving = isSaving,
        availableBalanceUsd = availableBalanceUsd,
        onBack = onBack,
        onOpenCreatorDashboard = onOpenCreatorDashboard,
        onOpenWallet = onOpenWallet,
        onUpdateProfile = onUpdateProfile,
        onToggleFollow = { onToggleFollow(creatorId, isFollowing) },
        onToggleSubscribe = { onToggleSubscribe(creatorId, isSubscribed) },
        onMessageCreator = { onMessageCreator(creatorId) },
        onOpenVideo = onOpenVideo,
        onDeleteVideo = onDeleteVideo,
        onSignOut = onSignOut
    )
}
