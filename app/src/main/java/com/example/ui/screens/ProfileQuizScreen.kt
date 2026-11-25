package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.UserProfile

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileQuizScreen(
    initialProfile: UserProfile?,
    isSaving: Boolean,
    isEditMode: Boolean,
    onSaveProfile: (UserProfile) -> Unit,
    onSkip: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }

    var preferredName by remember(initialProfile) {
        mutableStateOf(initialProfile?.preferredName.orEmpty())
    }
    var ageRange by remember(initialProfile) {
        mutableStateOf(initialProfile?.ageRange.orEmpty())
    }
    val lifestyleTags = remember(initialProfile) {
        mutableStateListOf<String>().apply {
            addAll(initialProfile?.lifestyleTags.orEmpty())
        }
    }
    var lifestyleNotes by remember(initialProfile) {
        mutableStateOf(initialProfile?.lifestyleNotes.orEmpty())
    }
    val keyRelationships = remember(initialProfile) {
        mutableStateListOf<String>().apply {
            addAll(initialProfile?.keyRelationships.orEmpty())
        }
    }
    var relationshipsNote by remember(initialProfile) {
        mutableStateOf(initialProfile?.relationshipsNote.orEmpty())
    }
    val mainConcerns = remember(initialProfile) {
        mutableStateListOf<String>().apply {
            addAll(initialProfile?.mainConcerns.orEmpty())
        }
    }
    var concernsNote by remember(initialProfile) {
        mutableStateOf(initialProfile?.concernsNote.orEmpty())
    }
    var reflectionStyle by remember(initialProfile) {
        mutableStateOf(
            initialProfile?.reflectionStyle?.ifBlank { "Domande dirette e pratiche" }
                ?: "Domande dirette e pratiche"
        )
    }

    BackHandler(enabled = step > 0 || isEditMode) {
        if (step > 0) step -= 1 else if (isEditMode) onSkip()
    }

    val ageOptions = listOf("Under 20", "20–29", "30–39", "40–49", "50–59", "60+")
    val lifestyleOptions = listOf(
        "Lavoro intenso",
        "Studio / università",
        "Orari irregolari",
        "Smart working",
        "Sport / movimento",
        "Poco tempo libero",
        "Fase di cambiamento",
        "Routine tranquilla"
    )
    val relationshipOptions = listOf(
        "Partner",
        "Famiglia d'origine",
        "Figli",
        "Amici stretti",
        "Colleghi / soci",
        "Nuove conoscenze",
        "Spesso in solitaria",
        "Animale domestico"
    )
    val concernOptions = listOf(
        "Equilibrio lavoro-vita",
        "Stanchezza mentale",
        "Scelte future",
        "Relazioni",
        "Stabilità economica",
        "Sonno / riposo",
        "Senso di direzione",
        "Tempo per me"
    )
    val styleOptions = listOf(
        "Domande dirette e pratiche" to "Vanno subito al nocciolo della giornata.",
        "Spunti curiosi e laterali" to "Ti fanno vedere la stessa cosa da un altro angolo.",
        "Domande leggere per staccare" to "Brevi e distensive, per chiudere la sera senza peso."
    )

    fun toggleLimited(list: MutableList<String>, item: String) {
        if (item in list) list.remove(item)
        else if (list.size < 8) list.add(item)
    }

    fun buildProfile(): UserProfile = UserProfile(
        preferredName = preferredName.trim(),
        ageRange = ageRange.trim(),
        lifestyleTags = lifestyleTags.toList(),
        lifestyleNotes = lifestyleNotes.trim(),
        keyRelationships = keyRelationships.toList(),
        relationshipsNote = relationshipsNote.trim(),
        mainConcerns = mainConcerns.toList(),
        concernsNote = concernsNote.trim(),
        reflectionStyle = reflectionStyle
    )

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
                    .widthIn(max = 620.dp)
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (step > 0 || isEditMode) {
                            IconButton(
                                onClick = {
                                    if (step > 0) step -= 1 else onSkip()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Indietro"
                                )
                            }
                        }
                        Text(
                            text = "Profilo personale · ${step + 1}/4",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    TextButton(
                        onClick = onSkip,
                        enabled = !isSaving,
                        modifier = Modifier.testTag("quiz_skip_button")
                    ) {
                        Text(if (isEditMode) "Chiudi" else "Salta per ora")
                    }
                }

                LinearProgressIndicator(
                    progress = { (step + 1) / 4f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (step) {
                        0 -> {
                            Text(
                                text = "Raccontami un po' chi sei",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Tutto è facoltativo: serve solo a far capire a Gemini il tuo contesto, senza mai farti sentire sotto esame.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = preferredName,
                                onValueChange = { preferredName = it.take(60) },
                                label = { Text("Come preferisci essere chiamato/a?") },
                                placeholder = { Text("Es. Ale, Marco, Giulia…") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("quiz_name_input")
                            )

                            Text(
                                text = "Fascia d'età (o età)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ageOptions.forEach { option ->
                                    FilterChip(
                                        selected = ageRange == option,
                                        onClick = { ageRange = if (ageRange == option) "" else option },
                                        label = { Text(option) }
                                    )
                                }
                            }

                            Text(
                                text = "Come descriveresti il tuo ritmo di vita adesso?",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                lifestyleOptions.forEach { tag ->
                                    FilterChip(
                                        selected = tag in lifestyleTags,
                                        onClick = { toggleLimited(lifestyleTags, tag) },
                                        label = { Text(tag) }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = lifestyleNotes,
                                onValueChange = { lifestyleNotes = it.take(1500) },
                                label = { Text("In breve, com'è una tua giornata tipo? (libero)") },
                                placeholder = { Text("Es. Lavoro fino alle 19, poi palestra o divano…") },
                                minLines = 3,
                                maxLines = 5,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        1 -> {
                            Text(
                                text = "I rapporti che contano",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Chi fa parte della tua quotidianità in questo periodo? Così quando nominerai qualcuno, Gemini avrà già un quadro naturale.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                relationshipOptions.forEach { tag ->
                                    FilterChip(
                                        selected = tag in keyRelationships,
                                        onClick = { toggleLimited(keyRelationships, tag) },
                                        label = { Text(tag) }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = relationshipsNote,
                                onValueChange = { relationshipsNote = it.take(1500) },
                                label = { Text("Vuoi aggiungere qualcosa sui tuoi legami?") },
                                placeholder = { Text("Es. Vivo con la mia compagna, vorrei vedere di più gli amici…") },
                                minLines = 4,
                                maxLines = 7,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        2 -> {
                            Text(
                                text = "Cosa ti occupa più spazio",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Preoccupazioni, pensieri ricorrenti o cose che vuoi tenere d'occhio. Servono solo a orientare meglio le domande, zero giudizi.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                concernOptions.forEach { tag ->
                                    FilterChip(
                                        selected = tag in mainConcerns,
                                        onClick = { toggleLimited(mainConcerns, tag) },
                                        label = { Text(tag) }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = concernsNote,
                                onValueChange = { concernsNote = it.take(1500) },
                                label = { Text("Se vuoi, raccontalo a parole tue") },
                                placeholder = { Text("Es. Faccio fatica a staccare col lavoro la sera…") },
                                minLines = 4,
                                maxLines = 7,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        3 -> {
                            Text(
                                text = "Che domande preferisci la sera?",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Quando chiedi una riflessione a Gemini dopo la trascrizione vocale, che taglio ti fa stare più a tuo agio?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            styleOptions.forEach { (title, subtitle) ->
                                val selected = reflectionStyle == title
                                Card(
                                    onClick = { reflectionStyle = title },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        }
                                    ),
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = title,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (selected) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = "Selezionato",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Navigation Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (step > 0) {
                        OutlinedButton(
                            onClick = { step -= 1 },
                            enabled = !isSaving,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            Text("Indietro")
                        }
                    }

                    Button(
                        onClick = {
                            if (step < 3) {
                                step += 1
                            } else {
                                onSaveProfile(buildProfile())
                            }
                        },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(52.dp)
                            .testTag("quiz_next_or_save_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Salvataggio…")
                        } else if (step < 3) {
                            Text("Continua")
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Salva e inizia")
                        }
                    }
                }
            }
        }
    }
}
