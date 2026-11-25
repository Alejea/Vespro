package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.DiaryEntry
import com.example.data.SavedAphorism
import com.example.data.WeeklySummary
import com.example.ui.components.EditorialAphorismPreviewCard
import com.example.ui.components.SavedAphorismRegisterCard
import com.example.ui.state.AiTaskState
import com.example.ui.state.AphorismPreview
import com.example.ui.state.DiaryViewModel
import com.example.ui.state.UiState
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InsightsScreen(
    entriesState: UiState<List<DiaryEntry>>,
    summariesState: UiState<List<WeeklySummary>>,
    aphorismsState: UiState<List<SavedAphorism>>,
    generatedAphorism: AphorismPreview?,
    isDefaultBedtime: Boolean,
    aiTaskState: AiTaskState,
    onGenerateWeeklySummary: (List<DiaryEntry>) -> Unit,
    onDeleteSummary: (String) -> Unit,
    onGenerateAphorism: (List<DiaryEntry>, Boolean) -> Unit,
    onSaveAphorismToRegister: () -> Unit,
    onDeleteAphorism: (String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val entries = (entriesState as? UiState.Success)?.data.orEmpty()
    val stats = remember(entries) { DiaryViewModel.computeRecurringStats(entries) }
    val isGenerating = aiTaskState is AiTaskState.GeneratingWeekly
    val isGeneratingAphorism = aiTaskState is AiTaskState.GeneratingAphorism
    var bedtimeMode by remember { mutableStateOf(isDefaultBedtime) }
    var summaryToDelete by remember { mutableStateOf<WeeklySummary?>(null) }
    var aphorismToDelete by remember { mutableStateOf<SavedAphorism?>(null) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Sintesi & Sentiment",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Osservazioni ricorrenti sulle tue giornate, senza giudizi né classifiche.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (entries.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Insights,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Ancora nessun dato da osservare",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Appena salvi le prime pagine nella scheda Scrivi, qui vedrai l'andamento del sentiment ricorrente, i temi più frequenti e le sintesi settimanali.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Recurring Sentiment & Clarity Trend Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ANDAMENTO TONO & CHIAREZZA",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${stats.totalEntries} pagine",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MetricPill(
                                label = "Tono medio",
                                value = String.format(Locale.ITALIAN, "%.1f / 5", stats.averageValence),
                                modifier = Modifier.weight(1f)
                            )
                            MetricPill(
                                label = "Chiarezza media",
                                value = String.format(Locale.ITALIAN, "%.1f / 5", stats.averageClarity),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (stats.recentTrend.size >= 2) {
                            SentimentTrendCanvas(
                                entries = stats.recentTrend,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stats.recentTrend.first().dateKey,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Linea continua: tono • Tratteggiata: chiarezza",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stats.recentTrend.last().dateKey,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Recurring Sentiments & Themes
                if (stats.topSentiments.isNotEmpty() || stats.topThemes.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (stats.topSentiments.isNotEmpty()) {
                                Text(
                                    text = "SENTIMENT RICORRENTI",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    stats.topSentiments.forEach { (label, count) ->
                                        AssistChip(
                                            onClick = {},
                                            label = {
                                                Text(
                                                    text = if (count > 1) "$label · $count" else label,
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        )
                                    }
                                }
                            }

                            if (stats.topThemes.isNotEmpty()) {
                                Text(
                                    text = "TEMI CHE TORNANO SPESSO",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    stats.topThemes.forEach { (theme, count) ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(
                                                text = if (count > 1) "#$theme ($count)" else "#$theme",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Weekly Synthesis CTA
                Button(
                    onClick = { onGenerateWeeklySummary(entries) },
                    enabled = !isGenerating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("generate_weekly_summary_button")
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Tiro le somme con Gemini…")
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Tira le somme settimanali con Gemini")
                    }
                }
            }

            // Saved Weekly Summaries
            when (summariesState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
                is UiState.Error -> {
                    Text(
                        text = summariesState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is UiState.Success -> {
                    summariesState.data.forEach { summary ->
                        WeeklySummaryCard(
                            summary = summary,
                            onDeleteClick = { summaryToDelete = summary }
                        )
                    }
                }
            }

            // On-Demand Aphorism & Personal Register
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
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
                        text = "Distilla le tue ultime pagine in un aforisma o in una frase della buonanotte da condividere o custodire nel registro.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        onClick = { onGenerateAphorism(entries, bedtimeMode) },
                        enabled = !isGeneratingAphorism && !isGenerating,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("insights_generate_aphorism_button")
                    ) {
                        if (isGeneratingAphorism) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Creo la frase…")
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

            if (generatedAphorism != null) {
                EditorialAphorismPreviewCard(
                    quoteText = generatedAphorism.quoteText,
                    attribution = generatedAphorism.attribution,
                    momentLabel = generatedAphorism.momentLabel,
                    themeTag = generatedAphorism.themeTag,
                    isSaved = generatedAphorism.isSaved,
                    isRegenerating = isGeneratingAphorism,
                    onSaveToRegister = onSaveAphorismToRegister,
                    onRegenerate = { onGenerateAphorism(entries, bedtimeMode) }
                )
            }

            Text(
                text = "Registro delle frasi",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            when (aphorismsState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                }
                is UiState.Error -> {
                    Text(
                        text = aphorismsState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is UiState.Success -> {
                    if (aphorismsState.data.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Le frasi e gli aforismi che scegli di tenere per te appariranno qui, pronti da rileggere o condividere.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        aphorismsState.data.forEach { saved ->
                            SavedAphorismRegisterCard(
                                aphorism = saved,
                                onCopied = { onShowMessage("Frase copiata negli appunti.") },
                                onDeleteClick = { aphorismToDelete = saved }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    val pendingDelete = summaryToDelete
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { summaryToDelete = null },
            title = { Text("Eliminare questa sintesi?") },
            text = { Text("La sintesi settimanale verrà rimossa dal diario.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        summaryToDelete = null
                        onDeleteSummary(pendingDelete.id)
                    }
                ) {
                    Text("Elimina", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { summaryToDelete = null }) {
                    Text("Annulla")
                }
            }
        )
    }

    val pendingAphorismDelete = aphorismToDelete
    if (pendingAphorismDelete != null) {
        AlertDialog(
            onDismissRequest = { aphorismToDelete = null },
            title = { Text("Rimuovere questa frase?") },
            text = { Text("Verrà eliminata dal tuo registro personale.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        aphorismToDelete = null
                        onDeleteAphorism(pendingAphorismDelete.id)
                    }
                ) {
                    Text("Elimina", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { aphorismToDelete = null }) {
                    Text("Annulla")
                }
            }
        )
    }
}

@Composable
private fun MetricPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SentimentTrendCanvas(
    entries: List<DiaryEntry>,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val clarityColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier) {
        val padH = 14.dp.toPx()
        val padV = 12.dp.toPx()
        val plotW = (size.width - padH * 2f).coerceAtLeast(1f)
        val plotH = (size.height - padV * 2f).coerceAtLeast(1f)

        // Subtle horizontal baseline grid for values 1..5
        for (level in 1..5) {
            val y = padV + plotH * (1f - (level - 1) / 4f)
            drawLine(
                color = gridColor.copy(alpha = 0.45f),
                start = Offset(padH, y),
                end = Offset(padH + plotW, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        val count = entries.size
        val stepX = if (count > 1) plotW / (count - 1) else 0f

        val valencePoints = entries.mapIndexed { idx, entry ->
            val x = padH + idx * stepX
            val norm = ((entry.sentimentValence.coerceIn(1L, 5L) - 1L) / 4f)
            val y = padV + plotH * (1f - norm)
            Offset(x, y)
        }

        val clarityPoints = entries.mapIndexed { idx, entry ->
            val x = padH + idx * stepX
            val norm = ((entry.mentalClarity.coerceIn(1L, 5L) - 1L) / 4f)
            val y = padV + plotH * (1f - norm)
            Offset(x, y)
        }

        // Clarity path (subtle secondary)
        if (clarityPoints.size >= 2) {
            val clarityPath = Path().apply {
                moveTo(clarityPoints.first().x, clarityPoints.first().y)
                for (i in 1 until clarityPoints.size) {
                    val prev = clarityPoints[i - 1]
                    val curr = clarityPoints[i]
                    val midX = (prev.x + curr.x) / 2f
                    cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                }
            }
            drawPath(
                path = clarityPath,
                color = clarityColor.copy(alpha = 0.7f),
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        floatArrayOf(12f, 10f)
                    )
                )
            )
        }

        // Valence smooth curve + gradient fill
        if (valencePoints.size >= 2) {
            val linePath = Path().apply {
                moveTo(valencePoints.first().x, valencePoints.first().y)
                for (i in 1 until valencePoints.size) {
                    val prev = valencePoints[i - 1]
                    val curr = valencePoints[i]
                    val midX = (prev.x + curr.x) / 2f
                    cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                }
            }

            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(valencePoints.last().x, padV + plotH)
                lineTo(valencePoints.first().x, padV + plotH)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        lineColor.copy(alpha = 0.28f),
                        lineColor.copy(alpha = 0.0f)
                    ),
                    startY = padV,
                    endY = padV + plotH
                )
            )

            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        valencePoints.forEachIndexed { idx, point ->
            val dotColor = valenceColor(entries[idx].sentimentValence)
            drawCircle(
                color = dotColor,
                radius = 5.dp.toPx(),
                center = point
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeeklySummaryCard(
    summary: WeeklySummary,
    onDeleteClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("weekly_summary_card_${summary.id}")
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.periodLabel.uppercase(Locale.ITALIAN),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = summary.headline,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Elimina sintesi",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = summary.summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (summary.recurringSentiments.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    summary.recurringSentiments.forEach { sentiment ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = sentiment,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            if (summary.recurringPatterns.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "FILI RICORRENTI",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = summary.recurringPatterns,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            if (summary.weeklyQuestion.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "DOMANDA PER LA PROSSIMA SETTIMANA",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = summary.weeklyQuestion,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
    }
}
