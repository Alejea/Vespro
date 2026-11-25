package com.example.ui.state

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CloudSyncStatus
import com.example.data.DiaryEntry
import com.example.data.DiaryRepository
import com.example.data.GeminiDiaryAssistant
import com.example.data.SavedAphorism
import com.example.data.SyncPhase
import com.example.data.SyncedEntriesSnapshot
import com.example.data.UserProfile
import com.example.data.WeeklySummary
import com.example.export.DiaryPdfExporter
import com.example.export.GeneratedPdfArtifact
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface AiTaskState {
    data object Idle : AiTaskState
    data object AnalyzingEntry : AiTaskState
    data object SavingRawEntry : AiTaskState
    data object RefreshingQuestion : AiTaskState
    data object GeneratingWeekly : AiTaskState
    data object GeneratingAphorism : AiTaskState
    data class Error(val message: String) : AiTaskState
}

sealed interface PdfExportState {
    data object Idle : PdfExportState
    data object Generating : PdfExportState
    data class Ready(val artifact: GeneratedPdfArtifact) : PdfExportState
    data class Error(val message: String) : PdfExportState
}

data class AphorismPreview(
    val entryId: String,
    val quoteText: String,
    val attribution: String,
    val momentLabel: String,
    val themeTag: String,
    val isSaved: Boolean = false
)

data class RecurringStats(
    val totalEntries: Int = 0,
    val averageValence: Float = 0f,
    val averageClarity: Float = 0f,
    val topThemes: List<Pair<String, Int>> = emptyList(),
    val topSentiments: List<Pair<String, Int>> = emptyList(),
    val recentTrend: List<DiaryEntry> = emptyList()
)

