package com.example.live

import android.app.Application
import android.content.Context
import android.util.Log
import android.view.TextureView
import com.example.BuildConfig
import im.zego.zegoexpress.ZegoExpressEngine
import im.zego.zegoexpress.callback.IZegoEventHandler
import im.zego.zegoexpress.constants.ZegoAudioSourceType
import im.zego.zegoexpress.constants.ZegoPublishChannel
import im.zego.zegoexpress.constants.ZegoRoomStateChangedReason
import im.zego.zegoexpress.constants.ZegoScenario
import im.zego.zegoexpress.constants.ZegoUpdateType
import im.zego.zegoexpress.constants.ZegoVideoConfigPreset
import im.zego.zegoexpress.constants.ZegoVideoSourceType
import im.zego.zegoexpress.constants.ZegoViewMode
import im.zego.zegoexpress.entity.ZegoCanvas
import im.zego.zegoexpress.entity.ZegoEngineProfile
import im.zego.zegoexpress.entity.ZegoRoomConfig
import im.zego.zegoexpress.entity.ZegoScreenCaptureConfig
import im.zego.zegoexpress.entity.ZegoStream
import im.zego.zegoexpress.entity.ZegoUser
import im.zego.zegoexpress.entity.ZegoVideoConfig
import java.util.ArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class ZegoConnectionState {
    IDLE,
    LOCAL_STUDIO_ONLY,
    CONNECTING,
    CONNECTED,
    ERROR
}

class ZegoLiveManager(private val context: Context) {

    private var engine: ZegoExpressEngine? = null
    private var activeRoomId: String? = null
    private var activePublishStreamId: String? = null
    private var activePlayStreamId: String? = null
    private var isFrontCamera: Boolean = true

    private val _connectionState = MutableStateFlow(ZegoConnectionState.IDLE)
    val connectionState: StateFlow<ZegoConnectionState> = _connectionState.asStateFlow()

    private val _remoteStreamId = MutableStateFlow<String?>(null)
    val remoteStreamId: StateFlow<String?> = _remoteStreamId.asStateFlow()

    private val _isScreenCapturing = MutableStateFlow(false)
    val isScreenCapturing: StateFlow<Boolean> = _isScreenCapturing.asStateFlow()

    private val _statusNote = MutableStateFlow<String?>(null)
    val statusNote: StateFlow<String?> = _statusNote.asStateFlow()

    private val eventHandler = object : IZegoEventHandler() {
        override fun onRoomStateChanged(
            roomID: String?,
            reason: ZegoRoomStateChangedReason?,
            errorCode: Int,
            extendedData: JSONObject?
        ) {
            if (errorCode != 0) {
                _connectionState.value = ZegoConnectionState.ERROR
                _statusNote.value = "ZegoCloud room error ($errorCode): ${reason?.name.orEmpty()}"
                return
            }
            when (reason) {
                ZegoRoomStateChangedReason.LOGINED,
                ZegoRoomStateChangedReason.RECONNECTED -> {
                    _connectionState.value = ZegoConnectionState.CONNECTED
                    _statusNote.value = "ZegoCloud Live Connected • Room: ${roomID.orEmpty()}"
                }
                ZegoRoomStateChangedReason.LOGINING,
                ZegoRoomStateChangedReason.RECONNECTING -> {
                    _connectionState.value = ZegoConnectionState.CONNECTING
                }
                ZegoRoomStateChangedReason.LOGOUT,
                ZegoRoomStateChangedReason.KICK_OUT -> {
                    _connectionState.value = ZegoConnectionState.IDLE
                }
                else -> Unit
            }
        }

        override fun onRoomStreamUpdate(
            roomID: String?,
            updateType: ZegoUpdateType?,
            streamList: ArrayList<ZegoStream>?,
            extendedData: JSONObject?
        ) {
            val streams = streamList.orEmpty()
            if (updateType == ZegoUpdateType.ADD) {
                val incoming = streams.firstOrNull { it.streamID != activePublishStreamId }
                if (incoming != null) {
                    _remoteStreamId.value = incoming.streamID
                }
            } else if (updateType == ZegoUpdateType.DELETE) {
                val removedIds = streams.map { it.streamID }.toSet()
                val currentRemote = _remoteStreamId.value
                if (currentRemote != null && removedIds.contains(currentRemote)) {
                    runCatching { engine?.stopPlayingStream(currentRemote) }
                    _remoteStreamId.value = null
                    activePlayStreamId = null
                }
            }
        }
    }

