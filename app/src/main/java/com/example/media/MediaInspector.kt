package com.example.media

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.util.Locale

data class MediaMetaInfo(
    val fileName: String,
    val mimeType: String,
    val durationMs: Long,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val fileSizeBytes: Long = -1L,
    val isVideo: Boolean = true
) {
    val formatLabel: String
        get() {
            val ext = fileName.substringAfterLast('.', "").uppercase(Locale.US)
            if (ext.isNotBlank() && ext.length <= 5) return ext
            return when {
                mimeType.contains("mp4") -> "MP4"
                mimeType.contains("matroska") -> "MKV"
                mimeType.contains("webm") -> "WEBM"
                mimeType.contains("quicktime") -> "MOV"
                mimeType.contains("3gpp") -> "3GP"
                mimeType.contains("avi") || mimeType.contains("msvideo") -> "AVI"
                mimeType.contains("flv") -> "FLV"
                mimeType.contains("mp2ts") -> "TS"
                mimeType.contains("mpeg") -> "MPG"
                mimeType.startsWith("audio/") -> "AUDIO"
                else -> "VIDEO"
            }
        }

    val qualityLabel: String
        get() {
            if (!isVideo) return "Audio"
            val shortSide = minOf(videoWidth, videoHeight).takeIf { it > 0 } ?: maxOf(videoWidth, videoHeight)
            return when {
                shortSide >= 2160 -> "4K UHD"
                shortSide >= 1440 -> "2K QHD"
                shortSide >= 1080 -> "1080p Full HD"
                shortSide >= 720 -> "720p HD"
                shortSide >= 480 -> "480p SD"
                shortSide >= 360 -> "360p SD"
                shortSide >= 240 -> "240p Low"
                shortSide > 0 -> "${shortSide}p Low"
                else -> "Video"
            }
        }

    val sizeLabel: String
        get() = when {
            fileSizeBytes <= 0L -> ""
            fileSizeBytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", fileSizeBytes / (1024.0 * 1024.0))
            fileSizeBytes >= 1024L -> "${fileSizeBytes / 1024L} KB"
            else -> "$fileSizeBytes B"
        }

    fun summaryBadge(): String {
        val parts = buildList {
            add(formatLabel)
            if (qualityLabel != "Video") add(qualityLabel)
            add("${(durationMs / 1000L).coerceAtLeast(1L)}s")
            if (sizeLabel.isNotBlank()) add(sizeLabel)
        }
        return parts.joinToString(" • ")
    }
}

fun persistReadPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
}

fun inspectMediaUri(context: Context, uri: Uri, fallbackMime: String): MediaMetaInfo {
    val resolver = context.contentResolver
    var fileName = "movie_clip"
    var fileSizeBytes = -1L

    runCatching {
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0) fileName = cursor.getString(nameIdx) ?: fileName
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                    fileSizeBytes = cursor.getLong(sizeIdx)
                }
            }
        }
    }

    if (fileSizeBytes <= 0L) {
        runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                if (afd.length > 0L) fileSizeBytes = afd.length
            }
        }
    }

    val rawMime = resolver.getType(uri).orEmpty()
    val resolvedMime = normalizeMediaMimeType(rawMime, fileName, fallbackMime)

    var durationMs = 15_000L
    var width = 0
    var height = 0
    var hasVideoTrack = resolvedMime.startsWith("video/")

    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        val rawDur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        if (rawDur != null && rawDur > 0L) durationMs = rawDur

        width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val hasVideoMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
        if (hasVideoMeta.equals("yes", ignoreCase = true) || width > 0 || height > 0) {
            hasVideoTrack = true
        }
    } catch (_: Exception) {
    } finally {
        runCatching { retriever.release() }
    }

    val finalMime = if (hasVideoTrack && !resolvedMime.startsWith("video/")) {
        normalizeMediaMimeType("", fileName, "video/mp4")
    } else {
        resolvedMime
    }

    return MediaMetaInfo(
        fileName = fileName,
        mimeType = finalMime,
        durationMs = durationMs,
        videoWidth = width,
        videoHeight = height,
        fileSizeBytes = fileSizeBytes,
        isVideo = hasVideoTrack
    )
}

fun normalizeMediaMimeType(rawMime: String, fileName: String, fallbackMime: String): String {
    if (rawMime.startsWith("video/") || rawMime.startsWith("audio/")) {
        return rawMime
    }
    return when (fileName.substringAfterLast('.', "").lowercase(Locale.US)) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "3gp", "3gpp", "3g2" -> "video/3gpp"
        "avi" -> "video/x-msvideo"
        "flv" -> "video/x-flv"
        "ts", "mts", "m2ts" -> "video/mp2ts"
        "mpg", "mpeg" -> "video/mpeg"
        "wmv" -> "video/x-ms-wmv"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "m4a", "aac" -> "audio/mp4"
        "ogg", "oga" -> "audio/ogg"
        "flac" -> "audio/flac"
        else -> fallbackMime
    }
}
