package com.example

import com.example.data.DiaryEntry
import com.example.export.DiaryPdfExporter
import com.example.export.PdfExportScope
import com.example.ui.state.DiaryViewModel
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun computeRecurringStats_aggregatesCorrectly() {
    val entries = listOf(
      DiaryEntry(
        id = "1",
        dateKey = "2026-11-24",
        themes = listOf("lavoro", "camminata"),
        sentimentLabel = "Carico ma lucido",
        sentimentValence = 4L,
        mentalClarity = 4L
      ),
      DiaryEntry(
        id = "2",
        dateKey = "2026-11-25",
        themes = listOf("Lavoro", "riposo"),
        sentimentLabel = "Carico ma lucido",
        sentimentValence = 2L,
        mentalClarity = 4L
      )
    )

    val stats = DiaryViewModel.computeRecurringStats(entries)
    assertEquals(2, stats.totalEntries)
    assertEquals(3.0f, stats.averageValence, 0.01f)
    assertEquals(4.0f, stats.averageClarity, 0.01f)
    assertEquals("lavoro" to 2, stats.topThemes.first())
    assertEquals("Carico ma lucido" to 2, stats.topSentiments.first())
  }

  @Test
  fun selectEntriesForScope_filtersAndSortsChronologically() {
    val nowMs = 1_795_600_000_000L
    val dayMs = 24L * 60L * 60L * 1000L

    val recentEntry = DiaryEntry(
      id = "recent",
      dateKey = "2026-11-24",
      createdAt = Timestamp(Date(nowMs - 2 * dayMs))
    )
    val midEntry = DiaryEntry(
      id = "mid",
      dateKey = "2026-11-10",
      createdAt = Timestamp(Date(nowMs - 16 * dayMs))
    )
    val oldEntry = DiaryEntry(
      id = "old",
      dateKey = "2026-09-01",
      createdAt = Timestamp(Date(nowMs - 85 * dayMs))
    )

    val all = listOf(recentEntry, midEntry, oldEntry)
    val last7 = DiaryPdfExporter.selectEntriesForScope(all, emptyList(), PdfExportScope.LAST_7_DAYS, nowMs)
    val last30 = DiaryPdfExporter.selectEntriesForScope(all, emptyList(), PdfExportScope.LAST_30_DAYS, nowMs)
    val allChrono = DiaryPdfExporter.selectEntriesForScope(all, emptyList(), PdfExportScope.ALL_ENTRIES, nowMs)
    val filtered = DiaryPdfExporter.selectEntriesForScope(all, listOf(recentEntry), PdfExportScope.FILTERED, nowMs)

    assertEquals(listOf("recent"), last7.map { it.id })
    assertEquals(listOf("mid", "recent"), last30.map { it.id })
    assertEquals(listOf("old", "mid", "recent"), allChrono.map { it.id })
    assertEquals(listOf("recent"), filtered.map { it.id })
  }
}
