package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.core.LockMode
import com.example.data.CloudSyncStatus
import com.example.data.SyncPhase
import com.example.data.UserProfile
import com.example.ui.components.PatternLockGrid
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LockSetupDialogType {
    NONE,
    SETUP_PIN,
    SETUP_PATTERN,
    REMOVE_LOCK
}

private data class SyncVisualState(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val tint: androidx.compose.ui.graphics.Color,
    val background: androidx.compose.ui.graphics.Color
)

@Composable
fun SecuritySettingsScreen(
    userEmail: String?,
    userProfile: UserProfile?,
    cloudSyncStatus: CloudSyncStatus,
    lockMode: LockMode,
    autoLockOnBackground: Boolean,
    isDarkTheme: Boolean,
    onSyncNow: () -> Unit,
    onOpenProfileQuiz: () -> Unit,
    onSetPin: (String) -> Unit,
    onSetPattern: (List<Int>) -> Unit,
    onVerifyCurrentPin: (String) -> Boolean,
    onVerifyCurrentPattern: (List<Int>) -> Boolean,
    onDisableLock: () -> Unit,
    onToggleAutoLock: (Boolean) -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit,
    onLockNow: () -> Unit,
    onSignOut: () -> Unit
) {
    var activeDialog by remember { mutableStateOf(LockSetupDialogType.NONE) }
    var showSignOutConfirm by remember { mutableStateOf(false) }

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
                    text = "Sicurezza & Profilo",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Proteggi il diario con PIN o sequenza e aggiorna il contesto per le domande.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Personal Profile Card for Gemini Orientation
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PersonOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(12.dp).size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (userProfile?.onboardingCompleted == true) {
                                    "Profilo personale attivo"
                                } else {
                                    "Profilo personale facoltativo"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (userProfile?.onboardingCompleted == true) {
                                    val namePart = userProfile.preferredName.takeIf { it.isNotBlank() }?.let { "$it · " }.orEmpty()
                                    "$namePart${userProfile.reflectionStyle}"
                                } else {
                                    "Racconta ritmo di vita, legami e preoccupazioni per domande su misura."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenProfileQuiz,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_profile_quiz_button")
                    ) {
                        Text(
                            if (userProfile?.onboardingCompleted == true) {
                                "Modifica profilo e stile domande"
                            } else {
                                "Compila il quiz di profilo"
                            }
                        )
                    }
                }
            }

            // Lock Card
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = if (lockMode == LockMode.NONE) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = if (lockMode == LockMode.NONE) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = if (lockMode == LockMode.NONE) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.padding(12.dp).size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (lockMode) {
                                    LockMode.NONE -> "Blocco locale disattivo"
                                    LockMode.PIN -> "Blocco con PIN attivo"
                                    LockMode.PATTERN -> "Blocco con sequenza attivo"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = when (lockMode) {
                                    LockMode.NONE -> "Scegli un PIN o una sequenza 3×3 per chiudere il diario."
                                    LockMode.PIN -> "Richiesto all'apertura dell'app."
                                    LockMode.PATTERN -> "Sequenza di almeno 4 punti richiesta all'apertura."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { activeDialog = LockSetupDialogType.SETUP_PIN },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("setup_pin_button")
                        ) {
                            Icon(Icons.Rounded.Pin, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (lockMode == LockMode.PIN) "Cambia PIN" else "Imposta PIN")
                        }

                        OutlinedButton(
                            onClick = { activeDialog = LockSetupDialogType.SETUP_PATTERN },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("setup_pattern_button")
                        ) {
                            Icon(Icons.Rounded.Gesture, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (lockMode == LockMode.PATTERN) "Cambia sequenza" else "Sequenza")
                        }
                    }

                    if (lockMode != LockMode.NONE) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Blocca quando esci dall'app",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Richiede PIN o sequenza al rientro in Vespro",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoLockOnBackground,
                                onCheckedChange = onToggleAutoLock,
                                modifier = Modifier.testTag("auto_lock_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onLockNow,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("lock_now_button")
                            ) {
                                Text("Blocca adesso")
                            }
                            TextButton(
                                onClick = { activeDialog = LockSetupDialogType.REMOVE_LOCK },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("remove_lock_button")
                            ) {
                                Text("Rimuovi blocco", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // Appearance Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DarkMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Tema Crepuscolo scuro",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Più riposante la sera; disattiva per carta chiara",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = onToggleDarkTheme,
                        modifier = Modifier.testTag("dark_theme_switch")
                    )
                }
            }

            // Cloud Sync & Multi-Device Account Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val (syncIcon, syncLabel, syncTint, syncBg) = when (cloudSyncStatus.phase) {
                    SyncPhase.SYNCED -> SyncVisualState(
                        icon = Icons.Rounded.CloudDone,
                        label = "Sincronizzato in cloud",
                        tint = MaterialTheme.colorScheme.tertiary,
                        background = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    )
                    SyncPhase.SYNCING -> SyncVisualState(
                        icon = Icons.Rounded.CloudSync,
                        label = "Sincronizzazione in corso…",
                        tint = MaterialTheme.colorScheme.primary,
                        background = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    )
                    SyncPhase.PENDING_UPLOAD -> SyncVisualState(
                        icon = Icons.Rounded.CloudUpload,
                        label = "In attesa di rete per il salvataggio cloud",
                        tint = MaterialTheme.colorScheme.primary,
                        background = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    )
                    SyncPhase.OFFLINE_CACHE -> SyncVisualState(
                        icon = Icons.Rounded.CloudOff,
                        label = "Offline · sincronizzazione automatica appena torni online",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        background = MaterialTheme.colorScheme.surfaceVariant
                    )
                    SyncPhase.ERROR -> SyncVisualState(
                        icon = Icons.Rounded.CloudOff,
                        label = cloudSyncStatus.errorMessage ?: "Errore di sincronizzazione",
                        tint = MaterialTheme.colorScheme.error,
                        background = MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                    )
                }
                val formattedLastSync = remember(cloudSyncStatus.lastSyncedMillis) {
                    cloudSyncStatus.lastSyncedMillis?.let { millis ->
                        SimpleDateFormat("HH:mm", Locale.ITALIAN).format(Date(millis))
                    }
                }

                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "SINCRONIZZAZIONE CLOUD MULTI-DISPOSITIVO",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = syncBg,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = syncIcon,
                                contentDescription = null,
                                tint = syncTint,
                                modifier = Modifier.padding(12.dp).size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userEmail ?: "Account Google connesso",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (formattedLastSync != null) {
                                    "$syncLabel · ultimo controllo alle $formattedLastSync"
                                } else {
                                    syncLabel
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = syncTint
                            )
                        }
                    }

                    Text(
                        text = "Ogni pagina, riflessione, frase salvata e sintesi viene sincronizzata automaticamente in tempo reale sul tuo spazio privato Firebase Firestore. Se scrivi offline, le modifiche restano al sicuro sul dispositivo e salgono da sole appena torna la connessione: accedendo con lo stesso account Google su un altro telefono o tablet ritrovi tutto subito.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FilledTonalButton(
                        onClick = onSyncNow,
                        enabled = cloudSyncStatus.phase != SyncPhase.SYNCING,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("sync_now_button")
                    ) {
                        if (cloudSyncStatus.phase == SyncPhase.SYNCING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verifica cloud in corso…")
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.CloudSync,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sincronizza ora")
                        }
                    }

                    OutlinedButton(
                        onClick = { showSignOutConfirm = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Esci dall'account")
                    }
                }
            }
        }
    }

    when (activeDialog) {
        LockSetupDialogType.SETUP_PIN -> {
            PinSetupDialog(
                onDismiss = { activeDialog = LockSetupDialogType.NONE },
                onConfirmed = { pin ->
                    onSetPin(pin)
                    activeDialog = LockSetupDialogType.NONE
                }
            )
        }
        LockSetupDialogType.SETUP_PATTERN -> {
            PatternSetupDialog(
                onDismiss = { activeDialog = LockSetupDialogType.NONE },
                onConfirmed = { pattern ->
                    onSetPattern(pattern)
                    activeDialog = LockSetupDialogType.NONE
                }
            )
        }
        LockSetupDialogType.REMOVE_LOCK -> {
            VerifyToRemoveLockDialog(
                lockMode = lockMode,
                onVerifyPin = onVerifyCurrentPin,
                onVerifyPattern = onVerifyCurrentPattern,
                onVerified = {
                    onDisableLock()
                    activeDialog = LockSetupDialogType.NONE
                },
                onDismiss = { activeDialog = LockSetupDialogType.NONE }
            )
        }
        LockSetupDialogType.NONE -> {}
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Uscire da Vespro?") },
            text = { Text("Potrai rientrare in qualsiasi momento con il tuo account Google.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutConfirm = false
                        onSignOut()
                    }
                ) {
                    Text("Esci")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text("Annulla")
                }
            }
        )
    }
}

