package com.example.data.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

object MediaUtils {

    @OptIn(UnstableApi::class)
    fun createSmoothExoPlayer(context: Context): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                5_000,   // minBufferMs
                30_000,  // maxBufferMs
                1_000,   // bufferForPlaybackMs (fast start)
                2_000    // bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    suspend fun copyVideoToInternalStorage(
        context: Context,
        sourceUri: Uri
    ): Result<Pair<String, Long>> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "uploaded_videos").apply { mkdirs() }
            val destFile = File(dir, "vid_${UUID.randomUUID()}.mp4")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: error("Unable to read selected video from gallery")

            val durationSec = extractVideoDurationSec(context, Uri.fromFile(destFile))
            Uri.fromFile(destFile).toString() to durationSec
        }
    }

    private fun extractVideoDurationSec(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            (ms / 1000L).coerceIn(1L, 86400L)
        } catch (_: Exception) {
            15L
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun encodeImageToCompactDataUri(
        context: Context,
        imageUri: Uri
    ): Result<String> = encodeBitmapToDataUri(context, imageUri, maxDim = 240, maxBytes = 110_000)

    suspend fun encodeThumbnailToDataUri(
        context: Context,
        imageUri: Uri
    ): Result<String> = encodeBitmapToDataUri(context, imageUri, maxDim = 540, maxBytes = 140_000)

    suspend fun encodeStoryPhotoToDataUri(
        context: Context,
        imageUri: Uri
    ): Result<String> = encodeBitmapToDataUri(context, imageUri, maxDim = 720, maxBytes = 320_000)

    data class ExtractedVideoFrame(
        val timeMs: Long,
        val dataUri: String
    )

    suspend fun extractVideoFrames(
        context: Context,
        videoUrl: String,
        durationSec: Long,
        count: Int = 6
    ): Result<List<ExtractedVideoFrame>> = withContext(Dispatchers.IO) {
        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                setRetrieverDataSource(retriever, context, videoUrl)
                val metaDurMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                val totalMs = (metaDurMs ?: (durationSec * 1000L)).coerceAtLeast(1_500L)
                val safeCount = count.coerceIn(1, 10)

                val fractions = if (safeCount == 1) {
                    listOf(0.15f)
                } else {
                    (0 until safeCount).map { idx ->
                        0.08f + (0.84f * idx.toFloat() / (safeCount - 1).toFloat())
                    }
                }

                fractions.mapNotNull { frac ->
                    val timeMs = (totalMs * frac).toLong().coerceIn(0L, totalMs)
                    val frame = retriever.getFrameAtTime(
                        timeMs * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    ) ?: return@mapNotNull null
                    val dataUri = encodeRawBitmapToDataUri(frame, maxDim = 480, maxBytes = 120_000)
                    frame.recycle()
                    ExtractedVideoFrame(timeMs = timeMs, dataUri = dataUri)
                }
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    suspend fun extractVideoFrameAtMs(
        context: Context,
        videoUrl: String,
        timeMs: Long
    ): Result<ExtractedVideoFrame> = withContext(Dispatchers.IO) {
        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                setRetrieverDataSource(retriever, context, videoUrl)
                val safeTimeMs = timeMs.coerceAtLeast(0L)
                val frame = retriever.getFrameAtTime(
                    safeTimeMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: retriever.getFrameAtTime(
                    safeTimeMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                ) ?: error("Could not extract frame from this video.")
                val dataUri = encodeRawBitmapToDataUri(frame, maxDim = 480, maxBytes = 120_000)
                frame.recycle()
                ExtractedVideoFrame(timeMs = safeTimeMs, dataUri = dataUri)
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    private fun setRetrieverDataSource(
        retriever: MediaMetadataRetriever,
        context: Context,
        videoUrl: String
    ) {
        val trimmed = videoUrl.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            retriever.setDataSource(trimmed, emptyMap())
        } else {
            retriever.setDataSource(context, Uri.parse(trimmed))
        }
    }

    private suspend fun encodeBitmapToDataUri(
        context: Context,
        imageUri: Uri,
        maxDim: Int,
        maxBytes: Int
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(imageUri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOpts)
            }
            val srcMax = maxOf(boundsOpts.outWidth, boundsOpts.outHeight).coerceAtLeast(1)
            val sampleSize = (srcMax / maxDim).coerceAtLeast(1)

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = context.contentResolver.openInputStream(imageUri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: error("Could not decode image")

            val result = encodeRawBitmapToDataUri(decoded, maxDim = maxDim, maxBytes = maxBytes)
            decoded.recycle()
            result
        }
    }

    private fun encodeRawBitmapToDataUri(
        source: Bitmap,
        maxDim: Int,
        maxBytes: Int
    ): String {
        val scaled = scaleDownToSquareMax(source, maxDim)
        var quality = 85
        var bytes: ByteArray
        do {
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            quality -= 10
        } while (bytes.size > maxBytes && quality >= 30)
        if (scaled !== source) scaled.recycle()

        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:image/jpeg;base64,$b64"
    }

    private fun scaleDownToSquareMax(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxEdge && h <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / maxOf(w, h).toFloat()
        val nw = (w * ratio).toInt().coerceAtLeast(1)
        val nh = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, nw, nh, true)
    }

    fun formatDurationSec(totalSeconds: Long): String {
        val safe = totalSeconds.coerceAtLeast(0L)
        val hrs = safe / 3600
        val mins = (safe % 3600) / 60
        val secs = safe % 60
        return if (hrs > 0) {
            "%d:%02d:%02d".format(hrs, mins, secs)
        } else {
            "%d:%02d".format(mins, secs)
        }
    }

    fun shareVideo(context: Context, title: String, creatorName: String, videoUrl: String) {
        val shareText = buildString {
            append("Watch \"$title\" by $creatorName on Your Insta!")
            if (videoUrl.startsWith("http")) {
                append("\n$videoUrl")
            }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(intent, "Share Video"))
    }
}
