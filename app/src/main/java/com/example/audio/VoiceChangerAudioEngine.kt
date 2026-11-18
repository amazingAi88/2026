package com.example.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.model.DubLine
import com.example.model.EffectiveVoiceParams
import com.example.model.VoiceEffect
import com.example.model.VoiceGender
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tanh

private const val TAG = "VoiceChangerAudioEngine"

data class DubAudioPlaybackState(
    val isTtsReady: Boolean = false,
    val activeLineId: String? = null,
    val isPlayingScene: Boolean = false,
    val isPreviewingFullDub: Boolean = false,
    val isPreviewPaused: Boolean = false,
    val previewPositionMs: Long = 0L,
    val previewDurationMs: Long = 0L,
    val isSynthesizing: Boolean = false,
    val hindiVoicesFound: Int = 0,
    val statusMessage: String? = null
)

class VoiceChangerAudioEngine(context: Context) {

    private val appContext = context.applicationContext
    private val cacheDir = File(appContext.cacheDir, "swardub_audio").apply { mkdirs() }

    private val _state = MutableStateFlow(DubAudioPlaybackState())
    val state: StateFlow<DubAudioPlaybackState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private var sceneSyncJob: Job? = null
    private var previewProgressJob: Job? = null
    private var lastGeneratedWavFile: File? = null

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val hindiLocale = Locale.Builder().setLanguage("hi").setRegion("IN").build()
                val res = tts?.setLanguage(hindiLocale)
                val hindiVoices = runCatching {
                    tts?.voices?.filter { it.locale.language == "hi" }.orEmpty()
                }.getOrDefault(emptyList())

