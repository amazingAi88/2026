package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class VoiceGender(
    val hindiLabel: String,
    val englishLabel: String,
    val basePitchOffset: Float
) {
    MALE("पुरुष", "Male", -0.10f),
    FEMALE("महिला", "Female", +0.18f),
    NEUTRAL("तटस्थ", "Neutral", +0.02f);

    companion object {
        fun fromRaw(raw: String?): VoiceGender =
            entries.firstOrNull { it.name == raw } ?: MALE
    }
}

enum class VoiceAgeGroup(
    val hindiLabel: String,
    val englishLabel: String,
    val pitchOffset: Float,
    val speedFactor: Float
) {
    CHILD("बच्चा", "Child", +0.34f, 1.08f),
    YOUNG_ADULT("युवा", "Young Adult", +0.08f, 1.04f),
    ADULT("वयस्क", "Adult", 0.0f, 1.00f),
    ELDER("वरिष्ठ", "Senior Elder", -0.14f, 0.88f);

    companion object {
        fun fromRaw(raw: String?): VoiceAgeGroup =
            entries.firstOrNull { it.name == raw } ?: ADULT
    }
}

enum class VoiceEffect(
    val hindiLabel: String,
    val englishLabel: String,
    val description: String,
    val pitchDelta: Float,
    val speedDelta: Float
) {
    NATURAL("स्वाभाविक", "Natural Cinema", "Clean studio Hindi dubbing", 0.0f, 0.0f),
    HERO_DEEP("हीरो भारी", "Deep Hero", "Warm cinematic bass presence", -0.12f, -0.04f),
    SWEET_BRIGHT("मधुर", "Bright Melodic", "Crisp expressive clarity", +0.10f, +0.03f),
    VILLAIN_GRAVEL("खलनायक", "Villain Gravel", "Dark gritty harmonic growl", -0.22f, -0.07f),
    ROBOT_SCIFI("रोबोट", "Sci-Fi Cyber", "Metallic ring-modulated voice", -0.04f, -0.02f),
    THEATER_ECHO("थिएटर इको", "Cinema Hall", "Epic multi-tap hall reverb", -0.02f, -0.04f),
    CARTOON_HIGH("कार्टून", "Animated High", "Playful high-pitch character", +0.32f, +0.10f);

    companion object {
        fun fromRaw(raw: String?): VoiceEffect =
            entries.firstOrNull { it.name == raw } ?: NATURAL
    }
}

enum class OriginalEmotion(
    val hindiLabel: String,
    val englishLabel: String,
    val pitchShift: Float,
    val speedShift: Float,
    val energyGain: Float
) {
    NEUTRAL("सामान्य", "Neutral", 0.0f, 0.0f, 1.0f),
    ANGRY("क्रोधी", "Intense / Angry", +0.06f, +0.10f, 1.25f),
    HAPPY("प्रसन्न", "Cheerful / Happy", +0.10f, +0.08f, 1.12f),
    SAD("भावुक / उदास", "Sad / Emotional", -0.08f, -0.14f, 0.88f),
    SUSPENSE("रहस्यमयी", "Suspense / Tense", -0.10f, -0.10f, 0.92f),
    ROMANTIC("रोमांटिक", "Warm / Romantic", -0.03f, -0.08f, 0.95f),
    EXCITED("उत्साहित", "Excited / Action", +0.12f, +0.14f, 1.22f),
    WHISPER("फुसफुसाहट", "Whisper / Hushed", -0.06f, -0.12f, 0.72f);

    companion object {
        fun fromRaw(raw: String?): OriginalEmotion =
            entries.firstOrNull { it.name == raw } ?: NEUTRAL
    }
}

data class VoiceStyleConfig(
    val gender: VoiceGender = VoiceGender.MALE,
    val ageGroup: VoiceAgeGroup = VoiceAgeGroup.ADULT,
    val effect: VoiceEffect = VoiceEffect.NATURAL,
    val pitchMultiplier: Float = 1.0f,
    val speedMultiplier: Float = 1.0f,
    val matchOriginalEmotion: Boolean = true
)

data class EffectiveVoiceParams(
    val finalPitch: Float,
    val finalSpeed: Float,
    val energyGain: Float,
    val effect: VoiceEffect,
    val gender: VoiceGender,
    val ageGroup: VoiceAgeGroup,
    val emotionApplied: OriginalEmotion?
)

fun computeEffectiveVoiceParams(
    gender: VoiceGender,
    ageGroup: VoiceAgeGroup,
    effect: VoiceEffect,
    userPitch: Float,
    userSpeed: Float,
    matchOriginalEmotion: Boolean,
    detectedEmotion: OriginalEmotion,
    emotionIntensity: Float
): EffectiveVoiceParams {
    val intensity = emotionIntensity.coerceIn(0f, 1f)
    val emotionPitch = if (matchOriginalEmotion) detectedEmotion.pitchShift * intensity else 0f
    val emotionSpeed = if (matchOriginalEmotion) detectedEmotion.speedShift * intensity else 0f
    val energyGain = if (matchOriginalEmotion) {
        1f + (detectedEmotion.energyGain - 1f) * intensity
    } else {
        1f
    }

    val rawPitch = userPitch +
        gender.basePitchOffset +
        ageGroup.pitchOffset +
        effect.pitchDelta +
        emotionPitch

    val rawSpeed = (userSpeed * ageGroup.speedFactor) +
        effect.speedDelta +
        emotionSpeed

    return EffectiveVoiceParams(
        finalPitch = rawPitch.coerceIn(0.45f, 1.85f),
        finalSpeed = rawSpeed.coerceIn(0.55f, 1.60f),
        energyGain = energyGain.coerceIn(0.55f, 1.35f),
        effect = effect,
        gender = gender,
        ageGroup = ageGroup,
        emotionApplied = if (matchOriginalEmotion) detectedEmotion else null
    )
}