    fun isZegoConfigured(): Boolean {
        val appId = parsedAppId()
        val appSign = BuildConfig.ZEGO_APP_SIGN.trim()
        return appId > 0L && appSign.isNotBlank() && appSign != "MY_ZEGO_APP_SIGN"
    }

    fun joinAsBroadcaster(
        roomId: String,
        userId: String,
        userName: String,
        enableCamera: Boolean
    ): Boolean {
        if (!isZegoConfigured()) {
            _connectionState.value = ZegoConnectionState.LOCAL_STUDIO_ONLY
            _statusNote.value = "Studio Mode Active • Set ZEGO_APP_ID & ZEGO_APP_SIGN in AI Studio Secrets"
            return false
        }
        return runCatching {
            val zego = ensureEngine()
            activeRoomId = roomId
            _connectionState.value = ZegoConnectionState.CONNECTING
            val zegoUser = ZegoUser(
                userId.ifBlank { "creator_${System.currentTimeMillis()}" },
                userName.ifBlank { "Creator" }
            )
            val roomConfig = ZegoRoomConfig().apply {
                isUserStatusNotify = true
            }
            zego.loginRoom(roomId, zegoUser, roomConfig) { errorCode, _ ->
                if (errorCode != 0) {
                    _connectionState.value = ZegoConnectionState.ERROR
                    _statusNote.value = "ZegoCloud loginRoom failed (code $errorCode). Check ZEGO_APP_ID / ZEGO_APP_SIGN."
                } else {
                    _connectionState.value = ZegoConnectionState.CONNECTED
                    _statusNote.value = "ZegoCloud Live Connected • Room: $roomId"
                    if (enableCamera) {
                        val streamId = "stream_${roomId}_main"
                        activePublishStreamId = streamId
                        zego.setVideoSource(ZegoVideoSourceType.CAMERA, ZegoPublishChannel.MAIN)
                        zego.setAudioSource(ZegoAudioSourceType.MICROPHONE, ZegoPublishChannel.MAIN)
                        zego.enableCamera(true)
                        zego.muteMicrophone(false)
                        zego.startPublishingStream(streamId)
                    }
                }
            }
            true
        }.getOrElse { err ->
            Log.e(TAG, "Failed to join ZegoCloud broadcaster room", err)
            _connectionState.value = ZegoConnectionState.ERROR
            _statusNote.value = err.localizedMessage ?: "ZegoCloud initialization error"
            false
        }
    }

    fun joinAsViewer(
        roomId: String,
        userId: String,
        userName: String
    ): Boolean {
        if (!isZegoConfigured()) {
            _connectionState.value = ZegoConnectionState.LOCAL_STUDIO_ONLY
            _statusNote.value = "Studio Viewer • Add ZEGO_APP_ID & ZEGO_APP_SIGN in Secrets for remote video"
            return false
        }
        return runCatching {
            val zego = ensureEngine()
            activeRoomId = roomId
            _connectionState.value = ZegoConnectionState.CONNECTING
            val zegoUser = ZegoUser(
                userId.ifBlank { "viewer_${System.currentTimeMillis()}" },
                userName.ifBlank { "Viewer" }
            )
            val roomConfig = ZegoRoomConfig().apply {
                isUserStatusNotify = true
            }
            zego.loginRoom(roomId, zegoUser, roomConfig) { errorCode, _ ->
                if (errorCode != 0) {
                    _connectionState.value = ZegoConnectionState.ERROR
                    _statusNote.value = "ZegoCloud viewer join failed (code $errorCode)"
                } else {
                    _connectionState.value = ZegoConnectionState.CONNECTED
                    _statusNote.value = "Watching ZegoCloud Room: $roomId"
                }
            }
            true
        }.getOrElse { err ->
            Log.e(TAG, "Failed to join ZegoCloud viewer room", err)
            _connectionState.value = ZegoConnectionState.ERROR
            _statusNote.value = err.localizedMessage
            false
        }
    }