@Composable
private fun PinSetupDialog(
    onDismiss: () -> Unit,
    onConfirmed: (String) -> Unit
) {
    var firstPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(1) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (step == 1) "Scegli un PIN (4-6 cifre)" else "Conferma il PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = if (step == 1) firstPin else confirmPin,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }.take(6)
                        errorMsg = null
                        if (step == 1) firstPin = digits else confirmPin = digits
                    },
                    label = { Text(if (step == 1) "Nuovo PIN" else "Ripeti il PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pin_setup_input")
                )
                if (errorMsg != null) {
                    Text(
                        text = errorMsg.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (step == 1) {
                        if (firstPin.length < 4) {
                            errorMsg = "Inserisci almeno 4 cifre."
                        } else {
                            step = 2
                        }
                    } else {
                        if (confirmPin == firstPin) {
                            onConfirmed(firstPin)
                        } else {
                            errorMsg = "I due PIN non coincidono."
                            confirmPin = ""
                        }
                    }
                }
            ) {
                Text(if (step == 1) "Avanti" else "Salva PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}

@Composable
private fun PatternSetupDialog(
    onDismiss: () -> Unit,
    onConfirmed: (List<Int>) -> Unit
) {
    var firstPattern by remember { mutableStateOf<List<Int>?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (firstPattern == null) "Traccia la nuova sequenza" else "Ripeti la sequenza per confermare"
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = errorMsg ?: "Collega almeno 4 punti sulla griglia.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (errorMsg != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                PatternLockGrid(
                    isError = errorMsg != null,
                    onPatternComplete = { nodes ->
                        val initial = firstPattern
                        if (initial == null) {
                            if (nodes.size < 4) {
                                errorMsg = "Servono almeno 4 punti collegati."
                            } else {
                                firstPattern = nodes
                                errorMsg = null
                            }
                        } else {
                            if (nodes == initial) {
                                onConfirmed(nodes)
                            } else {
                                errorMsg = "Sequenza diversa dalla precedente. Riprova."
                            }
                        }
                    }
                )
            }
        },
        confirmButton = {
            if (firstPattern != null) {
                TextButton(onClick = {
                    firstPattern = null
                    errorMsg = null
                }) {
                    Text("Ricomincia")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}

@Composable
private fun VerifyToRemoveLockDialog(
    lockMode: LockMode,
    onVerifyPin: (String) -> Boolean,
    onVerifyPattern: (List<Int>) -> Boolean,
    onVerified: () -> Unit,
    onDismiss: () -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Conferma per rimuovere il blocco") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (lockMode == LockMode.PIN) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            pinInput = it.filter { ch -> ch.isDigit() }.take(6)
                            errorMsg = null
                        },
                        label = { Text("PIN attuale") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    PatternLockGrid(
                        isError = errorMsg != null,
                        onPatternComplete = { nodes ->
                            if (onVerifyPattern(nodes)) {
                                onVerified()
                            } else {
                                errorMsg = "Sequenza errata."
                            }
                        }
                    )
                }
                if (errorMsg != null) {
                    Text(
                        text = errorMsg.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            if (lockMode == LockMode.PIN) {
                TextButton(
                    onClick = {
                        if (onVerifyPin(pinInput)) {
                            onVerified()
                        } else {
                            errorMsg = "PIN errato."
                        }
                    }
                ) {
                    Text("Rimuovi", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}