@Keep
@IgnoreExtraProperties
data class DubbingProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val sourceLanguage: String = "English",
    val targetLanguage: String = "Hindi",
    val videoFileName: String = "",
    val videoUriString: String = "",
    val durationMs: Long = 0L,
    val defaultGender: String = VoiceGender.MALE.name,
    val defaultAgeGroup: String = VoiceAgeGroup.ADULT.name,
    val defaultEffect: String = VoiceEffect.NATURAL.name,
    val defaultPitch: Double = 1.0,
    val defaultSpeed: Double = 1.0,
    val matchOriginalEmotion: Boolean = true,
    val originalAudioVolume: Double = 0.15,
    val lineCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toVoiceStyleConfig(): VoiceStyleConfig = VoiceStyleConfig(
        gender = VoiceGender.fromRaw(defaultGender),
        ageGroup = VoiceAgeGroup.fromRaw(defaultAgeGroup),
        effect = VoiceEffect.fromRaw(defaultEffect),
        pitchMultiplier = defaultPitch.toFloat().coerceIn(0.4f, 2.0f),
        speedMultiplier = defaultSpeed.toFloat().coerceIn(0.5f, 2.0f),
        matchOriginalEmotion = matchOriginalEmotion
    )

    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "title" to title.trim().take(120).ifBlank { "Untitled Dub" },
        "sourceLanguage" to sourceLanguage.take(40).ifBlank { "English" },
        "targetLanguage" to targetLanguage.take(40).ifBlank { "Hindi" },
        "videoFileName" to videoFileName.take(200),
        "videoUriString" to videoUriString.take(1000),
        "durationMs" to durationMs.coerceIn(0L, 86_400_000L),
        "defaultGender" to VoiceGender.fromRaw(defaultGender).name,
        "defaultAgeGroup" to VoiceAgeGroup.fromRaw(defaultAgeGroup).name,
        "defaultEffect" to VoiceEffect.fromRaw(defaultEffect).name,
        "defaultPitch" to defaultPitch.coerceIn(0.4, 2.0),
        "defaultSpeed" to defaultSpeed.coerceIn(0.5, 2.0),
        "matchOriginalEmotion" to matchOriginalEmotion,
        "originalAudioVolume" to originalAudioVolume.coerceIn(0.0, 1.0),
        "lineCount" to lineCount.coerceIn(0L, 500L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class DubLine(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val orderIndex: Long = 0L,
    val speakerName: String = "Speaker 1",
    val startMs: Long = 0L,
    val endMs: Long = 3000L,
    val englishText: String = "",
    val hindiText: String = "",
    val emotion: String = OriginalEmotion.NEUTRAL.name,
    val emotionIntensity: Double = 0.7,
    val voiceGender: String = VoiceGender.MALE.name,
    val ageGroup: String = VoiceAgeGroup.ADULT.name,
    val voiceEffect: String = VoiceEffect.NATURAL.name,
    val pitchMultiplier: Double = 1.0,
    val speedMultiplier: Double = 1.0,
    val matchOriginalEmotion: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun effectiveParams(): EffectiveVoiceParams = computeEffectiveVoiceParams(
        gender = VoiceGender.fromRaw(voiceGender),
        ageGroup = VoiceAgeGroup.fromRaw(ageGroup),
        effect = VoiceEffect.fromRaw(voiceEffect),
        userPitch = pitchMultiplier.toFloat(),
        userSpeed = speedMultiplier.toFloat(),
        matchOriginalEmotion = matchOriginalEmotion,
        detectedEmotion = OriginalEmotion.fromRaw(emotion),
        emotionIntensity = emotionIntensity.toFloat()
    )

    fun toCreateMap(uid: String, projId: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "projectId" to projId,
        "orderIndex" to orderIndex.coerceIn(0L, 500L),
        "speakerName" to speakerName.trim().take(60).ifBlank { "Speaker" },
        "startMs" to startMs.coerceIn(0L, 86_400_000L),
        "endMs" to endMs.coerceIn(0L, 86_400_000L),
        "englishText" to englishText.trim().take(1000),
        "hindiText" to hindiText.trim().take(1000).ifBlank { "..." },
        "emotion" to OriginalEmotion.fromRaw(emotion).name,
        "emotionIntensity" to emotionIntensity.coerceIn(0.0, 1.0),
        "voiceGender" to VoiceGender.fromRaw(voiceGender).name,
        "ageGroup" to VoiceAgeGroup.fromRaw(ageGroup).name,
        "voiceEffect" to VoiceEffect.fromRaw(voiceEffect).name,
        "pitchMultiplier" to pitchMultiplier.coerceIn(0.4, 2.0),
        "speedMultiplier" to speedMultiplier.coerceIn(0.5, 2.0),
        "matchOriginalEmotion" to matchOriginalEmotion,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
