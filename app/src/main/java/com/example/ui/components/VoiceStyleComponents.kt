package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Elderly
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Face3
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Man
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Woman
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.DubLine
import com.example.model.OriginalEmotion
import com.example.model.VoiceAgeGroup
import com.example.model.VoiceEffect
import com.example.model.VoiceGender
import com.example.model.VoiceStyleConfig
import com.example.model.computeEffectiveVoiceParams
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceStyleConfigurator(
    style: VoiceStyleConfig,
    onStyleChange: (VoiceStyleConfig) -> Unit,
    modifier: Modifier = Modifier,
    previewEmotion: OriginalEmotion = OriginalEmotion.SUSPENSE,
    previewIntensity: Float = 0.8f
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Voice Gender Selection
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(
                icon = Icons.Rounded.RecordVoiceOver,
                title = "आवाज़ का लिंग (Voice Gender)"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VoiceGender.entries.forEach { gender ->
                    val selected = style.gender == gender
                    val icon = when (gender) {
                        VoiceGender.MALE -> Icons.Rounded.Man
                        VoiceGender.FEMALE -> Icons.Rounded.Woman
                        VoiceGender.NEUTRAL -> Icons.Rounded.Person
                    }
                    SelectableOptionCard(
                        selected = selected,
                        onClick = { onStyleChange(style.copy(gender = gender)) },
                        icon = icon,
                        primaryLabel = gender.hindiLabel,
                        secondaryLabel = gender.englishLabel,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("gender_option_${gender.name.lowercase(Locale.ROOT)}")
                    )
                }
            }
        }

        // 2. Age Group Selection
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(
                icon = Icons.Rounded.Face,
                title = "आयु वर्ग (Age Group)"
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VoiceAgeGroup.entries.forEach { age ->
                    val selected = style.ageGroup == age
                    val icon = when (age) {
                        VoiceAgeGroup.CHILD -> Icons.Rounded.ChildCare
                        VoiceAgeGroup.YOUNG_ADULT -> Icons.Rounded.Face3
                        VoiceAgeGroup.ADULT -> Icons.Rounded.Person
                        VoiceAgeGroup.ELDER -> Icons.Rounded.Elderly
                    }
                    SelectablePill(
                        selected = selected,
                        onClick = { onStyleChange(style.copy(ageGroup = age)) },
                        icon = icon,
                        primaryLabel = age.hindiLabel,
                        secondaryLabel = age.englishLabel,
                        modifier = Modifier.testTag("age_option_${age.name.lowercase(Locale.ROOT)}")
                    )
                }
            }
        }

        // 3. Match Original Actor Emotional Tone Toggle
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (style.matchOriginalEmotion) {
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            border = BorderStroke(
                width = 1.dp,
                color = if (style.matchOriginalEmotion) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "मूल कलाकार के भाव से मिलाएँ",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Match original actor's emotional tone",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = style.matchOriginalEmotion,
                        onCheckedChange = { checked ->
                            onStyleChange(style.copy(matchOriginalEmotion = checked))
                        },
                        modifier = Modifier.testTag("match_emotion_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onTertiary,
                            checkedTrackColor = MaterialTheme.colorScheme.tertiary
                        )
                    )
                }

                AnimatedVisibility(visible = style.matchOriginalEmotion) {
                    val effective = computeEffectiveVoiceParams(
                        gender = style.gender,
                        ageGroup = style.ageGroup,
                        effect = style.effect,
                        userPitch = style.pitchMultiplier,
                        userSpeed = style.speedMultiplier,
                        matchOriginalEmotion = true,
                        detectedEmotion = previewEmotion,
                        emotionIntensity = previewIntensity
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "भाव मिलान सक्रिय: ${previewEmotion.hindiLabel} → Pitch ${
                                String.format(Locale.US, "%.2fx", effective.finalPitch)
                            } • Speed ${
                                String.format(Locale.US, "%.2fx", effective.finalSpeed)
                            } • Energy ${(effective.energyGain * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // 4. Voice Changer Modulation Effects
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(
                icon = Icons.Rounded.GraphicEq,
                title = "आवाज़ बदलने के फ़िल्टर (Voice Changer FX)"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                VoiceEffect.entries.forEach { fx ->
                    val selected = style.effect == fx
                    val icon = when (fx) {
                        VoiceEffect.NATURAL -> Icons.Rounded.RecordVoiceOver
                        VoiceEffect.HERO_DEEP -> Icons.Rounded.Speaker
                        VoiceEffect.SWEET_BRIGHT -> Icons.Rounded.AutoAwesome
                        VoiceEffect.VILLAIN_GRAVEL -> Icons.Rounded.TheaterComedy
                        VoiceEffect.ROBOT_SCIFI -> Icons.Rounded.SmartToy
                        VoiceEffect.THEATER_ECHO -> Icons.Rounded.GraphicEq
                        VoiceEffect.CARTOON_HIGH -> Icons.Rounded.ChildCare
                    }
                    Surface(
                        onClick = { onStyleChange(style.copy(effect = fx)) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        ),
                        modifier = Modifier
                            .width(142.dp)
                            .testTag("fx_option_${fx.name.lowercase(Locale.ROOT)}")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = fx.hindiLabel,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = fx.englishLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Text(
                                text = fx.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }

        // 5. Fine-Grain Pitch & Speed Controls
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SectionHeader(
                    icon = Icons.Rounded.Tune,
                    title = "पिच और गति नियंत्रण (Pitch & Speed)"
                )

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "स्वर ऊँचाई (Pitch Shift)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = String.format(Locale.US, "%.2fx", style.pitchMultiplier),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = style.pitchMultiplier,
                        onValueChange = { onStyleChange(style.copy(pitchMultiplier = it)) },
                        valueRange = 0.55f..1.65f,
                        modifier = Modifier.testTag("pitch_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "बोलने की गति (Lip-Sync Tempo)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = String.format(Locale.US, "%.2fx", style.speedMultiplier),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Slider(
                        value = style.speedMultiplier,
                        onValueChange = { onStyleChange(style.copy(speedMultiplier = it)) },
                        valueRange = 0.70f..1.45f,
                        modifier = Modifier.testTag("speed_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.secondary,
                            activeTrackColor = MaterialTheme.colorScheme.secondary
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SelectableOptionCard(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    primaryLabel: String,
    secondaryLabel: String,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = modifier.heightIn(min = 64.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = primaryLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = secondaryLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SelectablePill(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    primaryLabel: String,
    secondaryLabel: String,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = primaryLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = secondaryLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun EmotionBadge(
    emotion: OriginalEmotion,
    intensity: Float,
    modifier: Modifier = Modifier
) {
    val badgeColor = when (emotion) {
        OriginalEmotion.ANGRY -> MaterialTheme.colorScheme.error
        OriginalEmotion.EXCITED, OriginalEmotion.HAPPY -> MaterialTheme.colorScheme.primary
        OriginalEmotion.ROMANTIC -> MaterialTheme.colorScheme.secondary
        OriginalEmotion.SUSPENSE, OriginalEmotion.WHISPER -> MaterialTheme.colorScheme.tertiary
        OriginalEmotion.SAD -> Color(0xFF7B9CFF)
        OriginalEmotion.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = badgeColor.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(50.dp),
        modifier = modifier
    ) {
        Text(
            text = "${emotion.hindiLabel} (${emotion.englishLabel}) • ${(intensity * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = badgeColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LineVoiceEditorDialog(
    line: DubLine,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onReAdaptWithAi: (
        englishText: String,
        currentHindi: String,
        gender: VoiceGender,
        age: VoiceAgeGroup,
        emotion: OriginalEmotion,
        onResult: (String) -> Unit
    ) -> Unit,
    onSave: (DubLine) -> Unit
) {
    var speakerName by remember(line.id) { mutableStateOf(line.speakerName) }
    var englishText by remember(line.id) { mutableStateOf(line.englishText) }
    var hindiText by remember(line.id) { mutableStateOf(line.hindiText) }
    var selectedEmotion by remember(line.id) { mutableStateOf(OriginalEmotion.fromRaw(line.emotion)) }
    var emotionIntensity by remember(line.id) { mutableFloatStateOf(line.emotionIntensity.toFloat()) }
    var style by remember(line.id) {
        mutableStateOf(
            VoiceStyleConfig(
                gender = VoiceGender.fromRaw(line.voiceGender),
                ageGroup = VoiceAgeGroup.fromRaw(line.ageGroup),
                effect = VoiceEffect.fromRaw(line.voiceEffect),
                pitchMultiplier = line.pitchMultiplier.toFloat(),
                speedMultiplier = line.speedMultiplier.toFloat(),
                matchOriginalEmotion = line.matchOriginalEmotion
            )
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 760.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "संवाद और आवाज़ संपादन (Edit Dialogue & Voice)",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = speakerName,
                    onValueChange = { speakerName = it },
                    label = { Text("पात्र का नाम (Speaker Name)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = englishText,
                    onValueChange = { englishText = it },
                    label = { Text("मूल अंग्रेज़ी संवाद (Original English)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = hindiText,
                    onValueChange = { hindiText = it },
                    label = { Text("हिन्दी डबिंग संवाद (Hindi Dub)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_hindi_text_field")
                )

                OutlinedButton(
                    onClick = {
                        onReAdaptWithAi(
                            englishText,
                            hindiText,
                            style.gender,
                            style.ageGroup,
                            selectedEmotion
                        ) { newHindi ->
                            hindiText = newHindi
                        }
                    },
                    enabled = !isBusy && (englishText.isNotBlank() || hindiText.isNotBlank()),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI से चुने हुए भाव/उम्र में फिर लिखें (Re-adapt Hindi)")
                }

                // Original Actor Emotion Override
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "मूल अभिनेता का भाव (Original Actor Emotion)",
                        style = MaterialTheme.typography.titleSmall
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OriginalEmotion.entries.forEach { emo ->
                            val isSelected = selectedEmotion == emo
                            Surface(
                                onClick = { selectedEmotion = emo },
                                shape = RoundedCornerShape(50.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Text(
                                    text = "${emo.hindiLabel} (${emo.englishLabel})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                VoiceStyleConfigurator(
                    style = style,
                    onStyleChange = { style = it },
                    previewEmotion = selectedEmotion,
                    previewIntensity = emotionIntensity
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("रद्द करें (Cancel)")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                line.copy(
                                    speakerName = speakerName.trim().ifBlank { "Speaker" },
                                    englishText = englishText.trim(),
                                    hindiText = hindiText.trim().ifBlank { "..." },
                                    emotion = selectedEmotion.name,
                                    emotionIntensity = emotionIntensity.toDouble(),
                                    voiceGender = style.gender.name,
                                    ageGroup = style.ageGroup.name,
                                    voiceEffect = style.effect.name,
                                    pitchMultiplier = style.pitchMultiplier.toDouble(),
                                    speedMultiplier = style.speedMultiplier.toDouble(),
                                    matchOriginalEmotion = style.matchOriginalEmotion
                                )
                            )
                        },
                        enabled = !isBusy && hindiText.isNotBlank(),
                        modifier = Modifier.testTag("save_line_button")
                    ) {
                        Text("सेव करें (Save)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
