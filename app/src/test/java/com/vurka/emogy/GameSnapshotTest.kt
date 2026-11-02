package com.vurka.emogy

import com.vurka.emogy.data.GameSnapshot
import com.vurka.emogy.data.GameSnapshotCodec
import com.vurka.emogy.data.RESUME_WINDOW_MILLIS
import com.vurka.emogy.data.SnapshotCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSnapshotTest {

    private fun snapshot() = GameSnapshot(
        level = 42,
        savedAtMillis = 1_700_000_000_000L,
        cards = listOf(
            SnapshotCard(0, "🕷️", "🕷️", isMatched = false),
            SnapshotCard(1, "🕷️", "🕷️", isMatched = false),
            SnapshotCard(2, "👨‍👩‍👧", "👨‍👩‍👧", isMatched = true),
            SnapshotCard(3, "👨‍👩‍👧", "👨‍👩‍👧", isMatched = true),
        ),
        moves = 3,
        mistakes = 1,
        matchedPairs = 1,
        timeElapsedSec = 12,
        timeRemainingSec = 48,
        hintsLeft = 2,
        combo = 1,
        maxCombo = 2,
        shuffleCountdownSec = 17,
        shuffleCount = 4,
        wasPaused = true,
    )

    @Test
    fun roundTripPreservesEveryFieldAndEmojiSequence() {
        val decoded = GameSnapshotCodec.decode(GameSnapshotCodec.encode(snapshot()))
        assertEquals(snapshot(), decoded)
    }

    @Test
    fun nullAndCorruptPayloadsAreRejected() {
        assertNull(GameSnapshotCodec.decode(null))
        assertNull(GameSnapshotCodec.decode(""))
        assertNull(GameSnapshotCodec.decode("not a snapshot"))
        assertNull(GameSnapshotCodec.decode("v99\n42|1\n0|0|0|0|0|0|0|0|0|0|0"))
        assertNull(GameSnapshotCodec.decode("v1\n42|1\n0|0|0|0|0|0|0|0|0|0|0\nbroken"))
    }

    @Test
    fun v1SnapshotsDecodeWithZeroHintsUsed() {
        val v2 = GameSnapshotCodec.encode(snapshot())
        val lines = v2.lines()
        // A v1 body has 11 fields: drop the hintsUsed field appended by v2.
        val v1Body = lines[2].substringBeforeLast("|")
        val raw = listOf(
            lines[0].replace("v${GameSnapshotCodec.VERSION}", "v1"),
            lines[1],
            v1Body,
        ).joinToString("\n") + "\n" + lines.drop(3).joinToString("\n")

        val decoded = GameSnapshotCodec.decode(raw)

        assertEquals(1, decoded?.version)
        assertEquals(0, decoded?.hintsUsed)
        assertEquals(snapshot().cards, decoded?.cards)
        assertEquals(snapshot().moves, decoded?.moves)
    }

    @Test
    fun hintsUsedRoundTripsAndIsClampedWhenNegative() {
        val used = snapshot().copy(hintsUsed = 4)
        val decoded = GameSnapshotCodec.decode(GameSnapshotCodec.encode(used))
        assertEquals(4, decoded?.hintsUsed)

        val negative = GameSnapshotCodec.encode(used).replace("|4\n", "|-4\n")
        assertEquals(0, GameSnapshotCodec.decode(negative)?.hintsUsed)
    }

    @Test
    fun snapshotExpiresAfterTheResumeWindow() {
        val saved = snapshot()
        assertFalse(saved.isExpired(saved.savedAtMillis + RESUME_WINDOW_MILLIS))
        assertTrue(saved.isExpired(saved.savedAtMillis + RESUME_WINDOW_MILLIS + 1))
        // A clock moved backwards must not destroy an otherwise valid run.
        assertFalse(saved.isExpired(saved.savedAtMillis - 60_000))
    }
}