                val ready = res != TextToSpeech.LANG_MISSING_DATA &&
                    res != TextToSpeech.LANG_NOT_SUPPORTED
                _state.value = _state.value.copy(
                    isTtsReady = true,
                    hindiVoicesFound = hindiVoices.size,
                    statusMessage = if (!ready) {
                        "Hindi TTS voice pack not installed on device; using fallback engine."
                    } else null
                )
            } else {
                Log.w(TAG, "TextToSpeech init failed with status=$status")
                _state.value = _state.value.copy(
                    isTtsReady = false,
                    statusMessage = "Speech synthesizer unavailable on this device."
                )
            }
        }
    }

    suspend fun previewLine(line: DubLine): Result<Unit> {
        return try {
            stopAll()
            _state.value = _state.value.copy(
                activeLineId = line.id,
                isSynthesizing = true,
                statusMessage = null
            )
            val wavFile = renderDubLineToWav(line)
            lastGeneratedWavFile = wavFile
            _state.value = _state.value.copy(isSynthesizing = false)
            playWavFile(wavFile, line.id)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to preview dubbed line", e)
            _state.value = _state.value.copy(
                activeLineId = null,
                isSynthesizing = false,
                statusMessage = e.localizedMessage ?: "Audio synthesis failed."
            )
            Result.failure(e)
        }
    }

    suspend fun previewFullDubAudio(
        scope: CoroutineScope,
        lines: List<DubLine>
    ): Result<Unit> {
        return try {
            if (lines.isEmpty()) {
                return Result.failure(IllegalArgumentException("प्रीव्यू के लिए कम से कम एक संवाद जोड़ें।"))
            }
            stopAll()
            _state.value = _state.value.copy(
                isSynthesizing = true,
                isPreviewingFullDub = true,
                isPreviewPaused = false,
                previewPositionMs = 0L,
                previewDurationMs = 0L,
                statusMessage = null
            )
            val sortedLines = lines.sortedBy { it.orderIndex }
            val renderedFiles = sortedLines.map { renderDubLineToWav(it) }
            val combinedFile = File(cacheDir, "full_dub_preview_${System.currentTimeMillis()}.wav")
            withContext(Dispatchers.IO) {
                concatenateWavs(renderedFiles, combinedFile)
                renderedFiles.forEach { it.delete() }
            }
            lastGeneratedWavFile = combinedFile
            _state.value = _state.value.copy(isSynthesizing = false)
            playFullPreviewWav(scope, combinedFile)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Full dub audio preview failed", e)
            _state.value = _state.value.copy(
                isSynthesizing = false,
                isPreviewingFullDub = false,
                isPreviewPaused = false,
                statusMessage = e.localizedMessage ?: "Dub audio preview failed."
            )
            Result.failure(e)
        }
    }

    fun toggleFullPreviewPause() {
        val mp = mediaPlayer ?: return
        if (!_state.value.isPreviewingFullDub) return
        runCatching {
            if (mp.isPlaying) {
                mp.pause()
                _state.value = _state.value.copy(isPreviewPaused = true)
            } else {
                mp.start()
                _state.value = _state.value.copy(isPreviewPaused = false)
            }
        }
    }

    fun seekFullPreview(positionMs: Long) {
        val mp = mediaPlayer ?: return
        if (!_state.value.isPreviewingFullDub) return
        runCatching {
            mp.seekTo(positionMs.toInt().coerceAtLeast(0))
            _state.value = _state.value.copy(previewPositionMs = positionMs)
        }
    }

    fun startSceneSyncPlayback(
        scope: CoroutineScope,
        lines: List<DubLine>,
        getVideoPositionMs: () -> Long,
        isVideoPlaying: () -> Boolean,
        onLineTriggered: (DubLine) -> Unit
    ) {
        stopAll()
        _state.value = _state.value.copy(isPlayingScene = true)

        sceneSyncJob = scope.launch {
            val triggeredIds = mutableSetOf<String>()
            var lastPosMs = getVideoPositionMs()

            val sortedLines = lines.sortedBy { it.startMs }
            while (_state.value.isPlayingScene) {
                val posMs = getVideoPositionMs()
                if (posMs + 1000L < lastPosMs) {
                    triggeredIds.clear()
                }
                lastPosMs = posMs

                if (isVideoPlaying()) {
                    val dueLine = sortedLines.firstOrNull { line ->
                        val key = line.id.ifBlank { "${line.orderIndex}_${line.startMs}" }
                        key !in triggeredIds && posMs in line.startMs..(line.endMs + 800L)
                    }
                    if (dueLine != null) {
                        val key = dueLine.id.ifBlank { "${dueLine.orderIndex}_${dueLine.startMs}" }
                        triggeredIds.add(key)
                        onLineTriggered(dueLine)
                        launch {
                            runCatching {
                                _state.value = _state.value.copy(activeLineId = dueLine.id)
                                val wav = renderDubLineToWav(dueLine)
                                lastGeneratedWavFile = wav
                                playWavFile(wav, dueLine.id)
                            }.onFailure { err ->
                                Log.w(TAG, "Line sync audio playback error: ${err.message}")
                            }
                        }
                    }
                }
                delay(140L)
            }
        }
    }

    suspend fun buildCombinedDubWavBytes(lines: List<DubLine>): ByteArray {
        if (lines.isEmpty()) {
            throw IllegalArgumentException("No dialogue lines to export.")
        }
        _state.value = _state.value.copy(isSynthesizing = true)
        return try {
            val renderedFiles = lines.sortedBy { it.orderIndex }.map { renderDubLineToWav(it) }
            withContext(Dispatchers.IO) {
                val combined = File(cacheDir, "export_${System.currentTimeMillis()}.wav")
                concatenateWavs(renderedFiles, combined)
                renderedFiles.forEach { it.delete() }
                val bytes = combined.readBytes()
                combined.delete()
                bytes
            }
        } finally {
            _state.value = _state.value.copy(isSynthesizing = false)
        }
    }

    suspend fun exportCombinedDubWav(
        context: Context,
        outputUri: Uri,
        lines: List<DubLine>
    ): Result<Unit> {
        return try {
            val wavBytes = buildCombinedDubWavBytes(lines)
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(outputUri)?.use { out ->
                    out.write(wavBytes)
                } ?: throw IllegalStateException("Unable to open destination file.")
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            Result.failure(e)
        }
    }

    fun stopAll() {
        sceneSyncJob?.cancel()
        sceneSyncJob = null
        previewProgressJob?.cancel()
        previewProgressJob = null
        runCatching {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        }
        mediaPlayer = null
        _state.value = _state.value.copy(
            activeLineId = null,
            isPlayingScene = false,
            isPreviewingFullDub = false,
            isPreviewPaused = false,
            previewPositionMs = 0L,
            previewDurationMs = 0L,
            isSynthesizing = false
        )
    }

    private fun playFullPreviewWav(scope: CoroutineScope, wavFile: File) {
        runCatching { mediaPlayer?.release() }
        val player = MediaPlayer().apply {
            setDataSource(wavFile.absolutePath)
            setOnCompletionListener {
                previewProgressJob?.cancel()
                _state.value = _state.value.copy(
                    isPreviewingFullDub = false,
                    isPreviewPaused = false,
                    previewPositionMs = 0L
                )
                it.release()
                if (mediaPlayer === it) mediaPlayer = null
            }
            setOnErrorListener { mp, _, _ ->
                previewProgressJob?.cancel()
                _state.value = _state.value.copy(
                    isPreviewingFullDub = false,
                    isPreviewPaused = false
                )
                mp.release()
                if (mediaPlayer === mp) mediaPlayer = null
                true
            }
            prepare()
            _state.value = _state.value.copy(
                previewDurationMs = duration.toLong().coerceAtLeast(1L),
                previewPositionMs = 0L
            )
            start()
        }
        mediaPlayer = player
        previewProgressJob?.cancel()
        previewProgressJob = scope.launch {
            while (_state.value.isPreviewingFullDub) {
                runCatching {
                    mediaPlayer?.let { mp ->
                        _state.value = _state.value.copy(
                            previewPositionMs = mp.currentPosition.toLong(),
                            previewDurationMs = mp.duration.toLong().coerceAtLeast(1L)
                        )
                    }
                }
                delay(100L)
            }
        }
    }

    fun release() {
        stopAll()
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
    }

    private suspend fun renderDubLineToWav(line: DubLine): File {
        val engine = tts ?: throw IllegalStateException("Speech synthesizer is not initialized.")
        val params = line.effectiveParams()
        val rawFile = File(cacheDir, "raw_${UUID.randomUUID()}.wav")
        val processedFile = File(cacheDir, "dub_${UUID.randomUUID()}.wav")

        val synthesized = synthesizeTtsToFile(engine, line.hindiText, params, rawFile)
        if (!synthesized || !rawFile.exists() || rawFile.length() <= 44L) {
            throw IllegalStateException("Hindi TTS could not synthesize audio on this device.")
        }

        withContext(Dispatchers.Default) {
            applyVoiceChangerDsp(rawFile, processedFile, params)
            rawFile.delete()
        }
        return processedFile
    }

    private suspend fun synthesizeTtsToFile(
        engine: TextToSpeech,
        hindiText: String,
        params: EffectiveVoiceParams,
        outFile: File
    ): Boolean = suspendCancellableCoroutine { cont ->
        val utteranceId = UUID.randomUUID().toString()
        selectMatchingHindiVoice(engine, params.gender)
        engine.setPitch(params.finalPitch)
        engine.setSpeechRate(params.finalSpeed)

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId && cont.isActive) {
                    cont.resume(true)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId && cont.isActive) {
                    cont.resume(false)
                }
            }

            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId && cont.isActive) {
                    cont.resumeWithException(
                        IllegalStateException("TTS synthesis failed (code $errorCode)")
                    )
                }
            }
        })

        val result = engine.synthesizeToFile(hindiText, null, outFile, utteranceId)
        if (result != TextToSpeech.SUCCESS && cont.isActive) {
            cont.resume(false)
        }
        cont.invokeOnCancellation {
            runCatching { engine.stop() }
        }
    }

    private fun selectMatchingHindiVoice(engine: TextToSpeech, gender: VoiceGender) {
        val voices = runCatching { engine.voices }.getOrNull() ?: return
        val hindiVoices = voices.filter {
            it.locale.language.equals("hi", ignoreCase = true) && !it.isNetworkConnectionRequired
        }.ifEmpty {
            voices.filter { it.locale.language.equals("hi", ignoreCase = true) }
        }
        if (hindiVoices.isEmpty()) return

        val chosen: Voice = when (gender) {
            VoiceGender.FEMALE -> hindiVoices.firstOrNull {
                val n = it.name.lowercase(Locale.ROOT)
                n.contains("female") || n.contains("hin-x-hie") || n.contains("-f-") || n.contains("hia")
            } ?: hindiVoices.first()
            VoiceGender.MALE -> hindiVoices.firstOrNull {
                val n = it.name.lowercase(Locale.ROOT)
                n.contains("male") && !n.contains("female") || n.contains("hid") || n.contains("-m-")
            } ?: hindiVoices.last()
            VoiceGender.NEUTRAL -> hindiVoices.first()
        }
        runCatching { engine.voice = chosen }
    }

    private fun playWavFile(wavFile: File, lineId: String) {
        runCatching {
            mediaPlayer?.release()
        }
        val player = MediaPlayer().apply {
            setDataSource(wavFile.absolutePath)
            setOnCompletionListener {
                if (_state.value.activeLineId == lineId) {
                    _state.value = _state.value.copy(activeLineId = null)
                }
                it.release()
                if (mediaPlayer === it) mediaPlayer = null
            }
            setOnErrorListener { mp, _, _ ->
                if (_state.value.activeLineId == lineId) {
                    _state.value = _state.value.copy(activeLineId = null)
                }
                mp.release()
                if (mediaPlayer === mp) mediaPlayer = null
                true
            }
            prepare()
            start()
        }
        mediaPlayer = player
    }

    /**
     * Applies real 16-bit PCM WAV voice-changing DSP effects and emotional energy shaping.
     */
    private fun applyVoiceChangerDsp(
        inputWav: File,
        outputWav: File,
        params: EffectiveVoiceParams
    ) {
        val rawBytes = inputWav.readBytes()
        val dataChunk = findWavDataChunk(rawBytes)
        if (dataChunk == null) {
            inputWav.copyTo(outputWav, overwrite = true)
            return
        }

        val sampleRate = ByteBuffer.wrap(rawBytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
            .coerceIn(8000, 48000)
        val numChannels = ByteBuffer.wrap(rawBytes, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            .coerceIn(1, 2)

        val pcmOffset = dataChunk.first
        val pcmLen = dataChunk.second.coerceAtMost(rawBytes.size - pcmOffset)
        val sampleCount = pcmLen / 2
        if (sampleCount <= 0) {
            inputWav.copyTo(outputWav, overwrite = true)
            return
        }

        val shortBuf = ByteBuffer.wrap(rawBytes, pcmOffset, sampleCount * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
        val samples = FloatArray(sampleCount) { shortBuf.get(it) / 32768f }
        val outSamples = FloatArray(sampleCount)

        val delaySamples1 = (sampleRate * 0.085f).toInt() * numChannels
        val delaySamples2 = (sampleRate * 0.170f).toInt() * numChannels
        val gain = params.energyGain

        for (i in samples.indices) {
            val x = samples[i] * gain
            val t = i.toDouble() / (sampleRate * numChannels)
            val modulated = when (params.effect) {
                VoiceEffect.ROBOT_SCIFI -> {
                    val carrier = sin(2.0 * PI * 52.0 * t).toFloat()
                    val ring = x * (0.55f + 0.45f * carrier)
                    // Subtle quantization grit for cybernetic texture
                    ((ring * 24f).toInt() / 24f)
                }
                VoiceEffect.VILLAIN_GRAVEL -> {
                    val growl = (0.82f + 0.28f * sin(2.0 * PI * 26.0 * t).toFloat())
                    tanh((x * growl * 1.45f).toDouble()).toFloat() * 0.92f
                }
                VoiceEffect.THEATER_ECHO -> {
                    val d1 = if (i >= delaySamples1) samples[i - delaySamples1] * 0.34f else 0f
                    val d2 = if (i >= delaySamples2) samples[i - delaySamples2] * 0.18f else 0f
                    x * 0.88f + d1 + d2
                }
                VoiceEffect.HERO_DEEP -> {
                    val prev = if (i > 0) samples[i - 1] else 0f
                    val warm = 0.65f * x + 0.35f * prev
                    tanh((warm * 1.15f).toDouble()).toFloat()
                }
                VoiceEffect.SWEET_BRIGHT, VoiceEffect.CARTOON_HIGH -> {
                    val prev = if (i > 0) samples[i - 1] else x
                    val crisp = x + 0.25f * (x - prev)
                    crisp.coerceIn(-0.98f, 0.98f)
                }
                VoiceEffect.NATURAL -> x
            }
            outSamples[i] = modulated.coerceIn(-0.99f, 0.99f)
        }

        writePcm16Wav(outputWav, outSamples, sampleRate, numChannels)
    }

    private fun findWavDataChunk(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size < 44) return null
        var idx = 12
        while (idx + 8 <= bytes.size) {
            val chunkId = String(bytes, idx, 4, Charsets.US_ASCII)
            val chunkSize = ByteBuffer.wrap(bytes, idx + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (chunkId == "data") {
                return (idx + 8) to chunkSize
            }
            idx += 8 + chunkSize
            if (chunkSize <= 0) break
        }
        return 44 to (bytes.size - 44)
    }

    private fun writePcm16Wav(
        file: File,
        samples: FloatArray,
        sampleRate: Int,
        channels: Int
    ) {
        val dataSize = samples.size * 2
        val byteBuffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        byteBuffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        byteBuffer.putInt(36 + dataSize)
        byteBuffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        byteBuffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        byteBuffer.putInt(16)
        byteBuffer.putShort(1)
        byteBuffer.putShort(channels.toShort())
        byteBuffer.putInt(sampleRate)
        byteBuffer.putInt(sampleRate * channels * 2)
        byteBuffer.putShort((channels * 2).toShort())
        byteBuffer.putShort(16)
        byteBuffer.put("data".toByteArray(Charsets.US_ASCII))
        byteBuffer.putInt(dataSize)
        for (s in samples) {
            val pcm = (s * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            byteBuffer.putShort(pcm)
        }
        file.writeBytes(byteBuffer.array())
    }

    private fun concatenateWavs(files: List<File>, output: File) {
        if (files.isEmpty()) return
        val firstBytes = files.first().readBytes()
        val firstSampleRate = if (firstBytes.size >= 28) {
            ByteBuffer.wrap(firstBytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int.coerceIn(8000, 48000)
        } else 22050
        val channels = if (firstBytes.size >= 24) {
            ByteBuffer.wrap(firstBytes, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt().coerceIn(1, 2)
        } else 1

        output.outputStream().use { out ->
            // Write placeholder 44-byte header, then patch sizes
            out.write(ByteArray(44))
            var totalDataBytes = 0
            val silenceGap = ByteArray((firstSampleRate * channels * 2 * 0.35).toInt())
            files.forEachIndexed { index, file ->
                val bytes = file.readBytes()
                val dataInfo = findWavDataChunk(bytes)
                if (dataInfo != null) {
                    val len = dataInfo.second.coerceAtMost(bytes.size - dataInfo.first)
                    if (len > 0) {
                        out.write(bytes, dataInfo.first, len)
                        totalDataBytes += len
                        if (index < files.lastIndex) {
                            out.write(silenceGap)
                            totalDataBytes += silenceGap.size
                        }
                    }
                }
            }
            out.flush()
            RandomAccessFile(output, "rw").use { raf ->
                val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                header.put("RIFF".toByteArray(Charsets.US_ASCII))
                header.putInt(36 + totalDataBytes)
                header.put("WAVE".toByteArray(Charsets.US_ASCII))
                header.put("fmt ".toByteArray(Charsets.US_ASCII))
                header.putInt(16)
                header.putShort(1)
                header.putShort(channels.toShort())
                header.putInt(firstSampleRate)
                header.putInt(firstSampleRate * channels * 2)
                header.putShort((channels * 2).toShort())
                header.putShort(16)
                header.put("data".toByteArray(Charsets.US_ASCII))
                header.putInt(totalDataBytes)
                raf.seek(0)
                raf.write(header.array())
            }
        }
    }
}
