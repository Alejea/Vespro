package com.example.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.DiaryEntry
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PdfExportScope {
    FILTERED,
    LAST_7_DAYS,
    LAST_30_DAYS,
    ALL_ENTRIES
}

data class GeneratedPdfArtifact(
    val file: File,
    val contentUri: Uri,
    val fileName: String,
    val pageCount: Int,
    val entryCount: Int
)

object DiaryPdfExporter {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_H = 52f
    private const val MARGIN_TOP = 54f
    private const val MARGIN_BOTTOM = 52f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_H * 2f)

    // Warm Ivory Editorial Palette matching Vespro Light Parchment tokens
    private const val COLOR_IVORY_BG = 0xFFFAF5EC.toInt()
    private const val COLOR_CALLOUT_BG = 0xFFF3ECE0.toInt()
    private const val COLOR_CHARCOAL_INK = 0xFF1F1A2C.toInt()
    private const val COLOR_TAUPE_INK = 0xFF5D556E.toInt()
    private const val COLOR_AMBER_INK = 0xFF9A5B13.toInt()
    private const val COLOR_OUTLINE = 0xFFD5CCB9.toInt()

    fun selectEntriesForScope(
        allEntries: List<DiaryEntry>,
        filteredEntries: List<DiaryEntry>,
        scope: PdfExportScope,
        nowMillis: Long = System.currentTimeMillis()
    ): List<DiaryEntry> {
        val rawSelection = when (scope) {
            PdfExportScope.FILTERED -> filteredEntries
            PdfExportScope.ALL_ENTRIES -> allEntries
            PdfExportScope.LAST_7_DAYS -> filterByRecentDays(allEntries, 7, nowMillis)
            PdfExportScope.LAST_30_DAYS -> filterByRecentDays(allEntries, 30, nowMillis)
        }
        return rawSelection.sortedWith(
            compareBy<DiaryEntry> { it.dateKey }.thenBy { it.createdAt?.seconds ?: 0L }
        )
    }

    private fun filterByRecentDays(
        entries: List<DiaryEntry>,
        days: Int,
        nowMillis: Long
    ): List<DiaryEntry> {
        val cutoffMillis = nowMillis - days.toLong() * 24L * 60L * 60L * 1000L
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        return entries.filter { entry ->
            val createdMs = entry.createdAt?.toDate()?.time
            if (createdMs != null) {
                createdMs >= cutoffMillis
            } else {
                val parsedMs = runCatching { dateFormat.parse(entry.dateKey)?.time }.getOrNull()
                parsedMs != null && parsedMs >= cutoffMillis - 24L * 60L * 60L * 1000L
            }
        }
    }

    fun exportEntries(
        context: Context,
        entries: List<DiaryEntry>,
        collectionTitle: String?,
        fileNameSlug: String
    ): GeneratedPdfArtifact {
        require(entries.isNotEmpty()) { "Nessuna pagina da esportare in PDF." }

        val orderedEntries = entries.sortedWith(
            compareBy<DiaryEntry> { it.dateKey }.thenBy { it.createdAt?.seconds ?: 0L }
        )

        val serifTypeface = loadFontOrFallback(context, R.font.fraunces, Typeface.SERIF)
        val sansTypeface = loadFontOrFallback(context, R.font.plus_jakarta_sans, Typeface.SANS_SERIF)
        val monoTypeface = loadFontOrFallback(context, R.font.jetbrains_mono, Typeface.MONOSPACE)

        val paints = EditorialPaints(
            serifTypeface = serifTypeface,
            sansTypeface = sansTypeface,
            monoTypeface = monoTypeface
        )

        val document = PdfDocument()
        val state = PaginationCursor(document, paints)

        try {
            state.startNewPage(drawTopHeaderRule = orderedEntries.size == 1)

            // Collection cover header when exporting multiple entries
            if (orderedEntries.size > 1 || !collectionTitle.isNullOrBlank()) {
                state.drawCollectionMasthead(
                    title = collectionTitle ?: "Raccolta del Diario",
                    entryCount = orderedEntries.size,
                    firstDate = orderedEntries.first().dateKey,
                    lastDate = orderedEntries.last().dateKey
                )
            }

            orderedEntries.forEachIndexed { index, entry ->
                if (index > 0) {
                    if (state.remainingSpace() < 170f) {
                        state.startNewPage()
                    } else {
                        state.drawSectionDivider()
                    }
                }
                state.drawDiaryEntry(entry)
            }

            state.finishCurrentPage()

            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            pruneOldExports(exportsDir)

            val safeSlug = fileNameSlug
                .lowercase(Locale.ITALIAN)
                .replace(Regex("[^a-z0-9_-]+"), "_")
                .trim('_')
                .ifEmpty { "diario" }
                .take(40)
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "Vespro_${safeSlug}_$stamp.pdf"
            val outFile = File(exportsDir, fileName)

            FileOutputStream(outFile).use { fos ->
                document.writeTo(fos)
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )

            return GeneratedPdfArtifact(
                file = outFile,
                contentUri = uri,
                fileName = fileName,
                pageCount = state.pageNumber,
                entryCount = orderedEntries.size
            )
        } finally {
            document.close()
        }
    }

    fun copyPdfToDestination(context: Context, sourceFile: File, destinationUri: Uri) {
        val outputStream = context.contentResolver.openOutputStream(destinationUri)
            ?: throw IllegalStateException("Impossibile aprire il percorso di salvataggio selezionato.")
        outputStream.use { out ->
            sourceFile.inputStream().use { input ->
                input.copyTo(out)
            }
        }
    }

    private fun loadFontOrFallback(context: Context, fontRes: Int, fallback: Typeface): Typeface {
        return runCatching { ResourcesCompat.getFont(context, fontRes) }.getOrNull() ?: fallback
    }

    private fun pruneOldExports(exportsDir: File) {
        val files = exportsDir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(8).forEach { runCatching { it.delete() } }
    }

    private class EditorialPaints(
        serifTypeface: Typeface,
        sansTypeface: Typeface,
        monoTypeface: Typeface
    ) {
        val bgPaint = Paint().apply {
            color = COLOR_IVORY_BG
            style = Paint.Style.FILL
        }
        val calloutPaint = Paint().apply {
            color = COLOR_CALLOUT_BG
            style = Paint.Style.FILL
        }
        val accentBarPaint = Paint().apply {
            color = COLOR_AMBER_INK
            style = Paint.Style.FILL
        }
        val dividerPaint = Paint().apply {
            color = COLOR_OUTLINE
            strokeWidth = 0.8f
        }

        val mastheadEyebrow = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(monoTypeface, Typeface.BOLD)
            textSize = 9f
            color = COLOR_AMBER_INK
            letterSpacing = 0.12f
        }
        val mastheadTitle = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(serifTypeface, Typeface.BOLD)
            textSize = 22f
            color = COLOR_CHARCOAL_INK
        }
        val mastheadSub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = sansTypeface
            textSize = 10.5f
            color = COLOR_TAUPE_INK
        }

        val entryMeta = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(monoTypeface, Typeface.NORMAL)
            textSize = 9f
            color = COLOR_AMBER_INK
        }
        val entryTitle = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(serifTypeface, Typeface.BOLD)
            textSize = 17f
            color = COLOR_CHARCOAL_INK
        }
        val entrySubMeta = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = sansTypeface
            textSize = 9.5f
            color = COLOR_TAUPE_INK
        }
        val bodyText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = sansTypeface
            textSize = 11f
            color = COLOR_CHARCOAL_INK
        }
        val calloutLabel = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(monoTypeface, Typeface.BOLD)
            textSize = 8.5f
            color = COLOR_AMBER_INK
            letterSpacing = 0.08f
        }
        val questionText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(serifTypeface, Typeface.ITALIC)
            textSize = 12f
            color = COLOR_CHARCOAL_INK
        }
        val answerText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = sansTypeface
            textSize = 10.5f
            color = COLOR_CHARCOAL_INK
        }
        val takeawayText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(sansTypeface, Typeface.ITALIC)
            textSize = 9.5f
            color = COLOR_TAUPE_INK
        }
        val footerText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = monoTypeface
            textSize = 8.5f
            color = COLOR_TAUPE_INK
        }
    }

    private class PaginationCursor(
        private val document: PdfDocument,
        private val paints: EditorialPaints
    ) {
        var pageNumber = 0
            private set

        private var currentPage: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var cursorY = MARGIN_TOP
        private val maxBottomY = PAGE_HEIGHT - MARGIN_BOTTOM - 16f

        fun remainingSpace(): Float = maxBottomY - cursorY

        fun startNewPage(drawTopHeaderRule: Boolean = true) {
            finishCurrentPage()
            pageNumber += 1
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val page = document.startPage(pageInfo)
            currentPage = page
            val c = page.canvas
            canvas = c

            // Ivory paper background
            c.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), paints.bgPaint)

            if (drawTopHeaderRule && pageNumber > 1) {
                c.drawText("VESPRO · DIARIO PRIVATO", MARGIN_H, 34f, paints.footerText)
                c.drawLine(MARGIN_H, 40f, PAGE_WIDTH - MARGIN_H, 40f, paints.dividerPaint)
                cursorY = 58f
            } else {
                cursorY = MARGIN_TOP
            }
        }

        fun finishCurrentPage() {
            val page = currentPage ?: return
            val c = canvas ?: return

            val footerY = PAGE_HEIGHT - 28f
            c.drawLine(MARGIN_H, footerY - 12f, PAGE_WIDTH - MARGIN_H, footerY - 12f, paints.dividerPaint)
            c.drawText("Vespro · Dialogo onesto senza giudizio", MARGIN_H, footerY, paints.footerText)
            val pageLabel = "Pag. $pageNumber"
            val labelW = paints.footerText.measureText(pageLabel)
            c.drawText(pageLabel, PAGE_WIDTH - MARGIN_H - labelW, footerY, paints.footerText)

            document.finishPage(page)
            currentPage = null
            canvas = null
        }

        fun drawCollectionMasthead(
            title: String,
            entryCount: Int,
            firstDate: String,
            lastDate: String
        ) {
            drawStaticWrapped("VESPRO · EDIZIONE PERSONALE", paints.mastheadEyebrow, CONTENT_WIDTH)
            cursorY += 4f
            drawStaticWrapped(title, paints.mastheadTitle, CONTENT_WIDTH)
            cursorY += 4f
            val dateRange = if (firstDate == lastDate) firstDate else "$firstDate — $lastDate"
            val subtitle = "$entryCount ${if (entryCount == 1) "pagina" else "pagine"} · $dateRange"
            drawStaticWrapped(subtitle, paints.mastheadSub, CONTENT_WIDTH)
            cursorY += 12f
            canvas?.drawLine(MARGIN_H, cursorY, PAGE_WIDTH - MARGIN_H, cursorY, paints.dividerPaint)
            cursorY += 20f
        }

        fun drawSectionDivider() {
            cursorY += 10f
            canvas?.drawLine(MARGIN_H, cursorY, PAGE_WIDTH - MARGIN_H, cursorY, paints.dividerPaint)
            cursorY += 20f
        }

        fun drawDiaryEntry(entry: DiaryEntry) {
            // Keep title + meta together when near bottom of page
            if (remainingSpace() < 110f) {
                startNewPage()
            }

            val metaParts = buildList {
                add(entry.dateKey.ifBlank { "Data" })
                if (entry.mainTheme.isNotBlank()) add(entry.mainTheme)
                if (entry.sentimentLabel.isNotBlank()) add(entry.sentimentLabel)
            }.joinToString("  ·  ")

            drawStaticWrapped(metaParts.uppercase(Locale.ITALIAN), paints.entryMeta, CONTENT_WIDTH)
            cursorY += 4f

            val title = entry.title.ifBlank { "Pagina di diario" }
            drawStaticWrapped(title, paints.entryTitle, CONTENT_WIDTH)
            cursorY += 8f

            // Primary body: reorganized text (clean fallback to raw transcript if entry wasn't reorganized)
            val isFallbackRaw = entry.reorganizedText.isBlank()
            val body = entry.reorganizedText.ifBlank { entry.rawTranscript }.trim()
            if (isFallbackRaw) {
                drawStaticWrapped("Nota salvata in formato originale", paints.entrySubMeta, CONTENT_WIDTH)
                cursorY += 4f
            }

            val paragraphs = body.split(Regex("\\n\\s*\\n"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            paragraphs.forEachIndexed { idx, paragraph ->
                drawStaticWrapped(
                    text = paragraph,
                    paint = paints.bodyText,
                    width = CONTENT_WIDTH,
                    lineSpacingMult = 1.35f
                )
                if (idx < paragraphs.lastIndex) {
                    cursorY += 8f
                }
            }

            // Reflection Question & Answer Callout
            val hasQuestion = entry.reflectionQuestion.isNotBlank()
            val hasAnswer = entry.reflectionAnswer.isNotBlank()
            if (hasQuestion || hasAnswer) {
                cursorY += 14f
                if (remainingSpace() < 86f) {
                    startNewPage()
                }
                drawReflectionCallout(
                    question = entry.reflectionQuestion.trim(),
                    answer = entry.reflectionAnswer.trim()
                )
            }

            if (entry.takeaway.isNotBlank()) {
                cursorY += 10f
                drawStaticWrapped(
                    text = "In sintesi: ${entry.takeaway.trim()}",
                    paint = paints.takeawayText,
                    width = CONTENT_WIDTH,
                    lineSpacingMult = 1.25f
                )
            }

            cursorY += 14f
        }

        private fun drawReflectionCallout(question: String, answer: String) {
            val innerPadH = 14f
            val innerPadV = 10f
            val innerWidth = CONTENT_WIDTH - (innerPadH * 2f)

            val qLayout = if (question.isNotEmpty()) {
                buildStaticLayout(question, paints.questionText, innerWidth, 1.3f)
            } else null
            val aLabel = if (answer.isNotEmpty()) "LA TUA RISPOSTA" else null
            val aLayout = if (answer.isNotEmpty()) {
                buildStaticLayout(answer, paints.answerText, innerWidth, 1.3f)
            } else null

            val headerHeight = 14f
            val qHeight = qLayout?.height?.toFloat() ?: 0f
            val answerBlockHeight = if (aLayout != null) 18f + aLayout.height.toFloat() else 0f
            val totalBoxHeight = innerPadV * 2f + headerHeight + qHeight + answerBlockHeight +
                if (qLayout != null && aLayout != null) 8f else 0f

            // If entire callout fits cleanly on current page, draw as one crisp card
            if (totalBoxHeight <= remainingSpace()) {
                val c = canvas ?: return
                val rect = RectF(MARGIN_H, cursorY, MARGIN_H + CONTENT_WIDTH, cursorY + totalBoxHeight)
                c.drawRoundRect(rect, 8f, 8f, paints.calloutPaint)
                c.drawRect(MARGIN_H, cursorY, MARGIN_H + 3.5f, cursorY + totalBoxHeight, paints.accentBarPaint)

                var innerY = cursorY + innerPadV
                c.drawText("SPUNTO DI RIFLESSIONE", MARGIN_H + innerPadH, innerY + 9f, paints.calloutLabel)
                innerY += headerHeight

                if (qLayout != null) {
                    c.save()
                    c.translate(MARGIN_H + innerPadH, innerY)
                    qLayout.draw(c)
                    c.restore()
                    innerY += qHeight + if (aLayout != null) 8f else 0f
                }

                if (aLayout != null && aLabel != null) {
                    c.drawText(aLabel, MARGIN_H + innerPadH, innerY + 9f, paints.calloutLabel)
                    innerY += 16f
                    c.save()
                    c.translate(MARGIN_H + innerPadH, innerY)
                    aLayout.draw(c)
                    c.restore()
                }

                cursorY += totalBoxHeight
            } else {
                // Multi-page fallback: stream tinted bands cleanly line by line
                drawTintedBlockLineByLine(
                    label = "SPUNTO DI RIFLESSIONE",
                    text = question,
                    paint = paints.questionText,
                    innerWidth = innerWidth,
                    innerPadH = innerPadH
                )
                if (answer.isNotEmpty()) {
                    cursorY += 4f
                    drawTintedBlockLineByLine(
                        label = "LA TUA RISPOSTA",
                        text = answer,
                        paint = paints.answerText,
                        innerWidth = innerWidth,
                        innerPadH = innerPadH
                    )
                }
            }
        }

        private fun drawTintedBlockLineByLine(
            label: String,
            text: String,
            paint: TextPaint,
            innerWidth: Float,
            innerPadH: Float
        ) {
            if (text.isBlank()) return
            if (remainingSpace() < 42f) startNewPage()
            val cLabel = canvas ?: return
            cLabel.drawRect(MARGIN_H, cursorY, MARGIN_H + CONTENT_WIDTH, cursorY + 18f, paints.calloutPaint)
            cLabel.drawRect(MARGIN_H, cursorY, MARGIN_H + 3.5f, cursorY + 18f, paints.accentBarPaint)
            cLabel.drawText(label, MARGIN_H + innerPadH, cursorY + 12f, paints.calloutLabel)
            cursorY += 18f

            val layout = buildStaticLayout(text, paint, innerWidth, 1.3f)
            for (line in 0 until layout.lineCount) {
                val lineTop = layout.getLineTop(line).toFloat()
                val lineBottom = layout.getLineBottom(line).toFloat()
                val lineHeight = lineBottom - lineTop
                if (cursorY + lineHeight + 6f > maxBottomY) {
                    startNewPage()
                }
                val c = canvas ?: break
                c.drawRect(MARGIN_H, cursorY, MARGIN_H + CONTENT_WIDTH, cursorY + lineHeight + 2f, paints.calloutPaint)
                c.drawRect(MARGIN_H, cursorY, MARGIN_H + 3.5f, cursorY + lineHeight + 2f, paints.accentBarPaint)
                val lineText = text.substring(layout.getLineStart(line), layout.getLineEnd(line)).trimEnd('\n')
                val baselineOffset = layout.getLineBaseline(line) - layout.getLineTop(line)
                c.drawText(lineText, MARGIN_H + innerPadH, cursorY + baselineOffset, paint)
                cursorY += lineHeight
            }
            cursorY += 6f
        }

        private fun drawStaticWrapped(
            text: String,
            paint: TextPaint,
            width: Float,
            lineSpacingMult: Float = 1.2f
        ) {
            if (text.isBlank()) return
            val layout = buildStaticLayout(text, paint, width, lineSpacingMult)
            for (line in 0 until layout.lineCount) {
                val lineHeight = (layout.getLineBottom(line) - layout.getLineTop(line)).toFloat()
                if (cursorY + lineHeight > maxBottomY) {
                    startNewPage()
                }
                val c = canvas ?: break
                val lineText = text.substring(layout.getLineStart(line), layout.getLineEnd(line)).trimEnd('\n')
                val baselineOffset = layout.getLineBaseline(line) - layout.getLineTop(line)
                c.drawText(lineText, MARGIN_H, cursorY + baselineOffset, paint)
                cursorY += lineHeight
            }
        }

        private fun buildStaticLayout(
            text: String,
            paint: TextPaint,
            width: Float,
            lineSpacingMult: Float
        ): StaticLayout {
            return StaticLayout.Builder
                .obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, lineSpacingMult)
                .setIncludePad(false)
                .build()
        }
    }
}
