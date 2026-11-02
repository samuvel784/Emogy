package com.vurka.emogy.data

import kotlin.math.abs

/** A recorded change in the player's coin balance. */
data class CoinTransaction(
    val title: String,
    val amount: Int,
    val timestampMillis: Long,
) {
    val isCredit: Boolean get() = amount > 0
    val formattedAmount: String get() = if (amount > 0) "+$amount" else "$amount"
}

/**
 * Lightweight, line-based storage codec for [CoinTransaction].
 *
 * Keeps up to [MAX_TRANSACTIONS] entries in DataStore preferences.
 */
object CoinTransactionCodec {
    private const val FIELD = "|"
    const val MAX_TRANSACTIONS = 35

    fun encode(transactions: List<CoinTransaction>): String =
        transactions.take(MAX_TRANSACTIONS).joinToString("\n") {
            listOf(it.title, it.amount, it.timestampMillis).joinToString(FIELD)
        }

    fun decode(raw: String?): List<CoinTransaction> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lines().mapNotNull { line ->
            val parts = line.split(FIELD)
            if (parts.size >= 3) {
                val title = parts[0]
                val amount = parts[1].toIntOrNull()
                val time = parts[2].toLongOrNull()
                if (title.isNotBlank() && amount != null && time != null) {
                    CoinTransaction(title, amount, time)
                } else null
            } else null
        }
    }
}
