package com.example.data

import android.content.Context
import com.example.core.OperationType
import com.example.core.VesproFirestoreProvider
import com.example.core.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class DiaryRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        VesproFirestoreProvider.getInstance(context)
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Effettua l'accesso con Google prima di accedere al diario.")
    }

    fun observeEntries(userId: String): Flow<List<DiaryEntry>> =
        observeEntriesWithSync(userId).map { it.entries }

    fun observeEntriesWithSync(userId: String): Flow<SyncedEntriesSnapshot> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Sessione utente non corrispondente." }
        val path = "users/$uid/entries"
        emitAll(
            db.collection("users").document(uid).collection("entries")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    SyncedEntriesSnapshot(
                        entries = snapshot.toObjects(
                            DiaryEntry::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ),
                        hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                        isFromCache = snapshot.metadata.isFromCache,
                        snapshotTimeMillis = System.currentTimeMillis()
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun forceCloudSync(userId: String): Result<Unit> {
        val path = "users/$userId/entries"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sessione utente cambiata; sincronizzazione annullata." }
            db.waitForPendingWrites().await()
            db.collection("users").document(uid).collection("entries")
                .limit(1)
                .get(Source.SERVER)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    fun observeWeeklySummaries(userId: String): Flow<List<WeeklySummary>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Sessione utente non corrispondente." }
        val path = "users/$uid/weeklySummaries"
        emitAll(
            db.collection("users").document(uid).collection("weeklySummaries")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        WeeklySummary::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Sessione utente non corrispondente." }
        val path = "users/$uid/profile/$PROFILE_DOC_ID"
        emitAll(
            db.collection("users").document(uid).collection("profile").document(PROFILE_DOC_ID)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveUserProfile(userId: String, profile: UserProfile): Result<Unit> {
        val docPath = "users/$userId/profile/$PROFILE_DOC_ID"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sessione utente cambiata; salvataggio profilo annullato." }
            val docRef = db.collection("users").document(uid)
                .collection("profile").document(PROFILE_DOC_ID)

            val mutableFields = mapOf(
                "preferredName" to profile.preferredName.trim().take(60),
                "ageRange" to profile.ageRange.trim().take(30),
                "lifestyleTags" to profile.lifestyleTags.map { it.trim().take(40) }.filter { it.isNotEmpty() }.distinct().take(8),
                "lifestyleNotes" to profile.lifestyleNotes.trim().take(1500),
                "keyRelationships" to profile.keyRelationships.map { it.trim().take(40) }.filter { it.isNotEmpty() }.distinct().take(8),
                "relationshipsNote" to profile.relationshipsNote.trim().take(1500),
                "mainConcerns" to profile.mainConcerns.map { it.trim().take(40) }.filter { it.isNotEmpty() }.distinct().take(8),
                "concernsNote" to profile.concernsNote.trim().take(1500),
                "reflectionStyle" to profile.reflectionStyle.trim().take(80).ifEmpty { "Domande dirette e pratiche" },
                "onboardingCompleted" to profile.onboardingCompleted,
                "onboardingSkipped" to profile.onboardingSkipped,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val exists = docRef.get().await().exists()
            if (exists) {
                docRef.update(mutableFields).await()
            } else {
                val createPayload = mutableFields + mapOf(
                    "userId" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createEntry(userId: String, entry: DiaryEntry): Result<String> {
        var docPath = "users/$userId/entries"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sessione utente cambiata; scrittura annullata." }
            val trimmedRaw = entry.rawTranscript.trim().take(15000)
            require(trimmedRaw.isNotEmpty()) { "Il testo della pagina non può essere vuoto." }

            val docRef = db.collection("users").document(uid).collection("entries").document()
            docPath = docRef.path

            val sanitizedThemes = entry.themes
                .map { it.trim().take(40) }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(8)

            val payload = mapOf(
                "userId" to uid,
                "dateKey" to entry.dateKey.trim().ifEmpty { "2026-11-25" }.take(20),
                "title" to entry.title.trim().take(120),
                "rawTranscript" to trimmedRaw,
                "reorganizedText" to entry.reorganizedText.trim().take(15000),
                "mainTheme" to entry.mainTheme.trim().take(80),
                "themes" to sanitizedThemes,
                "sentimentLabel" to entry.sentimentLabel.trim().take(60),
                "sentimentValence" to entry.sentimentValence.coerceIn(1L, 5L),
                "mentalClarity" to entry.mentalClarity.coerceIn(1L, 5L),
                "reflectionQuestion" to entry.reflectionQuestion.trim().take(600),
                "reflectionAnswer" to entry.reflectionAnswer.trim().take(5000),
                "takeaway" to entry.takeaway.trim().take(400),
                "usedVoice" to entry.usedVoice,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateEntryAnalysis(
        userId: String,
        entryId: String,
        analysis: AiEntryAnalysis
    ): Result<Unit> {
        var docPath = "users/$userId/entries/$entryId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sessione utente cambiata; aggiornamento annullato." }
            require(entryId.isNotBlank()) { "ID pagina non valido." }

            val docRef = db.collection("users").document(uid).collection("entries").document(entryId)
            docPath = docRef.path

            val sanitizedThemes = analysis.themes
                .map { it.trim().take(40) }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(8)

            docRef.update(
                mapOf(
                    "title" to analysis.title.trim().take(120),
                    "reorganizedText" to analysis.reorganizedText.trim().take(15000),
                    "mainTheme" to analysis.mainTheme.trim().take(80),
                    "themes" to sanitizedThemes,
                    "sentimentLabel" to analysis.sentimentLabel.trim().take(60),
                    "sentimentValence" to analysis.sentimentValence.coerceIn(1, 5).toLong(),
                    "mentalClarity" to analysis.mentalClarity.coerceIn(1, 5).toLong(),
                    "reflectionQuestion" to analysis.reflectionQuestion.trim().take(600),
                    "takeaway" to analysis.takeaway.trim().take(400),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateReflectionQuestion(
        userId: String,
        entryId: String,
        question: String
    ): Result<Unit> {
        var docPath = "users/$userId/entries/$entryId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sessione utente cambiata." }
            require(entryId.isNotBlank()) { "ID pagina non valido." }
            val docRef = db.collection("users").document(uid).collection("entries").document(entryId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "reflectionQuestion" to question.trim().take(600),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateReflectionAnswer(
        userId: String,
        entryId: String,
        answer: String
    ): Result<Unit> {
        var docPath = "users/$userId/entries/$entryId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sessione utente cambiata." }
            require(entryId.isNotBlank()) { "ID pagina non valido." }
            val docRef = db.collection("users").document(uid).collection("entries").document(entryId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "reflectionAnswer" to answer.trim().take(5000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteEntry(userId: String, entryId: String): Result<Unit> {
        var docPath = "users/$userId/entries/$entryId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sessione utente cambiata." }
            require(entryId.isNotBlank()) { "ID pagina non valido." }
            val docRef = db.collection("users").document(uid).collection("entries").document(entryId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveWeeklySummary(userId: String, summary: WeeklySummary): Result<String> {
        var docPath = "users/$userId/weeklySummaries"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sessione utente cambiata." }
            val docRef = db.collection("users").document(uid).collection("weeklySummaries").document()
            docPath = docRef.path

            val payload = mapOf(
                "userId" to uid,
                "periodLabel" to summary.periodLabel.trim().take(80).ifEmpty { "Ultima settimana" },
                "headline" to summary.headline.trim().take(150).ifEmpty { "Sintesi settimanale" },
                "summary" to summary.summary.trim().take(6000),
                "recurringSentiments" to summary.recurringSentiments.map { it.trim().take(40) }.filter { it.isNotEmpty() }.take(8),
                "recurringThemes" to summary.recurringThemes.map { it.trim().take(40) }.filter { it.isNotEmpty() }.take(8),
                "recurringPatterns" to summary.recurringPatterns.trim().take(3000),
                "weeklyQuestion" to summary.weeklyQuestion.trim().take(600),
                "entryCount" to summary.entryCount.coerceIn(0L, 500L),
                "averageValence" to summary.averageValence.coerceIn(0.0, 5.0),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteWeeklySummary(userId: String, summaryId: String): Result<Unit> {
        var docPath = "users/$userId/weeklySummaries/$summaryId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sessione utente cambiata." }
            require(summaryId.isNotBlank()) { "ID sintesi non valido." }
            val docRef = db.collection("users").document(uid).collection("weeklySummaries").document(summaryId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    fun observeAphorisms(userId: String): Flow<List<SavedAphorism>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Sessione utente non corrispondente." }
        val path = "users/$uid/aphorisms"
        emitAll(
            db.collection("users").document(uid).collection("aphorisms")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SavedAphorism::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveAphorism(userId: String, aphorism: SavedAphorism): Result<String> {
        var docPath = "users/$userId/aphorisms"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sessione utente cambiata." }
            val trimmedQuote = aphorism.quoteText.trim().take(1000)
            require(trimmedQuote.isNotEmpty()) { "La frase non può essere vuota." }

            val docRef = db.collection("users").document(uid).collection("aphorisms").document()
            docPath = docRef.path

            val payload = mapOf(
                "userId" to uid,
                "entryId" to aphorism.entryId.trim().take(128),
                "quoteText" to trimmedQuote,
                "attribution" to aphorism.attribution.trim().take(120).ifEmpty { "Vespro" },
                "momentLabel" to aphorism.momentLabel.trim().take(40).ifEmpty { "Riflessione" },
                "themeTag" to aphorism.themeTag.trim().take(80),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteAphorism(userId: String, aphorismId: String): Result<Unit> {
        var docPath = "users/$userId/aphorisms/$aphorismId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sessione utente cambiata." }
            require(aphorismId.isNotBlank()) { "ID frase non valido." }
            val docRef = db.collection("users").document(uid).collection("aphorisms").document(aphorismId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    private companion object {
        const val PROFILE_DOC_ID = "main"
    }
}