class DiaryViewModel(
    private val repository: DiaryRepository,
    private val geminiAssistant: GeminiDiaryAssistant,
    private val currentUserId: String
) : ViewModel() {

    private val syncedEntriesFlow: StateFlow<UiState<SyncedEntriesSnapshot>> =
        repository.observeEntriesWithSync(currentUserId)
            .map<SyncedEntriesSnapshot, UiState<SyncedEntriesSnapshot>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing diary entries", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Impossibile caricare le pagine del diario."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val entriesState: StateFlow<UiState<List<DiaryEntry>>> = syncedEntriesFlow
        .map { state ->
            when (state) {
                is UiState.Loading -> UiState.Loading
                is UiState.Success -> UiState.Success(state.data.entries)
                is UiState.Error -> UiState.Error(state.message)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val _isManualSyncing = MutableStateFlow(false)
    private val _lastConfirmedSyncMillis = MutableStateFlow<Long?>(null)

    val cloudSyncStatus: StateFlow<CloudSyncStatus> = combine(
        syncedEntriesFlow,
        _isManualSyncing,
        _lastConfirmedSyncMillis
    ) { entriesResult, isManualSyncing, manualSyncMs ->
        when {
            isManualSyncing -> CloudSyncStatus(
                phase = SyncPhase.SYNCING,
                lastSyncedMillis = manualSyncMs
            )
            entriesResult is UiState.Loading -> CloudSyncStatus(
                phase = SyncPhase.SYNCING,
                lastSyncedMillis = manualSyncMs
            )
            entriesResult is UiState.Error -> CloudSyncStatus(
                phase = SyncPhase.ERROR,
                lastSyncedMillis = manualSyncMs,
                errorMessage = entriesResult.message
            )
            entriesResult is UiState.Success -> {
                val snap = entriesResult.data
                when {
                    snap.hasPendingWrites -> CloudSyncStatus(
                        phase = SyncPhase.PENDING_UPLOAD,
                        lastSyncedMillis = manualSyncMs
                    )
                    snap.isFromCache -> CloudSyncStatus(
                        phase = SyncPhase.OFFLINE_CACHE,
                        lastSyncedMillis = manualSyncMs
                    )
                    else -> CloudSyncStatus(
                        phase = SyncPhase.SYNCED,
                        lastSyncedMillis = maxOf(manualSyncMs ?: 0L, snap.snapshotTimeMillis)
                    )
                }
            }
            else -> CloudSyncStatus()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = CloudSyncStatus(phase = SyncPhase.SYNCING)
    )

    val summariesState: StateFlow<UiState<List<WeeklySummary>>> =
        repository.observeWeeklySummaries(currentUserId)
            .map<List<WeeklySummary>, UiState<List<WeeklySummary>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing weekly summaries", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Impossibile caricare le sintesi settimanali."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val profileState: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId)
            .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing user profile", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Impossibile caricare il profilo."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val aphorismsState: StateFlow<UiState<List<SavedAphorism>>> =
        repository.observeAphorisms(currentUserId)
            .map<List<SavedAphorism>, UiState<List<SavedAphorism>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing aphorisms", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Impossibile caricare il registro delle frasi."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private val _aiTaskState = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val aiTaskState: StateFlow<AiTaskState> = _aiTaskState.asStateFlow()

    private val _generatedAphorism = MutableStateFlow<AphorismPreview?>(null)
    val generatedAphorism: StateFlow<AphorismPreview?> = _generatedAphorism.asStateFlow()

    private val _pdfExportState = MutableStateFlow<PdfExportState>(PdfExportState.Idle)
    val pdfExportState: StateFlow<PdfExportState> = _pdfExportState.asStateFlow()

    private val _draftTranscript = MutableStateFlow("")
    val draftTranscript: StateFlow<String> = _draftTranscript.asStateFlow()

    private val _draftUsedVoice = MutableStateFlow(false)
    val draftUsedVoice: StateFlow<Boolean> = _draftUsedVoice.asStateFlow()

    private val _selectedEntryId = MutableStateFlow<String?>(null)
    val selectedEntryId: StateFlow<String?> = _selectedEntryId.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _isProfileQuizSaving = MutableStateFlow(false)
    val isProfileQuizSaving: StateFlow<Boolean> = _isProfileQuizSaving.asStateFlow()

    fun updateDraftTranscript(text: String) {
        _draftTranscript.value = text
    }

    fun appendVoiceSegment(segment: String) {
        val clean = segment.trim()
        if (clean.isEmpty()) return
        _draftUsedVoice.value = true
        val current = _draftTranscript.value.trimEnd()
        _draftTranscript.value = if (current.isEmpty()) {
            clean
        } else {
            val separator = if (current.endsWith(".") || current.endsWith("!") || current.endsWith("?")) "\n" else " "
            "$current$separator$clean"
        }
    }

    fun clearDraft() {
        _draftTranscript.value = ""
        _draftUsedVoice.value = false
        _aiTaskState.value = AiTaskState.Idle
    }

    fun selectEntry(entryId: String?) {
        _selectedEntryId.value = entryId
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun dismissAiError() {
        if (_aiTaskState.value is AiTaskState.Error) {
            _aiTaskState.value = AiTaskState.Idle
        }
    }

    private fun currentUserProfile(): UserProfile? =
        (profileState.value as? UiState.Success)?.data

    fun saveUserProfile(profile: UserProfile, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _isProfileQuizSaving.value = true
            repository.saveUserProfile(
                currentUserId,
                profile.copy(
                    userId = currentUserId,
                    onboardingCompleted = true,
                    onboardingSkipped = false
                )
            ).onSuccess {
                _isProfileQuizSaving.value = false
                _snackbarMessage.value = "Profilo salvato. Gemini lo userà con discrezione per orientare le domande."
                onDone()
            }.onFailure { err ->
                _isProfileQuizSaving.value = false
                _snackbarMessage.value = err.localizedMessage ?: "Impossibile salvare il profilo."
            }
        }
    }

    fun skipProfileQuiz(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _isProfileQuizSaving.value = true
            val existing = currentUserProfile()
            val skippedProfile = (existing ?: UserProfile(userId = currentUserId)).copy(
                userId = currentUserId,
                onboardingSkipped = true
            )
            repository.saveUserProfile(currentUserId, skippedProfile)
                .onSuccess {
                    _isProfileQuizSaving.value = false
                    onDone()
                }
                .onFailure {
                    _isProfileQuizSaving.value = false
                    onDone()
                }
        }
    }

    fun saveAndReflectWithGemini() {
        val raw = _draftTranscript.value.trim()
        if (raw.isEmpty()) {
            _snackbarMessage.value = "Scrivi o detta qualche pensiero prima di salvare."
            return
        }
        val usedVoice = _draftUsedVoice.value
        val existingEntries = (entriesState.value as? UiState.Success)?.data.orEmpty()
        val recentThemes = existingEntries
            .take(7)
            .flatMap { it.themes }
            .distinct()
            .take(8)
        val profile = currentUserProfile()

        viewModelScope.launch {
            _aiTaskState.value = AiTaskState.AnalyzingEntry
            try {
                val analysis = geminiAssistant.analyzeStreamOfConsciousness(
                    transcript = raw,
                    recentThemes = recentThemes,
                    userProfile = profile
                )
                val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val entry = DiaryEntry(
                    userId = currentUserId,
                    dateKey = todayKey,
                    title = analysis.title,
                    rawTranscript = raw,
                    reorganizedText = analysis.reorganizedText,
                    mainTheme = analysis.mainTheme,
                    themes = analysis.themes,
                    sentimentLabel = analysis.sentimentLabel,
                    sentimentValence = analysis.sentimentValence.toLong(),
                    mentalClarity = analysis.mentalClarity.toLong(),
                    reflectionQuestion = analysis.reflectionQuestion,
                    reflectionAnswer = "",
                    takeaway = analysis.takeaway,
                    usedVoice = usedVoice
                )
                val result = repository.createEntry(currentUserId, entry)
                result.onSuccess { createdId ->
                    _draftTranscript.value = ""
                    _draftUsedVoice.value = false
                    _aiTaskState.value = AiTaskState.Idle
                    _selectedEntryId.value = createdId
                }.onFailure { err ->
                    _aiTaskState.value = AiTaskState.Error(
                        err.localizedMessage ?: "Errore durante il salvataggio della pagina."
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini entry analysis timed out", e)
                _aiTaskState.value = AiTaskState.Error(
                    "Gemini ha impiegato più di 60 secondi. Riprova o salva il testo grezzo."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini entry analysis failed", e)
                _aiTaskState.value = AiTaskState.Error(
                    e.localizedMessage ?: "Analisi Gemini non riuscita. Riprova tra un momento."
                )
            }
        }
    }

    fun saveRawWithoutAi() {
        val raw = _draftTranscript.value.trim()
        if (raw.isEmpty()) {
            _snackbarMessage.value = "Scrivi o detta qualcosa prima di salvare."
            return
        }
        val usedVoice = _draftUsedVoice.value
        viewModelScope.launch {
            _aiTaskState.value = AiTaskState.SavingRawEntry
            val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val fallbackTitle = "Appunti del ${
                SimpleDateFormat("d MMM", Locale.ITALIAN).format(Date())
            }"
            val entry = DiaryEntry(
                userId = currentUserId,
                dateKey = todayKey,
                title = fallbackTitle,
                rawTranscript = raw,
                reorganizedText = "",
                mainTheme = "Nota libera",
                themes = emptyList(),
                sentimentLabel = "Da esplorare",
                sentimentValence = 3L,
                mentalClarity = 3L,
                reflectionQuestion = "",
                reflectionAnswer = "",
                takeaway = "",
                usedVoice = usedVoice
            )
            repository.createEntry(currentUserId, entry)
                .onSuccess { createdId ->
                    _draftTranscript.value = ""
                    _draftUsedVoice.value = false
                    _aiTaskState.value = AiTaskState.Idle
                    _selectedEntryId.value = createdId
                    _snackbarMessage.value = "Pagina salvata. Puoi elaborarla con Gemini quando vuoi."
                }
                .onFailure { err ->
                    _aiTaskState.value = AiTaskState.Error(
                        err.localizedMessage ?: "Salvataggio non riuscito."
                    )
                }
        }
    }

    fun analyzeExistingEntry(entry: DiaryEntry) {
        if (entry.id.isBlank()) return
        val existingEntries = (entriesState.value as? UiState.Success)?.data.orEmpty()
        val recentThemes = existingEntries
            .filter { it.id != entry.id }
            .take(7)
            .flatMap { it.themes }
            .distinct()
            .take(8)
        val profile = currentUserProfile()

        viewModelScope.launch {
            _aiTaskState.value = AiTaskState.AnalyzingEntry
            try {
                val analysis = geminiAssistant.analyzeStreamOfConsciousness(
                    transcript = entry.rawTranscript,
                    recentThemes = recentThemes,
                    userProfile = profile
                )
                repository.updateEntryAnalysis(currentUserId, entry.id, analysis)
                    .onSuccess {
                        _aiTaskState.value = AiTaskState.Idle
                        _snackbarMessage.value = "Pagina riorganizzata e domanda pronta."
                    }
                    .onFailure { err ->
                        _aiTaskState.value = AiTaskState.Error(
                            err.localizedMessage ?: "Aggiornamento non riuscito."
                        )
                    }
            } catch (e: TimeoutCancellationException) {
                _aiTaskState.value = AiTaskState.Error("Timeout dopo 60 secondi. Riprova.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Analyze existing entry failed", e)
                _aiTaskState.value = AiTaskState.Error(
                    e.localizedMessage ?: "Analisi Gemini non riuscita."
                )
            }
        }
    }

    fun refreshReflectionQuestion(entry: DiaryEntry) {
        if (entry.id.isBlank()) return
        val profile = currentUserProfile()
        viewModelScope.launch {
            _aiTaskState.value = AiTaskState.RefreshingQuestion
            try {
                val newQuestion = geminiAssistant.generateAlternativeQuestion(
                    transcript = entry.reorganizedText.ifBlank { entry.rawTranscript },
                    mainTheme = entry.mainTheme.ifBlank { "Giornata" },
                    previousQuestion = entry.reflectionQuestion,
                    userProfile = profile
                )
                repository.updateReflectionQuestion(currentUserId, entry.id, newQuestion)
                    .onSuccess {
                        _aiTaskState.value = AiTaskState.Idle
                    }
                    .onFailure { err ->
                        _aiTaskState.value = AiTaskState.Error(
                            err.localizedMessage ?: "Impossibile salvare la nuova domanda."
                        )
                    }
            } catch (e: TimeoutCancellationException) {
                _aiTaskState.value = AiTaskState.Error("Timeout dopo 60 secondi. Riprova.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Refresh reflection question failed", e)
                _aiTaskState.value = AiTaskState.Error(
                    e.localizedMessage ?: "Impossibile generare una nuova domanda."
                )
            }
        }
    }

    fun saveReflectionAnswer(entryId: String, answer: String) {
        if (entryId.isBlank()) return
        viewModelScope.launch {
            repository.updateReflectionAnswer(currentUserId, entryId, answer)
                .onSuccess {
                    _snackbarMessage.value = "Riflessione salvata."
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Errore nel salvataggio."
                }
        }
    }

    fun deleteEntry(entryId: String) {
        if (entryId.isBlank()) return
        viewModelScope.launch {
            repository.deleteEntry(currentUserId, entryId)
                .onSuccess {
                    if (_selectedEntryId.value == entryId) {
                        _selectedEntryId.value = null
                    }
                    _snackbarMessage.value = "Pagina eliminata."
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Impossibile eliminare la pagina."
                }
        }
    }

    fun generateWeeklySummary(entries: List<DiaryEntry>) {
        if (entries.isEmpty()) {
            _snackbarMessage.value = "Servono almeno 1-2 pagine di diario per tirare le somme."
            return
        }
        val recentEntries = entries.take(14)
        val oldestKey = recentEntries.lastOrNull()?.dateKey.orEmpty()
        val newestKey = recentEntries.firstOrNull()?.dateKey.orEmpty()
        val periodLabel = if (oldestKey == newestKey || oldestKey.isBlank()) {
            newestKey.ifBlank { "Ultima settimana" }
        } else {
            "$oldestKey → $newestKey"
        }
        val avgValence = recentEntries.map { it.sentimentValence.toDouble() }.average()
        val profile = currentUserProfile()

        viewModelScope.launch {
            _aiTaskState.value = AiTaskState.GeneratingWeekly
            try {
                val synthesis = geminiAssistant.generateWeeklySynthesis(
                    entries = recentEntries,
                    periodLabel = periodLabel,
                    userProfile = profile
                )
                val weeklySummary = WeeklySummary(
                    userId = currentUserId,
                    periodLabel = periodLabel,
                    headline = synthesis.headline,
                    summary = synthesis.summary,
                    recurringSentiments = synthesis.recurringSentiments,
                    recurringThemes = synthesis.recurringThemes,
                    recurringPatterns = synthesis.recurringPatterns,
                    weeklyQuestion = synthesis.weeklyQuestion,
                    entryCount = recentEntries.size.toLong(),
                    averageValence = avgValence
                )
                repository.saveWeeklySummary(currentUserId, weeklySummary)
                    .onSuccess {
                        _aiTaskState.value = AiTaskState.Idle
                        _snackbarMessage.value = "Sintesi settimanale generata."
                    }
                    .onFailure { err ->
                        _aiTaskState.value = AiTaskState.Error(
                            err.localizedMessage ?: "Errore nel salvataggio della sintesi."
                        )
                    }
            } catch (e: TimeoutCancellationException) {
                _aiTaskState.value = AiTaskState.Error("Timeout dopo 60 secondi durante la sintesi.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Weekly synthesis failed", e)
                _aiTaskState.value = AiTaskState.Error(
                    e.localizedMessage ?: "Impossibile generare la sintesi settimanale."
                )
            }
        }
    }

    fun deleteWeeklySummary(summaryId: String) {
        if (summaryId.isBlank()) return
        viewModelScope.launch {
            repository.deleteWeeklySummary(currentUserId, summaryId)
                .onSuccess { _snackbarMessage.value = "Sintesi eliminata." }
                .onFailure { err -> _snackbarMessage.value = err.localizedMessage ?: "Errore eliminazione." }
        }
    }

    fun isCurrentlyBedtimeOrEvening(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 20 || hour < 5
    }

    fun generateAphorismForEntry(entry: DiaryEntry, isBedtimeOverride: Boolean? = null) {
        val source = entry.reorganizedText.ifBlank { entry.rawTranscript }
        val isNight = isBedtimeOverride ?: isCurrentlyBedtimeOrEvening()
        val profile = currentUserProfile()
        viewModelScope.launch {
            _aiTaskState.value = AiTaskState.GeneratingAphorism
            try {
                val result = geminiAssistant.generateSuggestiveAphorism(
                    sourceText = source,
                    mainTheme = entry.mainTheme,
                    sentimentLabel = entry.sentimentLabel,
                    isNightTime = isNight,
                    userProfile = profile
                )
                _generatedAphorism.value = AphorismPreview(
                    entryId = entry.id,
                    quoteText = result.quoteText,
                    attribution = result.attribution,
                    momentLabel = result.momentLabel,
                    themeTag = result.themeTag,
                    isSaved = false
                )
                _aiTaskState.value = AiTaskState.Idle
            } catch (e: TimeoutCancellationException) {
                _aiTaskState.value = AiTaskState.Error("Timeout dopo 60 secondi. Riprova.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Entry aphorism generation failed", e)
                _aiTaskState.value = AiTaskState.Error(
                    e.localizedMessage ?: "Impossibile generare la frase adesso."
                )
            }
        }
    }

    fun generateStandaloneAphorism(entries: List<DiaryEntry>, isBedtimeMode: Boolean) {
        val recentText = entries.take(3).joinToString("\n\n") { entry ->
            val body = entry.reorganizedText.ifBlank { entry.rawTranscript }
            "[${entry.dateKey} - ${entry.mainTheme}] ${body.take(600)}"
        }
        val latest = entries.firstOrNull()
        val profile = currentUserProfile()
        viewModelScope.launch {
            _aiTaskState.value = AiTaskState.GeneratingAphorism
            try {
                val result = geminiAssistant.generateSuggestiveAphorism(
                    sourceText = recentText,
                    mainTheme = latest?.mainTheme.orEmpty(),
                    sentimentLabel = latest?.sentimentLabel.orEmpty(),
                    isNightTime = isBedtimeMode,
                    userProfile = profile
                )
                _generatedAphorism.value = AphorismPreview(
                    entryId = latest?.id.orEmpty(),
                    quoteText = result.quoteText,
                    attribution = result.attribution,
                    momentLabel = result.momentLabel,
                    themeTag = result.themeTag,
                    isSaved = false
                )
                _aiTaskState.value = AiTaskState.Idle
            } catch (e: TimeoutCancellationException) {
                _aiTaskState.value = AiTaskState.Error("Timeout dopo 60 secondi. Riprova.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Standalone aphorism generation failed", e)
                _aiTaskState.value = AiTaskState.Error(
                    e.localizedMessage ?: "Impossibile generare la frase adesso."
                )
            }
        }
    }

    fun saveGeneratedAphorismToRegister() {
        val preview = _generatedAphorism.value ?: return
        if (preview.isSaved) return
        viewModelScope.launch {
            val item = SavedAphorism(
                userId = currentUserId,
                entryId = preview.entryId,
                quoteText = preview.quoteText,
                attribution = preview.attribution,
                momentLabel = preview.momentLabel,
                themeTag = preview.themeTag
            )
            repository.saveAphorism(currentUserId, item)
                .onSuccess {
                    _generatedAphorism.value = preview.copy(isSaved = true)
                    _snackbarMessage.value = "Frase salvata nel tuo registro."
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Impossibile salvare la frase."
                }
        }
    }

    fun clearGeneratedAphorism() {
        _generatedAphorism.value = null
    }

    fun deleteAphorism(aphorismId: String) {
        if (aphorismId.isBlank()) return
        viewModelScope.launch {
            repository.deleteAphorism(currentUserId, aphorismId)
                .onSuccess { _snackbarMessage.value = "Frase rimossa dal registro." }
                .onFailure { err -> _snackbarMessage.value = err.localizedMessage ?: "Errore eliminazione." }
        }
    }

    fun exportSingleEntryToPdf(context: Context, entry: DiaryEntry) {
        val appContext = context.applicationContext
        val slug = "${entry.dateKey}_${entry.title.ifBlank { "pagina" }}"
        viewModelScope.launch {
            _pdfExportState.value = PdfExportState.Generating
            try {
                val artifact = withContext(Dispatchers.IO) {
                    DiaryPdfExporter.exportEntries(
                        context = appContext,
                        entries = listOf(entry),
                        collectionTitle = null,
                        fileNameSlug = slug
                    )
                }
                _pdfExportState.value = PdfExportState.Ready(artifact)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Single entry PDF export failed", e)
                _pdfExportState.value = PdfExportState.Error(
                    e.localizedMessage ?: "Impossibile generare il PDF per questa pagina."
                )
            }
        }
    }

    fun exportCollectionToPdf(
        context: Context,
        entries: List<DiaryEntry>,
        collectionTitle: String,
        fileNameSlug: String
    ) {
        if (entries.isEmpty()) {
            _snackbarMessage.value = "Nessuna pagina selezionata da esportare."
            return
        }
        val appContext = context.applicationContext
        viewModelScope.launch {
            _pdfExportState.value = PdfExportState.Generating
            try {
                val artifact = withContext(Dispatchers.IO) {
                    DiaryPdfExporter.exportEntries(
                        context = appContext,
                        entries = entries,
                        collectionTitle = collectionTitle,
                        fileNameSlug = fileNameSlug
                    )
                }
                _pdfExportState.value = PdfExportState.Ready(artifact)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Collection PDF export failed", e)
                _pdfExportState.value = PdfExportState.Error(
                    e.localizedMessage ?: "Impossibile creare il PDF della raccolta."
                )
            }
        }
    }

    fun clearPdfExportState() {
        _pdfExportState.value = PdfExportState.Idle
    }

    fun showMessage(message: String) {
        _snackbarMessage.value = message
    }

    fun syncNow() {
        if (_isManualSyncing.value) return
        viewModelScope.launch {
            _isManualSyncing.value = true
            repository.forceCloudSync(currentUserId)
                .onSuccess {
                    _lastConfirmedSyncMillis.value = System.currentTimeMillis()
                    _isManualSyncing.value = false
                    _snackbarMessage.value = "Diario sincronizzato in cloud."
                }
                .onFailure {
                    _isManualSyncing.value = false
                    _snackbarMessage.value = "Rete assente: le tue pagine locali verranno sincronizzate automaticamente appena torni online."
                }
        }
    }

    companion object {
        private const val TAG = "DiaryViewModel"
        private const val STOP_TIMEOUT_MILLIS = 5000L

        fun computeRecurringStats(entries: List<DiaryEntry>): RecurringStats {
            if (entries.isEmpty()) return RecurringStats()
            val avgValence = entries.map { it.sentimentValence.toFloat() }.average().toFloat()
            val avgClarity = entries.map { it.mentalClarity.toFloat() }.average().toFloat()
            val themeCounts = entries
                .flatMap { e ->
                    val list = if (e.themes.isNotEmpty()) e.themes else listOfNotNull(e.mainTheme.takeIf { it.isNotBlank() })
                    list.map { it.trim().lowercase(Locale.ITALIAN) }
                }
                .filter { it.isNotEmpty() && it != "nota libera" }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(8)
                .map { it.key to it.value }

            val sentimentCounts = entries
                .map { it.sentimentLabel.trim() }
                .filter { it.isNotEmpty() && it != "Da esplorare" }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(6)
                .map { it.key to it.value }

            return RecurringStats(
                totalEntries = entries.size,
                averageValence = avgValence,
                averageClarity = avgClarity,
                topThemes = themeCounts,
                topSentiments = sentimentCounts,
                recentTrend = entries.take(10).reversed()
            )
        }
    }
}
