package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.example.media.normalizeMediaMimeType
import com.example.model.DubLine
import com.example.model.OriginalEmotion
import com.example.model.VoiceAgeGroup
import com.example.model.VoiceGender
import com.example.model.VoiceStyleConfig
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "GeminiDubbingService"
private const val MAX_INLINE_MEDIA_BYTES = 18 * 1024 * 1024 // Stay safely below 20MB inline limit
private const val MAX_EXTRACTED_WAV_BYTES = 14 * 1024 * 1024 // ~7 min of 16kHz mono PCM audio
private const val AI_TIMEOUT_MS = 60_000L

private val GEMINI_DIRECT_VIDEO_MIMES = setOf(
    "video/mp4",
    "video/mpeg",
    "video/mpg",
    "video/mov",
    "video/quicktime",
    "video/avi",
    "video/x-msvideo",
    "video/x-flv",
    "video/webm",
    "video/wmv",
    "video/x-ms-wmv",
    "video/3gpp"
)

private val GEMINI_DIRECT_AUDIO_MIMES = setOf(
    "audio/wav",
    "audio/x-wav",
    "audio/mp3",
    "audio/mpeg",
    "audio/aiff",
    "audio/aac",
    "audio/ogg",
    "audio/flac",
    "audio/mp4"
)

data class DubbingGenerationResult(
    val sceneTitle: String,
    val lines: List<DubLine>,
    val note: String? = null
)

class GeminiDubbingService {

    private val dubbingSchema = Schema.obj(
        properties = mapOf(
            "sceneTitle" to Schema.string(description = "Short descriptive title for the movie scene"),
            "lines" to Schema.array(
                description = "Time-aligned English dialogues dubbed into natural cinematic Hindi",
                items = Schema.obj(
                    properties = mapOf(
                        "speakerName" to Schema.string(description = "Character or speaker label, e.g. Hero, Detective, Elena"),
                        "startMs" to Schema.integer(description = "Start time in milliseconds"),
                        "endMs" to Schema.integer(description = "End time in milliseconds"),
                        "englishText" to Schema.string(description = "Original English spoken dialogue"),
                        "hindiText" to Schema.string(description = "Natural, expressive Hindi dubbing dialogue in Devanagari script matching lip-sync pacing"),
                        "emotion" to Schema.enumeration(
                            values = OriginalEmotion.entries.map { it.name },
                            description = "Detected emotional tone of the original English actor"
                        ),
                        "emotionIntensity" to Schema.double(description = "Emotional intensity from 0.1 to 1.0"),
                        "suggestedGender" to Schema.enumeration(
                            values = VoiceGender.entries.map { it.name },
                            description = "Inferred speaker gender from voice or context"
                        ),
                        "suggestedAgeGroup" to Schema.enumeration(
                            values = VoiceAgeGroup.entries.map { it.name },
                            description = "Inferred speaker age group"
                        )
                    )
                )
            )
        )
    )

    private val singleLineSchema = Schema.obj(
        properties = mapOf(
            "hindiText" to Schema.string(description = "Adapted Hindi dubbing dialogue in Devanagari script"),
            "emotion" to Schema.enumeration(
                values = OriginalEmotion.entries.map { it.name },
                description = "Emotional tone"
            )
        )
    )

