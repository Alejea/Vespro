package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CloudSyncStatus
import com.example.data.DiaryEntry
import com.example.data.SyncPhase
import com.example.ui.components.PdfExportScopeSheet
import com.example.ui.state.UiState
import java.util.Locale

@Composable
fun JournalFeedScreen(
    entriesState: UiState<List<DiaryEntry>>,
    cloudSyncStatus: CloudSyncStatus,
    onOpenEntry: (String) -> Unit,
    onExportCollectionPdf: (entries: List<DiaryEntry>, collectionTitle: String, slug: String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTheme by remember { mutableStateOf<String?>(null) }
    var showExportScopeSheet by remember { mutableStateOf(false) }
    val loadedEntries = (entriesState as? UiState.Success)?.data.orEmpty()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Le tue pagine",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Cronologia privata dei tuoi sfoghi e delle tue serate",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (loadedEntries.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(12.dp))
                    FilledTonalButton(
                        onClick = { showExportScopeSheet = true },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("journal_export_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PDF")
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            CloudSyncStatusBadge(cloudSyncStatus = cloudSyncStatus)
            Spacer(modifier = Modifier.height(12.dp))

            when (entriesState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = entriesState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                is UiState.Success -> {
                    val entries = entriesState.data
                    if (entries.isEmpty()) {
                        EmptyDiaryState()
                    } else {
                        val allThemes = remember(entries) {
                            entries.flatMap { it.themes }
                                .map { it.trim().lowercase(Locale.ITALIAN) }
                                .filter { it.isNotEmpty() }
                                .distinct()
                                .take(10)
                        }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Cerca nei pensieri o nei temi…") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Search, contentDescription = null)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Rounded.Clear, contentDescription = "Pulisci ricerca")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("journal_search_input")
                        )

                        if (allThemes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 2.dp)
                            ) {
                                item {
                                    FilterChip(
                                        selected = selectedTheme == null,
                                        onClick = { selectedTheme = null },
                                        label = { Text("Tutte") }
                                    )
                                }
                                items(allThemes) { theme ->
                                    FilterChip(
                                        selected = selectedTheme == theme,
                                        onClick = {
                                            selectedTheme = if (selectedTheme == theme) null else theme
                                        },
                                        label = { Text("#$theme") }
                                    )
                                }
                            }
                        }

                        val filtered = remember(entries, searchQuery, selectedTheme) {
                            val q = searchQuery.trim().lowercase(Locale.ITALIAN)
                            entries.filter { entry ->
                                val matchesTheme = selectedTheme == null ||
                                    entry.themes.any { it.equals(selectedTheme, ignoreCase = true) }
                                val matchesQuery = q.isEmpty() ||
                                    entry.title.lowercase(Locale.ITALIAN).contains(q) ||
                                    entry.rawTranscript.lowercase(Locale.ITALIAN).contains(q) ||
                                    entry.reorganizedText.lowercase(Locale.ITALIAN).contains(q) ||
                                    entry.mainTheme.lowercase(Locale.ITALIAN).contains(q)
                                matchesTheme && matchesQuery
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filtered, key = { it.id }) { entry ->
                                DiaryEntryCard(
                                    entry = entry,
                                    onClick = { onOpenEntry(entry.id) }
                                )
                            }
                        }

                        if (showExportScopeSheet) {
                            PdfExportScopeSheet(
                                allEntries = entries,
                                filteredEntries = filtered,
                                isFiltered = searchQuery.isNotBlank() || selectedTheme != null,
                                onDismiss = { showExportScopeSheet = false },
                                onConfirmScope = { selection, collectionTitle, slug ->
                                    showExportScopeSheet = false
                                    onExportCollectionPdf(selection, collectionTitle, slug)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiaryEntryCard(
    entry: DiaryEntry,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diary_entry_card_${entry.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    Text(
                        text = entry.dateKey,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (entry.usedVoice) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = "Dettatura vocale",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                if (entry.sentimentLabel.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(valenceColor(entry.sentimentValence))
                        )
                        Text(
                            text = entry.sentimentLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = entry.title.ifBlank { "Appunti della sera" },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = entry.reorganizedText.ifBlank { entry.rawTranscript },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            if (entry.reflectionQuestion.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QuestionAnswer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = entry.reflectionQuestion,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDiaryState() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_vespro_twilight),
                contentDescription = "Diario vuoto pronto per la prima pagina",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Text(
                text = "La prima pagina ti aspetta",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Vai nella scheda Scrivi, premi il microfono o butta giù due righe su come stai. Qui ritrovi tutte le tue pagine, le riflessioni e le frasi salvate.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CloudSyncStatusBadge(cloudSyncStatus: CloudSyncStatus) {
    val (icon, label, tint, containerColor) = when (cloudSyncStatus.phase) {
        SyncPhase.SYNCED -> Quadruple(
            Icons.Rounded.CloudDone,
            "Sincronizzato in cloud",
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
        )
        SyncPhase.SYNCING -> Quadruple(
            Icons.Rounded.CloudSync,
            "Sincronizzazione cloud…",
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        )
        SyncPhase.PENDING_UPLOAD -> Quadruple(
            Icons.Rounded.CloudUpload,
            "Modifiche in attesa di rete (salvate sul dispositivo)",
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        )
        SyncPhase.OFFLINE_CACHE -> Quadruple(
            Icons.Rounded.CloudOff,
            "Modalità offline · sincronizzazione automatica al ritorno della rete",
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
        SyncPhase.ERROR -> Quadruple(
            Icons.Rounded.CloudOff,
            cloudSyncStatus.errorMessage ?: "Sincronizzazione cloud non riuscita",
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
        )
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("journal_sync_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
