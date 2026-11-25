package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.DiaryEntry
import com.example.export.DiaryPdfExporter
import com.example.export.GeneratedPdfArtifact
import com.example.export.PdfExportScope
import com.example.ui.state.PdfExportState
import com.example.ui.theme.AmberInk
import com.example.ui.theme.CharcoalInk
import com.example.ui.theme.ParchmentCream
import com.example.ui.theme.ParchmentOutline
import com.example.ui.theme.WarmTaupeInk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun shareExportedPdf(context: Context, artifact: GeneratedPdfArtifact) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, artifact.contentUri)
        putExtra(Intent.EXTRA_SUBJECT, "Vespro — ${artifact.fileName}")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Condividi diario in PDF"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfExportScopeSheet(
    allEntries: List<DiaryEntry>,
    filteredEntries: List<DiaryEntry>,
    isFiltered: Boolean,
    onDismiss: () -> Unit,
    onConfirmScope: (selection: List<DiaryEntry>, collectionTitle: String, slug: String) -> Unit
) {
    val filteredCount = remember(filteredEntries) { filteredEntries.size }
    val last7 = remember(allEntries) {
        DiaryPdfExporter.selectEntriesForScope(allEntries, filteredEntries, PdfExportScope.LAST_7_DAYS)
    }
    val last30 = remember(allEntries) {
        DiaryPdfExporter.selectEntriesForScope(allEntries, filteredEntries, PdfExportScope.LAST_30_DAYS)
    }
    val allOrdered = remember(allEntries) {
        DiaryPdfExporter.selectEntriesForScope(allEntries, filteredEntries, PdfExportScope.ALL_ENTRIES)
    }
    val filteredOrdered = remember(allEntries, filteredEntries) {
        DiaryPdfExporter.selectEntriesForScope(allEntries, filteredEntries, PdfExportScope.FILTERED)
    }

    var selectedScope by remember(isFiltered, last7.size) {
        mutableStateOf(
            when {
                isFiltered && filteredCount > 0 -> PdfExportScope.FILTERED
                last7.isNotEmpty() -> PdfExportScope.LAST_7_DAYS
                else -> PdfExportScope.ALL_ENTRIES
            }
        )
    }

    val chosenEntries = when (selectedScope) {
        PdfExportScope.FILTERED -> filteredOrdered
        PdfExportScope.LAST_7_DAYS -> last7
        PdfExportScope.LAST_30_DAYS -> last30
        PdfExportScope.ALL_ENTRIES -> allOrdered
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Esporta raccolta in PDF",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Layout editoriale chiaro su carta avorio con testo riorganizzato, domande di riflessione e risposte.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isFiltered) {
                ScopeOptionRow(
                    title = "Pagine filtrate correnti",
                    subtitle = "$filteredCount ${if (filteredCount == 1) "pagina" else "pagine"} dalla ricerca attiva",
                    selected = selectedScope == PdfExportScope.FILTERED,
                    enabled = filteredCount > 0,
                    onClick = { selectedScope = PdfExportScope.FILTERED },
                    testTag = "pdf_scope_filtered"
                )
            }

            ScopeOptionRow(
                title = "Ultimi 7 giorni",
                subtitle = "${last7.size} ${if (last7.size == 1) "pagina" else "pagine"} recenti",
                selected = selectedScope == PdfExportScope.LAST_7_DAYS,
                enabled = last7.isNotEmpty(),
                onClick = { selectedScope = PdfExportScope.LAST_7_DAYS },
                testTag = "pdf_scope_7d"
            )

            ScopeOptionRow(
                title = "Ultimi 30 giorni",
                subtitle = "${last30.size} ${if (last30.size == 1) "pagina" else "pagine"} dell'ultimo mese",
                selected = selectedScope == PdfExportScope.LAST_30_DAYS,
                enabled = last30.isNotEmpty(),
                onClick = { selectedScope = PdfExportScope.LAST_30_DAYS },
                testTag = "pdf_scope_30d"
            )

            ScopeOptionRow(
                title = "Tutte le pagine del diario",
                subtitle = "${allOrdered.size} ${if (allOrdered.size == 1) "pagina" else "pagine"} totali",
                selected = selectedScope == PdfExportScope.ALL_ENTRIES,
                enabled = allOrdered.isNotEmpty(),
                onClick = { selectedScope = PdfExportScope.ALL_ENTRIES },
                testTag = "pdf_scope_all"
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = {
                    val (title, slug) = when (selectedScope) {
                        PdfExportScope.FILTERED -> "Selezione dal Diario" to "selezione"
                        PdfExportScope.LAST_7_DAYS -> "Ultimi 7 Giorni" to "ultimi_7_giorni"
                        PdfExportScope.LAST_30_DAYS -> "Ultimo Mese" to "ultimi_30_giorni"
                        PdfExportScope.ALL_ENTRIES -> "Diario Completo" to "diario_completo"
                    }
                    onConfirmScope(chosenEntries, title, slug)
                },
                enabled = chosenEntries.isNotEmpty(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("confirm_export_pdf_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.PictureAsPdf,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Crea PDF (${chosenEntries.size})")
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("Annulla")
            }
        }
    }
}

@Composable
private fun ScopeOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = { if (enabled) onClick() },
        enabled = enabled,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = if (enabled) onClick else null,
                enabled = enabled
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    }
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (enabled) 1f else 0.5f
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfExportResultSheet(
    pdfExportState: PdfExportState,
    onDismiss: () -> Unit,
    onSavedSuccess: () -> Unit,
    onSaveError: (String) -> Unit
) {
    if (pdfExportState is PdfExportState.Idle) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isCopyingToDevice by remember { mutableStateOf(false) }

    val readyArtifact = (pdfExportState as? PdfExportState.Ready)?.artifact

    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        val artifact = readyArtifact ?: return@rememberLauncherForActivityResult
        if (destUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isCopyingToDevice = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    DiaryPdfExporter.copyPdfToDestination(context, artifact.file, destUri)
                }
            }
            isCopyingToDevice = false
            result.onSuccess {
                onSavedSuccess()
            }.onFailure { err ->
                onSaveError(err.localizedMessage ?: "Salvataggio del PDF non riuscito.")
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (pdfExportState) {
                is PdfExportState.Generating -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Impaginazione del diario in PDF…",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Composizione su carta avorio in corso",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is PdfExportState.Error -> {
                    Text(
                        text = "Esportazione non riuscita",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = pdfExportState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Chiudi")
                    }
                }

                is PdfExportState.Ready -> {
                    val artifact = pdfExportState.artifact
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(26.dp)
                        )
                        Text(
                            text = "PDF pronto",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Ivory Paper Editorial Preview Card
                    Surface(
                        color = ParchmentCream,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, ParchmentOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PictureAsPdf,
                                contentDescription = null,
                                tint = AmberInk,
                                modifier = Modifier.size(34.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = artifact.fileName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CharcoalInk
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${artifact.pageCount} ${if (artifact.pageCount == 1) "pagina" else "pagine"} A4 · ${artifact.entryCount} ${if (artifact.entryCount == 1) "voce" else "voci"} · Carta avorio",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WarmTaupeInk
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { shareExportedPdf(context, artifact) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("share_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Condividi PDF")
                    }

                    OutlinedButton(
                        onClick = { saveDocumentLauncher.launch(artifact.fileName) },
                        enabled = !isCopyingToDevice,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("save_pdf_device_button")
                    ) {
                        if (isCopyingToDevice) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Salvataggio…")
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Salva su dispositivo")
                        }
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Fatto")
                    }
                }

                PdfExportState.Idle -> Unit
            }
        }
    }
}
