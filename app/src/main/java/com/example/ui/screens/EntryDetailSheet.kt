package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.DiaryEntry
import com.example.ui.components.EditorialAphorismPreviewCard
import com.example.ui.state.AiTaskState
import com.example.ui.state.AphorismPreview
import com.example.ui.theme.ValenceCalm
import com.example.ui.theme.ValenceHeavy
import com.example.ui.theme.ValenceNeutral
import com.example.ui.theme.ValenceSerene
import com.example.ui.theme.ValenceTense

fun valenceColor(valence: Long): Color = when (valence.toInt()) {
    1 -> ValenceHeavy
    2 -> ValenceTense
    3 -> ValenceNeutral
    4 -> ValenceCalm
    else -> ValenceSerene
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryDetailOverlay(
    entry: DiaryEntry,
    aiTaskState: AiTaskState,
    generatedAphorism: AphorismPreview?,
    isDefaultBedtime: Boolean,
    onClose: () -> Unit,
    onAnalyzeEntry: (DiaryEntry) -> Unit,
    onRefreshQuestion: (DiaryEntry) -> Unit,
    onSaveAnswer: (String, String) -> Unit,
    onGenerateAphorism: (DiaryEntry, Boolean) -> Unit,
    onSaveAphorismToRegister: () -> Unit,
    onExportPdf: (DiaryEntry) -> Unit,
    onDeleteEntry: (String) -> Unit
) {
    BackHandler(onBack = onClose)

    var selectedTab by remember(entry.id, entry.reorganizedText) {
        mutableIntStateOf(if (entry.reorganizedText.isNotBlank()) 0 else 1)
    }
    var answerDraft by remember(entry.id, entry.reflectionAnswer) {
        mutableStateOf(entry.reflectionAnswer)
    }
    var bedtimeMode by remember(entry.id) { mutableStateOf(isDefaultBedtime) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val isAnalyzing = aiTaskState is AiTaskState.AnalyzingEntry
    val isRefreshingQuestion = aiTaskState is AiTaskState.RefreshingQuestion
    val isGeneratingAphorism = aiTaskState is AiTaskState.GeneratingAphorism
    val entryAphorism = generatedAphorism?.takeIf { it.entryId == entry.id }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize()
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.testTag("entry_detail_back")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Torna indietro"
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = entry.dateKey,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (entry.usedVoice) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Mic,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Flusso vocale",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onExportPdf(entry) },
                            modifier = Modifier.testTag("entry_detail_export_pdf")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PictureAsPdf,
                                contentDescription = "Esporta in PDF",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.testTag("entry_detail_delete")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Elimina pagina",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Text(
                        text = entry.title.ifBlank { "Pagina della sera" },
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    // Sentiment & Clarity Snapshot Card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(valenceColor(entry.sentimentValence))
                                    )
                                    Text(
                                        text = entry.sentimentLabel.ifBlank { "Tono neutro" },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (entry.mainTheme.isNotBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = entry.mainTheme,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "Tono: ${entry.sentimentValence}/5",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Chiarezza mentale: ${entry.mentalClarity}/5",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (entry.takeaway.isNotBlank()) {
                                Text(
                                    text = entry.takeaway,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (entry.themes.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    entry.themes.forEach { tag ->
                                        AssistChip(
                                            onClick = {},
                                            label = { Text("#$tag", style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Reflection Question Card
                    if (entry.reflectionQuestion.isNotBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
                            ),
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "SPUNTO SUL TEMA DEL GIORNO",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    TextButton(
                                        onClick = { onRefreshQuestion(entry) },
                                        enabled = !isRefreshingQuestion && !isAnalyzing,
                                        modifier = Modifier.testTag("refresh_question_button")
                                    ) {
                                        if (isRefreshingQuestion) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.Refresh,
                                                contentDescription = "Cambia domanda",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Altra domanda")
                                    }
                                }

                                Text(
                                    text = entry.reflectionQuestion,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                OutlinedTextField(
                                    value = answerDraft,
                                    onValueChange = { answerDraft = it },
                                    placeholder = {
                                        Text("Scrivi qui cosa ti viene in mente, se ti va…")
                                    },
                                    minLines = 3,
                                    maxLines = 8,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("reflection_answer_input")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Button(
                                        onClick = { onSaveAnswer(entry.id, answerDraft) },
                                        enabled = answerDraft.trim() != entry.reflectionAnswer.trim(),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.testTag("save_reflection_answer_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Salva risposta")
                                    }
                                }
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onAnalyzeEntry(entry) },
                            enabled = !isAnalyzing,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("analyze_raw_entry_button")
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Elaborazione con Gemini…")
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Riorganizza e genera domanda con Gemini")
                            }
                        }
                    }

                    // Reorganized vs Original Transcript
                    if (entry.reorganizedText.isNotBlank()) {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) {
                                Text("Riorganizzato")
                            }
                            SegmentedButton(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) {
                                Text("Flusso originale")
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (selectedTab == 0 && entry.reorganizedText.isNotBlank()) {
                                    "TESTO RIORGANIZZATO"
                                } else {
                                    "FLUSSO DI COSCIENZA ORIGINALE"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (selectedTab == 0 && entry.reorganizedText.isNotBlank()) {
                                    entry.reorganizedText
                                } else {
                                    entry.rawTranscript
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (entry.reorganizedText.isNotBlank()) {
                        TextButton(
                            onClick = { onAnalyzeEntry(entry) },
                            enabled = !isAnalyzing,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Rianalizza con Gemini")
                        }
                    }

                    // On-demand Evocative Phrase / Aphorism / Goodnight Thought
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "FRASE O AFORISMA SU RICHIESTA",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "Vuoi distillare questa pagina in una frase suggestiva o in un pensiero della buonanotte da tenere nel registro o condividere?",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = !bedtimeMode,
                                    onClick = { bedtimeMode = false },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.WbSunny,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text("Del momento") }
                                )
                                FilterChip(
                                    selected = bedtimeMode,
                                    onClick = { bedtimeMode = true },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.NightsStay,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text("Buonanotte") }
                                )
                            }

                            OutlinedButton(
                                onClick = { onGenerateAphorism(entry, bedtimeMode) },
                                enabled = !isGeneratingAphorism && !isAnalyzing,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("entry_generate_aphorism_button")
                            ) {
                                if (isGeneratingAphorism) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Cerco le parole giuste…")
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.FormatQuote,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (bedtimeMode) "Genera frase della buonanotte"
                                        else "Genera frase o aforisma"
                                    )
                                }
                            }
                        }
                    }

                    if (entryAphorism != null) {
                        EditorialAphorismPreviewCard(
                            quoteText = entryAphorism.quoteText,
                            attribution = entryAphorism.attribution,
                            momentLabel = entryAphorism.momentLabel,
                            themeTag = entryAphorism.themeTag,
                            isSaved = entryAphorism.isSaved,
                            isRegenerating = isGeneratingAphorism,
                            onSaveToRegister = onSaveAphorismToRegister,
                            onRegenerate = { onGenerateAphorism(entry, bedtimeMode) }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminare questa pagina?") },
            text = { Text("La pagina verrà rimossa definitivamente dal tuo diario.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteEntry(entry.id)
                    }
                ) {
                    Text("Elimina", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Annulla")
                }
            }
        )
    }
}