    private val dubbingModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = dubbingSchema
                temperature = 0.4f
            }
        )
    }

    private val lineAdaptModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = singleLineSchema
                temperature = 0.5f
            }
        )
    }

    suspend fun analyzeAndDubMediaClip(
        context: Context,
        uri: Uri,
        mimeType: String,
        durationMs: Long,
        preferredStyle: VoiceStyleConfig,
        autoDetectCharacterVoices: Boolean
    ): Result<DubbingGenerationResult> {
        return try {
            val mediaPayload = withContext(Dispatchers.IO) {
                prepareMediaPayload(context, uri, mimeType, durationMs)
            }

            val promptText = buildString {
                appendLine("You are an expert Bollywood & Hindi cinema dubbing director.")
                appendLine("Analyze this English movie/video/audio clip carefully.")
                appendLine("1. Transcribe every spoken English dialogue in chronological order with realistic startMs and endMs timestamps (clip duration ~${durationMs.coerceAtLeast(5000L)} ms).")
                appendLine("2. Identify the speaker character (e.g., Hero, Narrator, Villain, Elena) and detect the original actor's emotional tone (emotion) and emotionIntensity (0.1 to 1.0).")
                appendLine("3. Infer each speaker's gender (MALE, FEMALE, NEUTRAL) and age group (CHILD, YOUNG_ADULT, ADULT, ELDER).")
                appendLine("4. Translate and adapt each dialogue into natural, cinematic, emotionally resonant Hindi (Devanagari script) that matches the speaking duration for dubbing.")
                if (!autoDetectCharacterVoices) {
                    appendLine("Note: The user prefers ${preferredStyle.gender.englishLabel} (${preferredStyle.ageGroup.englishLabel}) Hindi phrasing.")
                }
                if (mediaPayload.extractedAudio) {
                    appendLine("The full extracted audio track and representative video frames are attached so you can hear every spoken English dialogue and see the scene context.")
                } else if (mediaPayload.framesOnly) {
                    appendLine("Only key visual frames were available (no readable audio track); infer dramatic dialogue/narration cues from the visual scene.")
                }
            }

            val response = withTimeout(AI_TIMEOUT_MS) {
                dubbingModel.generateContent(
                    content {
                        text(promptText)
                        if (mediaPayload.bytes != null) {
                            inlineData(mediaPayload.bytes, mediaPayload.mimeType)
                        }
                        mediaPayload.frames.forEach { bitmap ->
                            image(bitmap)
                        }
                    }
                )
            }

            val jsonString = response.text
                ?: return Result.failure(IllegalStateException("AI returned an empty response."))

            val parsed = parseDubbingResponse(
                jsonString = jsonString,
                preferredStyle = preferredStyle,
                useAiSuggestedCharacterVoice = autoDetectCharacterVoices,
                note = when {
                    mediaPayload.extractedAudio ->
                        "HD/बड़ी वीडियो फ़ाइल से ऑडियो और दृश्य निकालकर हिन्दी डब तैयार किया गया है। अब आप शब्दों को एडिट और सेव कर सकते हैं।"
                    mediaPayload.framesOnly ->
                        "वीडियो के मुख्य दृश्यों से हिन्दी डब बनाया गया है। आप किसी भी संवाद के शब्द नीचे एडिट कर सकते हैं।"
                    else -> null
                }
            )
            Result.success(parsed)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Media dubbing timed out", e)
            Result.failure(IllegalStateException("AI analysis timed out after 60s. Try a shorter clip or paste script."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Media dubbing failed", e)
            Result.failure(e)
        }
    }

    suspend fun dubEnglishScriptToHindi(
        englishScript: String,
        sceneDurationMs: Long,
        preferredStyle: VoiceStyleConfig,
        autoDetectCharacterVoices: Boolean
    ): Result<DubbingGenerationResult> {
        return try {
            val cleanScript = englishScript.trim()
            if (cleanScript.isBlank()) {
                return Result.failure(IllegalArgumentException("Please enter English movie dialogue to dub."))
            }

            val estimatedDuration = sceneDurationMs.coerceAtLeast(15_000L)
            val promptText = buildString {
                appendLine("You are an expert Bollywood & Hindi cinema dubbing director.")
                appendLine("Convert the following English movie dialogue/script into time-aligned Hindi dubbing lines:")
                appendLine("---")
                appendLine(cleanScript.take(4000))
                appendLine("---")
                appendLine("Instructions:")
                appendLine("- Split into sequential dialogue lines fitting roughly within 0 to $estimatedDuration ms (or extend naturally if there are many lines).")
                appendLine("- Detect each speaker's name, original emotional tone (emotion), and emotionIntensity (0.1 to 1.0).")
                appendLine("- Infer suggestedGender (MALE, FEMALE, NEUTRAL) and suggestedAgeGroup (CHILD, YOUNG_ADULT, ADULT, ELDER).")
                appendLine("- Write natural, conversational, cinematic Hindi in Devanagari script tailored for voice acting and lip-sync.")
            }

            val response = withTimeout(AI_TIMEOUT_MS) {
                dubbingModel.generateContent(promptText)
            }

            val jsonString = response.text
                ?: return Result.failure(IllegalStateException("AI returned an empty response."))

            val parsed = parseDubbingResponse(
                jsonString = jsonString,
                preferredStyle = preferredStyle,
                useAiSuggestedCharacterVoice = autoDetectCharacterVoices,
                note = null
            )
            Result.success(parsed)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Script dubbing timed out", e)
            Result.failure(IllegalStateException("AI dubbing timed out after 60s. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Script dubbing failed", e)
            Result.failure(e)
        }
    }

    suspend fun reAdaptLineForVoiceStyle(
        englishText: String,
        currentHindi: String,
        targetGender: VoiceGender,
        targetAgeGroup: VoiceAgeGroup,
        targetEmotion: OriginalEmotion
    ): Result<String> {
        return try {
            val prompt = buildString {
                appendLine("Rewrite this Hindi movie dubbing dialogue in Devanagari so it sounds completely natural for:")
                appendLine("- Speaker Gender: ${targetGender.englishLabel}")
                appendLine("- Age Group: ${targetAgeGroup.englishLabel}")
                appendLine("- Emotional Tone: ${targetEmotion.englishLabel}")
                appendLine("Original English: \"$englishText\"")
                appendLine("Current Hindi: \"$currentHindi\"")
                appendLine("Keep similar spoken length for lip-sync. Return Hindi text in Devanagari.")
            }

            val response = withTimeout(AI_TIMEOUT_MS) {
                lineAdaptModel.generateContent(prompt)
            }
            val json = JSONObject(response.text ?: throw IllegalStateException("Empty AI response"))
            val adapted = json.optString("hindiText").trim()
            if (adapted.isBlank()) {
                Result.failure(IllegalStateException("AI returned blank Hindi text."))
            } else {
                Result.success(adapted)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Line adaptation timed out", e)
            Result.failure(IllegalStateException("Timed out adapting dialogue."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Line adaptation failed", e)
            Result.failure(e)
        }
    }

    private fun parseDubbingResponse(
        jsonString: String,
        preferredStyle: VoiceStyleConfig,
        useAiSuggestedCharacterVoice: Boolean,
        note: String?
    ): DubbingGenerationResult {
        val json = JSONObject(jsonString)
        val sceneTitle = json.optString("sceneTitle").ifBlank { "Hindi Movie Dub" }
        val linesArray = json.optJSONArray("lines")
        val lines = mutableListOf<DubLine>()

        if (linesArray != null) {
            for (i in 0 until linesArray.length()) {
                val obj = linesArray.optJSONObject(i) ?: continue
                val startMs = obj.optLong("startMs", i * 3500L).coerceAtLeast(0L)
                val endMs = obj.optLong("endMs", startMs + 3200L).coerceAtLeast(startMs + 500L)
                val emotion = OriginalEmotion.fromRaw(obj.optString("emotion"))
                val intensity = obj.optDouble("emotionIntensity", 0.75).coerceIn(0.1, 1.0)
                val gender = if (useAiSuggestedCharacterVoice) {
                    VoiceGender.fromRaw(obj.optString("suggestedGender"))
                } else {
                    preferredStyle.gender
                }
                val ageGroup = if (useAiSuggestedCharacterVoice) {
                    VoiceAgeGroup.fromRaw(obj.optString("suggestedAgeGroup"))
                } else {
                    preferredStyle.ageGroup
                }
                val englishText = obj.optString("englishText").trim()
                val hindiText = obj.optString("hindiText").trim().ifBlank { "..." }

                lines.add(
                    DubLine(
                        orderIndex = i.toLong(),
                        speakerName = obj.optString("speakerName").trim().ifBlank { "Speaker ${i + 1}" },
                        startMs = startMs,
                        endMs = endMs,
                        englishText = englishText,
                        hindiText = hindiText,
                        emotion = emotion.name,
                        emotionIntensity = intensity,
                        voiceGender = gender.name,
                        ageGroup = ageGroup.name,
                        voiceEffect = preferredStyle.effect.name,
                        pitchMultiplier = preferredStyle.pitchMultiplier.toDouble(),
                        speedMultiplier = preferredStyle.speedMultiplier.toDouble(),
                        matchOriginalEmotion = preferredStyle.matchOriginalEmotion
                    )
                )
            }
        }

        return DubbingGenerationResult(
            sceneTitle = sceneTitle,
            lines = lines,
            note = note
        )
    }

    private data class MediaPayload(
        val bytes: ByteArray? = null,
        val mimeType: String = "video/mp4",
        val frames: List<Bitmap> = emptyList(),
        val framesOnly: Boolean = false,
        val extractedAudio: Boolean = false
    )

    private fun prepareMediaPayload(
        context: Context,
        uri: Uri,
        mimeType: String,
        durationMs: Long
    ): MediaPayload {
        val resolver = context.contentResolver
        val normalizedMime = normalizeMediaMimeType(mimeType, uri.lastPathSegment.orEmpty(), mimeType)
        val fileSize = runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        }.getOrDefault(-1L)

        val directSupported = normalizedMime in GEMINI_DIRECT_VIDEO_MIMES ||
            normalizedMime in GEMINI_DIRECT_AUDIO_MIMES

        if (directSupported && fileSize in 1L..MAX_INLINE_MEDIA_BYTES) {
            val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes != null && bytes.size <= MAX_INLINE_MEDIA_BYTES) {
                return MediaPayload(bytes = bytes, mimeType = normalizedMime)
            }
        }

        // For HD / 4K / oversized files (>18MB) or any video container (MKV, TS, AVI, M4V, 3GP, etc.):
        // Extract lightweight 16kHz mono WAV audio + key visual frames so speech & visuals are preserved.
        val extractedWav = runCatching {
            extractAudioToWavBytes(context, uri, MAX_EXTRACTED_WAV_BYTES)
        }.onFailure { err ->
            Log.w(TAG, "Audio extraction fallback triggered: ${err.message}")
        }.getOrNull()

        val frames = extractVideoKeyFrames(context, uri, durationMs)

        if (extractedWav != null && extractedWav.size > 44) {
            return MediaPayload(
                bytes = extractedWav,
                mimeType = "audio/wav",
                frames = frames,
                extractedAudio = true
            )
        }

        if (frames.isNotEmpty()) {
            return MediaPayload(frames = frames, framesOnly = true)
        }

        throw IllegalArgumentException(
            "इस फ़ाइल से ऑडियो या वीडियो पढ़ा नहीं जा सका। कृपया कोई मान्य वीडियो/ऑडियो फ़ाइल चुनें या अंग्रेज़ी स्क्रिप्ट लिखें।"
        )
    }

    private fun extractVideoKeyFrames(
        context: Context,
        uri: Uri,
        durationMs: Long
    ): List<Bitmap> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            if (!hasVideo.equals("yes", ignoreCase = true) && width <= 0) {
                return emptyList()
            }
            val effectiveDuration = durationMs.takeIf { it > 0L }
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: 10_000L
            val frameTimesUs = listOf(0.15, 0.40, 0.65, 0.85).map { frac ->
                (effectiveDuration * frac * 1000L).toLong()
            }
            frameTimesUs.mapNotNull { timeUs ->
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { bmp ->
                    scaleDownBitmap(bmp, 640)
                }
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractAudioToWavBytes(
        context: Context,
        uri: Uri,
        maxWavBytes: Int
    ): ByteArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val trackMime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (trackMime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }
            if (audioTrackIndex < 0 || audioFormat == null) return null

            val trackMime = audioFormat.getString(MediaFormat.KEY_MIME) ?: return null
            extractor.selectTrack(audioTrackIndex)

            var inputSampleRate = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
            } else 44100
            var inputChannels = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 8)
            } else 1

            codec = MediaCodec.createDecoderByType(trackMime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val targetSampleRate = 16000
            val maxPcmBytes = (maxWavBytes - 44).coerceAtLeast(1024)
            val pcmOut = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false
            var resamplePhase = 0.0

            while (!sawOutputEos && pcmOut.size() < maxPcmBytes) {
                if (!sawInputEos) {
                    val inIndex = codec.dequeueInputBuffer(10_000L)
                    if (inIndex >= 0) {
                        val inputBuf = codec.getInputBuffer(inIndex)
                        if (inputBuf != null) {
                            val sampleSize = extractor.readSampleData(inputBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEos = true
                            } else {
                                codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFmt = codec.outputFormat
                        if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            inputSampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
                        }
                        if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            inputChannels = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 8)
                        }
                    }
                    outIndex >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIndex)
                        if (outBuf != null && bufferInfo.size > 0) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val totalShorts = shortBuf.remaining()
                            val frameCount = totalShorts / inputChannels
                            val step = inputSampleRate.toDouble() / targetSampleRate.toDouble()

                            var fIdx = resamplePhase
                            while (fIdx < frameCount && pcmOut.size() < maxPcmBytes) {
                                val framePos = fIdx.toInt()
                                var sum = 0
                                val base = framePos * inputChannels
                                for (c in 0 until inputChannels) {
                                    sum += shortBuf.get(base + c).toInt()
                                }
                                val monoSample = (sum / inputChannels).coerceIn(-32768, 32767)
                                pcmOut.write(monoSample and 0xFF)
                                pcmOut.write((monoSample shr 8) and 0xFF)
                                fIdx += step
                            }
                            resamplePhase = fIdx - frameCount
                            if (resamplePhase < 0.0) resamplePhase = 0.0
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEos = true
                        }
                    }
                }
            }

            val pcmBytes = pcmOut.toByteArray()
            if (pcmBytes.size < 3200) return null
            wrapPcm16MonoWav(pcmBytes, targetSampleRate)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun wrapPcm16MonoWav(pcmData: ByteArray, sampleRate: Int): ByteArray {
        val dataSize = pcmData.size
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(sampleRate)
            putInt(sampleRate * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }
        return header.array() + pcmData
    }

    private fun scaleDownBitmap(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) return bitmap
        val ratio = minOf(maxDim.toFloat() / width, maxDim.toFloat() / height)
        val newW = (width * ratio).toInt().coerceAtLeast(1)
        val newH = (height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }
}
