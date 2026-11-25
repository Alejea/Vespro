package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.LockMode
import com.example.core.VaultLockManager
import com.example.data.DiaryEntry
import com.example.ui.state.DiaryViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VaultLockManagerTest {

    @Test
    fun `pin lock stores hashed secret and verifies per user`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = VaultLockManager(context)
        val uid = "user_test_1"

        assertEquals(LockMode.NONE, manager.getLockMode(uid))
        assertFalse(manager.isLockEnabled(uid))

        manager.setPin(uid, "2580")
        assertEquals(LockMode.PIN, manager.getLockMode(uid))
        assertTrue(manager.isLockEnabled(uid))
        assertTrue(manager.verifyPin(uid, "2580"))
        assertFalse(manager.verifyPin(uid, "1234"))
        assertFalse(manager.verifyPin("other_user", "2580"))

        manager.disableLock(uid)
        assertEquals(LockMode.NONE, manager.getLockMode(uid))
        assertFalse(manager.verifyPin(uid, "2580"))
    }

    @Test
    fun `pattern lock stores hashed sequence and verifies accurately`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = VaultLockManager(context)
        val uid = "user_test_2"

        manager.setPattern(uid, listOf(0, 1, 4, 7, 8))
        assertEquals(LockMode.PATTERN, manager.getLockMode(uid))
        assertTrue(manager.verifyPattern(uid, listOf(0, 1, 4, 7, 8)))
        assertFalse(manager.verifyPattern(uid, listOf(0, 1, 4, 7)))
    }

    @Test
    fun `computeRecurringStats aggregates themes and sentiments`() {
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
}