    fun startGamingScreenCapture(
        roomId: String,
        fps: Int,
        captureSystemAudio: Boolean,
        captureMic: Boolean
    ): Result<Unit> {
        if (!isZegoConfigured()) {
            _isScreenCapturing.value = true
            return Result.success(Unit)
        }
        return runCatching {
            val zego = ensureEngine()
            val clampedFps = fps.coerceIn(15, 60)

            val videoConfig = ZegoVideoConfig(ZegoVideoConfigPreset.PRESET_720P).apply {
                this.fps = clampedFps
                this.bitrate = if (clampedFps >= 60) 2500 else 1500
            }
            zego.setVideoConfig(videoConfig, ZegoPublishChannel.MAIN)
            zego.setVideoSource(ZegoVideoSourceType.SCREEN_CAPTURE, ZegoPublishChannel.MAIN)
            zego.setAudioSource(
                if (captureSystemAudio) ZegoAudioSourceType.SCREEN_CAPTURE else ZegoAudioSourceType.MICROPHONE,
                ZegoPublishChannel.MAIN
            )
            zego.muteMicrophone(!captureMic)

            val captureConfig = ZegoScreenCaptureConfig().apply {
                captureVideo = true
                captureAudio = captureSystemAudio
            }
            zego.startScreenCapture(captureConfig)

            val streamId = "game_${roomId}_screen"
            activePublishStreamId = streamId
            zego.startPublishingStream(streamId)

            _isScreenCapturing.value = true
            _statusNote.value = "ZegoCloud Gaming Screen Live ($clampedFps FPS)"
        }.onFailure { err ->
            Log.w(TAG, "ZegoCloud screen capture start failed", err)
            _statusNote.value = err.localizedMessage ?: "Screen capture failed"
        }
    }

    fun stopGamingScreenCapture(resumeCamera: Boolean = false) {
        _isScreenCapturing.value = false
        val zego = engine ?: return
        runCatching {
            zego.stopScreenCapture()
            if (resumeCamera) {
                zego.setVideoSource(ZegoVideoSourceType.CAMERA, ZegoPublishChannel.MAIN)
                zego.setAudioSource(ZegoAudioSourceType.MICROPHONE, ZegoPublishChannel.MAIN)
            } else {
                zego.stopPublishingStream()
                activePublishStreamId = null
            }
        }
    }

    fun setMicEnabled(enabled: Boolean) {
        runCatching { engine?.muteMicrophone(!enabled) }
    }

    fun flipCamera() {
        isFrontCamera = !isFrontCamera
        runCatching { engine?.useFrontCamera(isFrontCamera) }
    }

    fun attachLocalVideo(textureView: TextureView) {
        val zego = engine ?: return
        val canvas = ZegoCanvas(textureView).apply {
            viewMode = ZegoViewMode.ASPECT_FILL
        }
        zego.startPreview(canvas)
    }

    fun attachRemoteVideo(textureView: TextureView, streamId: String) {
        val zego = engine ?: return
        val canvas = ZegoCanvas(textureView).apply {
            viewMode = ZegoViewMode.ASPECT_FIT
        }
        if (activePlayStreamId != streamId) {
            activePlayStreamId?.let { prev ->
                runCatching { zego.stopPlayingStream(prev) }
            }
            activePlayStreamId = streamId
        }
        zego.startPlayingStream(streamId, canvas)
    }

    fun leaveAndDestroy() {
        runCatching {
            val zego = engine ?: return@runCatching
            if (_isScreenCapturing.value) {
                zego.stopScreenCapture()
            }
            activePlayStreamId?.let { zego.stopPlayingStream(it) }
            activePublishStreamId?.let { zego.stopPublishingStream() }
            zego.stopPreview()
            activeRoomId?.let { zego.logoutRoom(it) } ?: zego.logoutRoom()
        }
        activeRoomId = null
        activePublishStreamId = null
        activePlayStreamId = null
        _isScreenCapturing.value = false
        _remoteStreamId.value = null
        _connectionState.value = ZegoConnectionState.IDLE
        runCatching {
            if (engine != null) {
                ZegoExpressEngine.destroyEngine(null)
            }
        }
        engine = null
    }

    private fun parsedAppId(): Long =
        BuildConfig.ZEGO_APP_ID.trim().toLongOrNull() ?: 0L

    private fun ensureEngine(): ZegoExpressEngine {
        engine?.let { return it }
        val profile = ZegoEngineProfile().apply {
            appID = parsedAppId()
            appSign = BuildConfig.ZEGO_APP_SIGN.trim()
            scenario = ZegoScenario.BROADCAST
            application = context.applicationContext as Application
        }
        return ZegoExpressEngine.createEngine(profile, eventHandler).also { engine = it }
    }

    private companion object {
        const val TAG = "ZegoLiveMgr"
    }
}
