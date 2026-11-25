package com.example.data

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class GeminiDiaryAssistant {

    private val systemTonePrompt = """
        Sei Vespro, una presenza discreta per un diario privato degli sfoghi e delle riflessioni (sia durante la giornata che prima di dormire) in italiano.
        Regole fondamentali di tono (DA RISPETTARE SEMPRE):
        1. Dai sempre del "tu" all'utente con un tono informale, umano, caldo, lucido e alla pari.
        2. ZERO giudizio etico o morale: non valutare mai se qualcosa è giusto, sbagliato, sano o tossico. È un dialogo onesto con sé stessi su gioie, dolori e pensieri.
        3. ZERO frasi fatte da psicologo o gergo terapeutico. È SEVERAMENTE VIETATO usare espressioni come: "accogli questa emozione", "sii gentile con te stesso", "spazio sicuro", "elaborare il vissuto", "percorso di guarigione", "validazione emotiva", "confini sani", "cosa ti dice il corpo", "prenditi cura del tuo io interiore", "è normale sentirsi così", "lavora su te stesso".
        4. Non dare consigli non richiesti né prediche. Riorganizza in prima persona ("Io") restando fedele alle parole e al registro dell'utente.
        5. Le domande di riflessione devono essere stimolanti, concrete, curiose e ancorate esattamente a ciò che l'utente ha raccontato oggi.
    """.trimIndent()

    private val entrySchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Titolo breve ed evocativo della serata, max 8 parole, senza punti esclamativi retorici."),
            "reorganizedText" to Schema.string(description = "Il flusso di coscienza riorganizzato in prima persona in 2-4 paragrafi puliti e scorrevoli, fedele ai fatti e alla voce di chi scrive, senza aggiunte moralistiche."),
            "mainTheme" to Schema.string(description = "Il tema centrale della giornata in 1-3 parole (es. 'Ritmo lavorativo', 'Rapporto con Marco', 'Bisogno di staccare')."),
            "themes" to Schema.array(
                items = Schema.string(),
                description = "Da 1 a 4 tag tematici brevi in italiano, tutti in minuscolo."
            ),
            "sentimentLabel" to Schema.string(description = "Descrizione semplice e naturale del tono emotivo prevalente (es. 'Carico ma lucido', 'Un po' scarico', 'Leggero', 'Inquieta attesa')."),
            "sentimentValence" to Schema.integer(description = "Valenza emotiva percepita da 1 (giornata pesante/tesa) a 5 (giornata serena/leggera)."),
            "mentalClarity" to Schema.integer(description = "Livello di chiarezza/leggerezza mentale da 1 (testa molto affollata) a 5 (mente sgombra)."),
            "reflectionQuestion" to Schema.string(description = "Una sola domanda di riflessione stimolante, informale, empatica e concreta legata al tema del giorno. Nessun giudizio e nessuna frase da psicologo."),
            "takeaway" to Schema.string(description = "Una riga sintetica e concreta che fotografa cosa resta aperto o chiaro stasera, senza prediche.")
        )
    )

    private val questionSchema = Schema.obj(
        properties = mapOf(
            "reflectionQuestion" to Schema.string(description = "Una nuova domanda di riflessione stimolante, informale, empatica, concreta e senza linguaggio clinico o giudizio, basata sul tema del giorno.")
        )
    )

    private val weeklySchema = Schema.obj(
        properties = mapOf(
            "headline" to Schema.string(description = "Titolo breve della settimana, max 9 parole, tono editoriale e umano."),
            "summary" to Schema.string(description = "Sintesi settimanale informale e non giudicante in 2-3 paragrafi: cosa ha occupato più spazio, cosa ha cambiato ritmo, come si è mosso l'umore senza prediche."),
            "recurringSentiments" to Schema.array(
                items = Schema.string(),
                description = "Da 2 a 5 stati d'animo o tonalità emotive ricorrenti nella settimana."
            ),
            "recurringThemes" to Schema.array(
                items = Schema.string(),
                description = "Da 2 a 5 temi o argomenti tornati più spesso."
            ),
            "recurringPatterns" to Schema.string(description = "Osservazioni pratiche sui collegamenti ricorrenti tra situazioni, orari/ritmi e stato d'animo, formulate come spunti neutri e curiosi."),
            "weeklyQuestion" to Schema.string(description = "Una domanda stimolante e informale per guardare alla settimana che arriva, zero clichés.")
        )
    )

    private val entryModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = entrySchema
                temperature = 0.7f
            },
            systemInstruction = content { text(systemTonePrompt) }
        )
    }

    private val questionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = questionSchema
                temperature = 0.9f
            },
            systemInstruction = content { text(systemTonePrompt) }
        )
    }

    private val aphorismSchema = Schema.obj(
        properties = mapOf(
            "quoteText" to Schema.string(description = "Frase o aforisma breve e altamente suggestivo (max 35 parole), intenso, poetico ma mai retorico o da psicologo, adatto sia da tenere per sé che da condividere come pensiero/post."),
            "attribution" to Schema.string(description = "Nome dell'autore reale se è una citazione celebre autentica e verificata; altrimenti 'Vespro' se è una frase originale inventata su misura."),
            "momentLabel" to Schema.string(description = "Etichetta breve del momento (es. 'Buonanotte', 'Sera', 'Sfogo del giorno', 'Pensiero lucido')."),
            "themeTag" to Schema.string(description = "Parola o tema sintetico legato alla frase, max 3 parole.")
        )
    )

    private val weeklyModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = weeklySchema
                temperature = 0.65f
            },
            systemInstruction = content { text(systemTonePrompt) }
        )
    }

    private val aphorismModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = aphorismSchema
                temperature = 0.85f
            },
            systemInstruction = content { text(systemTonePrompt) }
        )
    }

    private fun buildProfileContext(profile: UserProfile?): String {
        if (profile == null) return ""
        val parts = buildList {
            if (profile.preferredName.isNotBlank()) add("Nome: ${profile.preferredName}")
            if (profile.ageRange.isNotBlank()) add("Età/fascia: ${profile.ageRange}")
            if (profile.lifestyleTags.isNotEmpty()) add("Stile di vita: ${profile.lifestyleTags.joinToString(", ")}")
            if (profile.lifestyleNotes.isNotBlank()) add("Nota sul quotidiano: ${profile.lifestyleNotes}")
            if (profile.keyRelationships.isNotEmpty()) add("Legami significativi: ${profile.keyRelationships.joinToString(", ")}")
            if (profile.relationshipsNote.isNotBlank()) add("Contesto relazioni: ${profile.relationshipsNote}")
            if (profile.mainConcerns.isNotEmpty()) add("Preoccupazioni ricorrenti dichiarate: ${profile.mainConcerns.joinToString(", ")}")
            if (profile.concernsNote.isNotBlank()) add("Nota preoccupazioni: ${profile.concernsNote}")
            if (profile.reflectionStyle.isNotBlank()) add("Preferenza stile domande: ${profile.reflectionStyle}")
        }
        if (parts.isEmpty()) return ""
        return """
            Contesto personale condiviso dall'utente (usalo solo come bussola silenziosa per orientare meglio la domanda se pertinente, senza ripeterlo meccanicamente né fare diagnosi):
            ${parts.joinToString("\n")}
        """.trimIndent()
    }

    suspend fun analyzeStreamOfConsciousness(
        transcript: String,
        recentThemes: List<String> = emptyList(),
        userProfile: UserProfile? = null
    ): AiEntryAnalysis {
        val themesHint = if (recentThemes.isNotEmpty()) {
            "Temi già emersi nei giorni scorsi (usali solo per coerenza se davvero presenti oggi): ${recentThemes.joinToString(", ")}."
        } else ""
        val profileContext = buildProfileContext(userProfile)

        val prompt = """
            $profileContext
            
            Ecco il flusso di coscienza di fine giornata appena dettato o scritto dall'utente:
            ---
            $transcript
            ---
            $themesHint
            
            Compiti:
            1. Riorganizza il testo in prima persona rendendolo scorrevole e ordinato senza cambiare il senso o inventare fatti.
            2. Individua il tema principale del giorno, i tag, il tono emotivo, la valenza (1-5) e la chiarezza mentale (1-5).
            3. Scrivi UNA domanda di riflessione stimolante basata proprio sul tema del giorno: informale, empatica, acuta, zero giudizio e zero frasi da psicologo.
            4. Aggiungi un "takeaway" di una riga che fotografi lo stato della serata senza consigli non richiesti.
        """.trimIndent()

        val response = try {
            withTimeout(60_000L) {
                entryModel.generateContent(prompt)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Entry analysis failed", e)
            throw e
        }

        val rawJson = response.text
            ?: throw IllegalStateException("Gemini non ha restituito alcuna risposta.")
        val json = JSONObject(rawJson)

        val themesArray = json.optJSONArray("themes")
        val themes = buildList {
            if (themesArray != null) {
                for (i in 0 until themesArray.length()) {
                    val item = themesArray.optString(i).trim()
                    if (item.isNotEmpty()) add(item.take(40))
                }
            }
        }.take(6)

        return AiEntryAnalysis(
            title = json.optString("title", "Appunti della sera").take(120),
            reorganizedText = json.optString("reorganizedText", transcript).take(15000),
            mainTheme = json.optString("mainTheme", "Giornata").take(80),
            themes = themes,
            sentimentLabel = json.optString("sentimentLabel", "Riflessivo").take(60),
            sentimentValence = json.optInt("sentimentValence", 3).coerceIn(1, 5),
            mentalClarity = json.optInt("mentalClarity", 3).coerceIn(1, 5),
            reflectionQuestion = json.optString("reflectionQuestion", "").take(600),
            takeaway = json.optString("takeaway", "").take(400)
        )
    }

    suspend fun generateAlternativeQuestion(
        transcript: String,
        mainTheme: String,
        previousQuestion: String,
        userProfile: UserProfile? = null
    ): String {
        val profileContext = buildProfileContext(userProfile)
        val prompt = """
            $profileContext
            
            Testo del diario di stasera:
            ---
            $transcript
            ---
            Tema del giorno: $mainTheme
            Domanda precedente (da NON ripetere, proponi una prospettiva diversa ma concreta): $previousQuestion
            
            Genera una nuova domanda di riflessione breve, informale, empatica e stimolante sul tema del giorno, senza alcun giudizio né frasi da psicologo.
        """.trimIndent()

        val response = try {
            withTimeout(60_000L) {
                questionModel.generateContent(prompt)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Alternative question generation failed", e)
            throw e
        }

        val rawJson = response.text
            ?: throw IllegalStateException("Gemini non ha restituito alcuna domanda.")
        val json = JSONObject(rawJson)
        return json.optString("reflectionQuestion", "").trim().take(600)
            .ifEmpty { throw IllegalStateException("Domanda vuota ricevuta dal modello.") }
    }

    suspend fun generateWeeklySynthesis(
        entries: List<DiaryEntry>,
        periodLabel: String,
        userProfile: UserProfile? = null
    ): AiWeeklySynthesis {
        val profileContext = buildProfileContext(userProfile)
        val entriesDigest = entries.take(14).joinToString("\n\n") { entry ->
            val body = entry.reorganizedText.ifBlank { entry.rawTranscript }
            """
            [Data: ${entry.dateKey} | Tema: ${entry.mainTheme.ifBlank { "n/d" }} | Tono: ${entry.sentimentLabel.ifBlank { "n/d" }} (${entry.sentimentValence}/5) | Chiarezza: ${entry.mentalClarity}/5]
            Titolo: ${entry.title}
            Testo: ${body.take(900)}
            ${if (entry.reflectionAnswer.isNotBlank()) "Risposta alla domanda serale: ${entry.reflectionAnswer.take(350)}" else ""}
            """.trimIndent()
        }

        val prompt = """
            $profileContext
            
            Periodo analizzato: $periodLabel (${entries.size} pagine di diario).
            
            Pagine del diario:
            $entriesDigest
            
            Compiti:
            1. Tira le somme della settimana con tono informale, lucido e privo di giudizio o frasi da psicologo.
            2. Individua i sentimenti ricorrenti e i temi tornati più volte.
            3. Evidenzia eventuali schemi o connessioni concrete tra ciò che succede nelle giornate e come cambia il tono emotivo/chiarezza.
            4. Chiudi con una domanda aperta stimolante per la settimana che viene.
        """.trimIndent()

        val response = try {
            withTimeout(60_000L) {
                weeklyModel.generateContent(prompt)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Weekly synthesis failed", e)
            throw e
        }

        val rawJson = response.text
            ?: throw IllegalStateException("Gemini non ha restituito la sintesi settimanale.")
        val json = JSONObject(rawJson)

        fun readList(key: String): List<String> {
            val arr = json.optJSONArray(key) ?: return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i).trim()
                    if (v.isNotEmpty()) add(v.take(40))
                }
            }.take(6)
        }

        return AiWeeklySynthesis(
            headline = json.optString("headline", "Bilancio della settimana").take(150),
            summary = json.optString("summary", "").take(6000),
            recurringSentiments = readList("recurringSentiments"),
            recurringThemes = readList("recurringThemes"),
            recurringPatterns = json.optString("recurringPatterns", "").take(3000),
            weeklyQuestion = json.optString("weeklyQuestion", "").take(600)
        )
    }

    suspend fun generateSuggestiveAphorism(
        sourceText: String,
        mainTheme: String = "",
        sentimentLabel: String = "",
        isNightTime: Boolean = false,
        userProfile: UserProfile? = null
    ): AiAphorismResult {
        val profileContext = buildProfileContext(userProfile)
        val momentInstruction = if (isNightTime) {
            "È sera/notte o il momento prima di andare a dormire: calibra la frase come un pensiero della buonanotte suggestivo, che chiuda la giornata con onestà, respiro o incoraggiamento discreto, senza positività tossica."
        } else {
            "È un momento della giornata (diario degli sfoghi e dialogo onesto con sé stessi): calibra la frase come un'istantanea lucida, intensa ed evocativa di gioie, pesi o verità vissute oggi, adatta anche a essere riletta o condivisa come post."
        }
        val cleanSource = sourceText.trim().take(2500)
        val prompt = """
            $profileContext
            
            Contesto su cui basare l'aforisma o frase:
            ${if (mainTheme.isNotBlank()) "Tema: $mainTheme" else ""}
            ${if (sentimentLabel.isNotBlank()) "Tono emotivo: $sentimentLabel" else ""}
            Testo o pensieri recenti:
            ---
            ${cleanSource.ifEmpty { "Dialogo sincero con sé stessi tra ciò che pesa e ciò che dà luce." }}
            ---
            
            Indicazione di momento: $momentInstruction
            
            Scegli con cura:
            - Puoi proporre una CITAZIONE CELEBRE reale e autentica (letteratura, filosofia, poesia) solo se sei certo dell'autore esatto e calza a pennello — mai inventare attribuzioni.
            - Oppure crea un AFORISMA ORIGINALE altamente suggestivo e memorabile (in questo caso usa attribution = "Vespro").
            - Zero retorica da social motivazionali spiccioli, zero frasi fatte da psicologo, zero giudizio.
        """.trimIndent()

        val response = try {
            withTimeout(60_000L) {
                aphorismModel.generateContent(prompt)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Aphorism generation failed", e)
            throw e
        }

        val rawJson = response.text
            ?: throw IllegalStateException("Gemini non ha restituito nessuna frase.")
        val json = JSONObject(rawJson)

        val quote = json.optString("quoteText", "").trim().take(1000)
        if (quote.isEmpty()) {
            throw IllegalStateException("Frase vuota ricevuta dal modello.")
        }

        return AiAphorismResult(
            quoteText = quote,
            attribution = json.optString("attribution", "Vespro").trim().take(120).ifEmpty { "Vespro" },
            momentLabel = json.optString(
                "momentLabel",
                if (isNightTime) "Buonanotte" else "Pensiero del giorno"
            ).trim().take(40),
            themeTag = json.optString("themeTag", mainTheme.ifBlank { "onestà" }).trim().take(80)
        )
    }

    private companion object {
        const val TAG = "GeminiDiaryAssistant"
        const val MODEL_NAME = "gemini-3.8-flash"
    }
}
