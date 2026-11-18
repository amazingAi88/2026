package com.example.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.model.DubLine
import com.example.model.DubbingProject
import com.example.model.OriginalEmotion
import com.example.model.VoiceAgeGroup
import com.example.model.VoiceEffect
import com.example.model.VoiceGender
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

object ProjectFileExporter {

    fun serializeProjectToJson(project: DubbingProject, lines: List<DubLine>): String {
        val root = JSONObject().apply {
            put("format", "swardub_project_v1")
            put("exportedAtEpochMs", System.currentTimeMillis())
            put("project", JSONObject().apply {
                put("title", project.title)
                put("sourceLanguage", project.sourceLanguage)
                put("targetLanguage", project.targetLanguage)
                put("videoFileName", project.videoFileName)
                put("durationMs", project.durationMs)
                put("defaultGender", project.defaultGender)
                put("defaultAgeGroup", project.defaultAgeGroup)
                put("defaultEffect", project.defaultEffect)
                put("defaultPitch", project.defaultPitch)
                put("defaultSpeed", project.defaultSpeed)
                put("matchOriginalEmotion", project.matchOriginalEmotion)
                put("originalAudioVolume", project.originalAudioVolume)
            })
            val linesArr = JSONArray()
            lines.sortedBy { it.orderIndex }.forEach { line ->
                linesArr.put(
                    JSONObject().apply {
                        put("orderIndex", line.orderIndex)
                        put("speakerName", line.speakerName)
                        put("startMs", line.startMs)
                        put("endMs", line.endMs)
                        put("englishText", line.englishText)
                        put("hindiText", line.hindiText)
                        put("emotion", line.emotion)
                        put("emotionIntensity", line.emotionIntensity)
                        put("voiceGender", line.voiceGender)
                        put("ageGroup", line.ageGroup)
                        put("voiceEffect", line.voiceEffect)
                        put("pitchMultiplier", line.pitchMultiplier)
                        put("speedMultiplier", line.speedMultiplier)
                        put("matchOriginalEmotion", line.matchOriginalEmotion)
                    }
                )
            }
            put("lines", linesArr)
        }
        return root.toString(2)
    }

    fun parseProjectFromJson(jsonString: String): Pair<DubbingProject, List<DubLine>> {
        val root = JSONObject(jsonString)
        val projObj = root.optJSONObject("project") ?: root
        val project = DubbingProject(
            title = projObj.optString("title").ifBlank { "Imported Dub Project" },
            sourceLanguage = projObj.optString("sourceLanguage").ifBlank { "English" },
            targetLanguage = projObj.optString("targetLanguage").ifBlank { "Hindi" },
            videoFileName = projObj.optString("videoFileName", ""),
            videoUriString = "",
            durationMs = projObj.optLong("durationMs", 15_000L).coerceAtLeast(0L),
            defaultGender = VoiceGender.fromRaw(projObj.optString("defaultGender")).name,
            defaultAgeGroup = VoiceAgeGroup.fromRaw(projObj.optString("defaultAgeGroup")).name,
            defaultEffect = VoiceEffect.fromRaw(projObj.optString("defaultEffect")).name,
            defaultPitch = projObj.optDouble("defaultPitch", 1.0).coerceIn(0.4, 2.0),
            defaultSpeed = projObj.optDouble("defaultSpeed", 1.0).coerceIn(0.5, 2.0),
            matchOriginalEmotion = projObj.optBoolean("matchOriginalEmotion", true),
            originalAudioVolume = projObj.optDouble("originalAudioVolume", 0.15).coerceIn(0.0, 1.0)
        )

        val lines = mutableListOf<DubLine>()
        val linesArr = root.optJSONArray("lines")
        if (linesArr != null) {
            for (i in 0 until linesArr.length()) {
                val obj = linesArr.optJSONObject(i) ?: continue
                lines.add(
                    DubLine(
                        orderIndex = obj.optLong("orderIndex", i.toLong()),
                        speakerName = obj.optString("speakerName").ifBlank { "Speaker" },
                        startMs = obj.optLong("startMs", i * 3000L).coerceAtLeast(0L),
                        endMs = obj.optLong("endMs", (i + 1) * 3000L).coerceAtLeast(500L),
                        englishText = obj.optString("englishText", ""),
                        hindiText = obj.optString("hindiText").ifBlank { "..." },
                        emotion = OriginalEmotion.fromRaw(obj.optString("emotion")).name,
                        emotionIntensity = obj.optDouble("emotionIntensity", 0.75).coerceIn(0.0, 1.0),
                        voiceGender = VoiceGender.fromRaw(obj.optString("voiceGender")).name,
                        ageGroup = VoiceAgeGroup.fromRaw(obj.optString("ageGroup")).name,
                        voiceEffect = VoiceEffect.fromRaw(obj.optString("voiceEffect")).name,
                        pitchMultiplier = obj.optDouble("pitchMultiplier", 1.0).coerceIn(0.4, 2.0),
                        speedMultiplier = obj.optDouble("speedMultiplier", 1.0).coerceIn(0.5, 2.0),
                        matchOriginalEmotion = obj.optBoolean("matchOriginalEmotion", true)
                    )
                )
            }
        }
        return project.copy(lineCount = lines.size.toLong()) to lines
    }

    fun generateHindiSrt(lines: List<DubLine>): String {
        val sb = StringBuilder()
        lines.sortedBy { it.startMs }.forEachIndexed { idx, line ->
            sb.appendLine(idx + 1)
            sb.appendLine("${formatSrtTime(line.startMs)} --> ${formatSrtTime(line.endMs.coerceAtLeast(line.startMs + 500L))}")
            sb.appendLine("[${line.speakerName}] ${line.hindiText}")
            sb.appendLine()
        }
        return sb.toString()
    }

    fun writeStringToUri(context: Context, uri: Uri, content: String) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("Unable to open selected storage location.")
    }

    fun readStringFromUri(context: Context, uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
            it.readText()
        } ?: throw IllegalStateException("Unable to read selected project file.")
    }

    /**
     * Saves bytes directly into local mobile storage (Downloads/SwarDub) via MediaStore on Android 10+,
     * or app-specific Documents/SwarDub folder on Android 7-9 without requesting legacy storage permissions.
     */
    fun saveBytesToLocalMobileStorage(
        context: Context,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/SwarDub")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create file in Downloads/SwarDub.")
            try {
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: throw IllegalStateException("Could not write file to Downloads.")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return "Downloads/SwarDub/$fileName"
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
        } else {
            val dir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir,
                "SwarDub"
            ).apply { mkdirs() }
            val outFile = File(dir, fileName)
            outFile.writeBytes(bytes)
            return outFile.absolutePath
        }
    }

    fun sanitizedBaseName(title: String): String {
        return title.trim()
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(36)
            .ifBlank { "swardub_project" }
    }

    private fun formatSrtTime(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val hours = safe / 3_600_000L
        val mins = (safe % 3_600_000L) / 60_000L
        val secs = (safe % 60_000L) / 1000L
        val millis = safe % 1000L
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, mins, secs, millis)
    }
}
