package com.vurka.emogy.data

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * A small, versioned record of an unfinished level.
 *
 * Only durable facts are stored. Transient UI state (preview, a half-selected pair, running jobs)
 * is intentionally rebuilt on restore, so a snapshot can never resume in an impossible state.
 */
data class GameSnapshot(
    val level: Int,
    val savedAtMillis: Long,
    val cards: List<SnapshotCard>,
    val moves: Int,
    val mistakes: Int,
    val matchedPairs: Int,
    val timeElapsedSec: Int,
    val timeRemainingSec: Int,
    val hintsLeft: Int,
    val combo: Int,
    val maxCombo: Int,
    val shuffleCountdownSec: Int,
    val shuffleCount: Int,
    val wasPaused: Boolean,
    /** Hints used before the snapshot was taken. Missing in v1 saves and defaults to 0. */
    val hintsUsed: Int = 0,
    val version: Int = GameSnapshotCodec.VERSION,
) {
    fun isExpired(nowMillis: Long, windowMillis: Long = RESUME_WINDOW_MILLIS): Boolean =
        nowMillis - savedAtMillis > windowMillis
}

data class SnapshotCard(
    val id: Int,
    val emojiId: String,
    val emoji: String,
    val isMatched: Boolean,
)

/** How long a backgrounded run stays resumable. */
const val RESUME_WINDOW_MILLIS: Long = 24L * 60L * 60L * 1000L

/**
 * Line-oriented, URL-encoded text. It needs no JSON/serialization runtime, survives emoji and
 * variation selectors, and is cheap to write on every settled move.
 */
object GameSnapshotCodec {
    const val VERSION = 2

    fun encode(snapshot: GameSnapshot): String = buildString {
        appendLine("v${snapshot.version}")
        appendLine(
            listOf(snapshot.level, snapshot.savedAtMillis).joinToString("|")
        )
        appendLine(
            listOf(
                snapshot.moves,
                snapshot.mistakes,
                snapshot.matchedPairs,
                snapshot.timeElapsedSec,
                snapshot.timeRemainingSec,
                snapshot.hintsLeft,
                snapshot.combo,
                snapshot.maxCombo,
                snapshot.shuffleCountdownSec,
                snapshot.shuffleCount,
                if (snapshot.wasPaused) 1 else 0,
                snapshot.hintsUsed,
            ).joinToString("|")
        )
        snapshot.cards.forEach { card ->
            appendLine(
                listOf(
                    card.id,
                    if (card.isMatched) 1 else 0,
                    encodeField(card.emojiId),
                    encodeField(card.emoji),
                ).joinToString("|")
            )
        }
    }

    fun decode(raw: String?): GameSnapshot? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val lines = raw.lineSequence().filter { it.isNotEmpty() }.toList()
            if (lines.size < 3) return@runCatching null

            val version = lines[0].removePrefix("v").toIntOrNull() ?: return@runCatching null
            if (version !in 1..VERSION) return@runCatching null

            val head = lines[1].split("|")
            if (head.size != 2) return@runCatching null
            val level = head[0].toIntOrNull() ?: return@runCatching null
            val savedAt = head[1].toLongOrNull() ?: return@runCatching null
            if (level !in 1..LevelCatalog.TOTAL_LEVELS) return@runCatching null

            val body = lines[2].split("|")
            if (body.size != if (version >= 2) 12 else 11) return@runCatching null
            val numbers = body.map { it.toIntOrNull() ?: return@runCatching null }
            val wasPaused = numbers[10] != 0
            val hintsUsed = if (version >= 2) numbers[11].coerceAtLeast(0) else 0

            val cards = lines.drop(3).map { line ->
                val fields = line.split("|")
                if (fields.size != 4) return@runCatching null
                val id = fields[0].toIntOrNull() ?: return@runCatching null
                val matched = fields[1] == "1"
                val emojiId = decodeField(fields[2])
                val emoji = decodeField(fields[3])
                if (emojiId.isBlank() || emoji.isBlank()) return@runCatching null
                SnapshotCard(id = id, emojiId = emojiId, emoji = emoji, isMatched = matched)
            }
            if (cards.isEmpty()) return@runCatching null
            if (cards.map { it.id }.distinct().size != cards.size) return@runCatching null

            GameSnapshot(
                level = level,
                savedAtMillis = savedAt,
                cards = cards,
                moves = numbers[0],
                mistakes = numbers[1],
                matchedPairs = numbers[2],
                timeElapsedSec = numbers[3],
                timeRemainingSec = numbers[4],
                hintsLeft = numbers[5],
                combo = numbers[6],
                maxCombo = numbers[7],
                shuffleCountdownSec = numbers[8],
                shuffleCount = numbers[9],
                wasPaused = wasPaused,
                hintsUsed = hintsUsed,
                version = version,
            )
        }.getOrNull()
    }

    private fun encodeField(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun decodeField(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}
