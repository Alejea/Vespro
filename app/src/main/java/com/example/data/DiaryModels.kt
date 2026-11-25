package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class DiaryEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = "",
    val title: String = "",
    val rawTranscript: String = "",
    val reorganizedText: String = "",
    val mainTheme: String = "",
    val themes: List<String> = emptyList(),
    val sentimentLabel: String = "",
    val sentimentValence: Long = 3L,
    val mentalClarity: Long = 3L,
    val reflectionQuestion: String = "",
    val reflectionAnswer: String = "",
    val takeaway: String = "",
    val usedVoice: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class WeeklySummary(
    @DocumentId val id: String = "",
    val userId: String = "",
    val periodLabel: String = "",
    val headline: String = "",
    val summary: String = "",
    val recurringSentiments: List<String> = emptyList(),
    val recurringThemes: List<String> = emptyList(),
    val recurringPatterns: String = "",
    val weeklyQuestion: String = "",
    val entryCount: Long = 0L,
    val averageValence: Double = 3.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val preferredName: String = "",
    val ageRange: String = "",
    val lifestyleTags: List<String> = emptyList(),
    val lifestyleNotes: String = "",
    val keyRelationships: List<String> = emptyList(),
    val relationshipsNote: String = "",
    val mainConcerns: List<String> = emptyList(),
    val concernsNote: String = "",
    val reflectionStyle: String = "Domande dirette e pratiche",
    val onboardingCompleted: Boolean = false,
    val onboardingSkipped: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class AiEntryAnalysis(
    val title: String,
    val reorganizedText: String,
    val mainTheme: String,
    val themes: List<String>,
    val sentimentLabel: String,
    val sentimentValence: Int,
    val mentalClarity: Int,
    val reflectionQuestion: String,
    val takeaway: String
)

data class AiWeeklySynthesis(
    val headline: String,
    val summary: String,
    val recurringSentiments: List<String>,
    val recurringThemes: List<String>,
    val recurringPatterns: String,
    val weeklyQuestion: String
)

@Keep
@IgnoreExtraProperties
data class SavedAphorism(
    @DocumentId val id: String = "",
    val userId: String = "",
    val entryId: String = "",
    val quoteText: String = "",
    val attribution: String = "",
    val momentLabel: String = "",
    val themeTag: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class AiAphorismResult(
    val quoteText: String,
    val attribution: String,
    val momentLabel: String,
    val themeTag: String
)

enum class SyncPhase {
    SYNCING,
    SYNCED,
    PENDING_UPLOAD,
    OFFLINE_CACHE,
    ERROR
}

data class CloudSyncStatus(
    val phase: SyncPhase = SyncPhase.SYNCING,
    val lastSyncedMillis: Long? = null,
    val errorMessage: String? = null
)

data class SyncedEntriesSnapshot(
    val entries: List<DiaryEntry> = emptyList(),
    val hasPendingWrites: Boolean = false,
    val isFromCache: Boolean = false,
    val snapshotTimeMillis: Long = System.currentTimeMillis()
)


