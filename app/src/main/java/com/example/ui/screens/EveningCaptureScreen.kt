package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DiaryEntry
import com.example.ui.state.AiTaskState
import com.example.voice.VoiceStreamManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun EveningCaptureScreen(
    draftTranscript: String,
    aiTaskState: AiTaskState,
    latestEntry: DiaryEntry?,
    onDraftChange: (String) -> Unit,
    onVoiceSegmentCommitted: (String) -> Unit,
    onClearDraft: () -> Unit,
    onSaveWithGemini: () -> Unit,
    onSaveRawOnly: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onDismissError: () -> Unit
) {
    val context = LocalContext.current
    val voiceManager = remember(context) {
        VoiceStreamManager(context, onSegmentCommitted = onVoiceSegmentCommitted)
    }
    val voiceState by voiceManager.state.collectAsStateWithLifecycle()

    DisposableEffect(voiceManager) {
        onDispose {
            voiceManager.release()
        }
    }

    var permissionDeniedNote by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDeniedNote = false
            voiceManager.startListening()
        } else {
            permissionDeniedNote = true
        }
    }

    val systemSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.trim().orEmpty()
            if (spoken.isNotEmpty()) {
                onVoiceSegmentCommitted(spoken)
            }
        }
    }

    val pulseScale by animateFloatAsState(
        targetValue = if (voiceState.isListening) 1f + (voiceState.rmsLevel * 0.28f) else 1f,
        animationSpec = spring(stiffness = 380f),
        label = "micPulse"
    )

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour in 5..11 -> "Buongiorno"
            hour in 12..17 -> "Buon pomeriggio"
            hour in 18..22 -> "Buonasera"
            else -> "Notte quieta"
        }
    }

    val todayFormatted = remember {
        SimpleDateFormat("EEEE d MMMM", Locale.ITALIAN)
            .format(Date())
            .replaceFirstChar { it.titlecase(Locale.ITALIAN) }
    }

    val isBusy = aiTaskState is AiTaskState.AnalyzingEntry || aiTaskState is AiTaskState.SavingRawEntry
    val wordCount = remember(draftTranscript) {
        draftTranscript.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = todayFormatted.uppercase(Locale.ITALIAN),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "$greeting. Cosa hai in testa?",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Di giorno per sfogarti o la sera prima di dormire: un dialogo onesto con te stesso, senza filtri né giudizio.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Voice Stream-of-Consciousness Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (voiceState.isListening) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (voiceState.isListening) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .scale(pulseScale)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                        CircleShape
                                    )
                            )
                        }
                        FilledIconButton(
                            onClick = {
                                if (voiceState.isListening) {
                                    voiceManager.stopListening()
                                } else {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        permissionDeniedNote = false
                                        voiceManager.startListening()
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            enabled = !isBusy,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (voiceState.isListening) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                contentColor = if (voiceState.isListening) {
                                    MaterialTheme.colorScheme.onError
                                } else {
                                    MaterialTheme.colorScheme.onPrimary
                                }
                            ),
                            modifier = Modifier
                                .size(60.dp)
                                .testTag("voice_record_button")
                        ) {
                            Icon(
                                imageVector = if (voiceState.isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                contentDescription = if (voiceState.isListening) {
                                    "Ferma flusso vocale"
                                } else {
                                    "Avvia flusso vocale"
                                },
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (voiceState.isListening) {
                                "Flusso vocale attivo"
                            } else {
                                "Flusso di coscienza vocale"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = voiceState.statusHint ?: if (voiceState.isRecognizerAvailable) {
                                "Tocca il microfono per dettare continuamente in italiano."
                            } else {
                                "Usa la dettatura di sistema o scrivi qui sotto."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!voiceState.isListening) {
                            TextButton(
                                onClick = {
                                    try {
                                        systemSpeechLauncher.launch(voiceManager.createFallbackSpeechIntent())
                                    } catch (_: Exception) {
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("system_speech_intent_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Dettatura di sistema",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = voiceState.partialSegment.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 18.dp, bottom = 16.dp)
                    ) {
                        Text(
                            text = "“${voiceState.partialSegment}…”",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            if (permissionDeniedNote) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Permesso microfono negato. Puoi comunque scrivere la tua pagina o usare la tastiera vocale.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Freeform Diary Canvas Input
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PAGINA DI OGGI",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "$wordCount parole",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (draftTranscript.isNotBlank() && !isBusy) {
                                TextButton(
                                    onClick = onClearDraft,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Clear,
                                        contentDescription = "Svuota testo",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Svuota")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = draftTranscript,
                        onValueChange = onDraftChange,
                        enabled = !isBusy,
                        placeholder = {
                            Text(
                                text = "Gioie, dolori, sfoghi o pensieri sparsi: parla con te stesso senza dover sembrare diverso da come stai…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        ),
                        shape = RoundedCornerShape(18.dp),
                        minLines = 6,
                        maxLines = 14,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("diary_transcript_input")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (aiTaskState is AiTaskState.Error) {
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = aiTaskState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onDismissError) {
                                    Text("OK")
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (voiceState.isListening) voiceManager.stopListening()
                            onSaveWithGemini()
                        },
                        enabled = draftTranscript.isNotBlank() && !isBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("save_with_gemini_button")
                    ) {
                        if (aiTaskState is AiTaskState.AnalyzingEntry) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Riordino e preparo la domanda…")
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Salva e rifletti con Gemini")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            if (voiceState.isListening) voiceManager.stopListening()
                            onSaveRawOnly()
                        },
                        enabled = draftTranscript.isNotBlank() && !isBusy,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_raw_only_button")
                    ) {
                        if (aiTaskState is AiTaskState.SavingRawEntry) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Salvataggio…")
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.BookmarkAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Salva solo il testo così com'è")
                        }
                    }
                }
            }

            // Latest Reflection Prompt Preview
            if (latestEntry != null && latestEntry.reflectionQuestion.isNotBlank()) {
                Card(
                    onClick = { onOpenEntry(latestEntry.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("latest_reflection_card")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ULTIMA DOMANDA DI RIFLESSIONE",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = latestEntry.mainTheme,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Text(
                            text = latestEntry.reflectionQuestion,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (latestEntry.reflectionAnswer.isNotBlank()) {
                                "Hai già risposto • Tocca per rileggere"
                            } else {
                                "Tocca per aprire la pagina e rispondere"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
