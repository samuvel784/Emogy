package com.vurka.emogy.data.tasks

/**
 * Versioned, line-oriented storage for [TaskState].
 *
 * Same style as [com.vurka.emogy.data.GameSnapshotCodec]: no JSON dependency, tolerant of older
 * payloads and of definitions that no longer exist. Unknown task ids are dropped, which is safe
 * because a reward can only be granted while its definition is present in [TaskConfig].
 */
object TaskStateCodec {
    const val VERSION = 1

    private const val FIELD = "|"
    private const val DISTINCT_SEPARATOR = "\u001F"

    fun encode(state: TaskState): String = buildString {
        appendLine("v$VERSION")
        listOfNotNull(state.daily, state.weekly, state.monthly).forEach { period ->
            appendLine(
                listOf(
                    "P",
                    period.period.name,
                    period.key,
                    period.startEpochDay,
                    if (period.milestoneClaimed) 1 else 0,
                ).joinToString(FIELD),
            )
            period.instances.forEach { instance ->
                appendLine(
                    listOf(
                        "T",
                        instance.definition.id,
                        instance.progress,
                        instance.completedAtMillis,
                        instance.claimedAtMillis,
                        instance.distinct.joinToString(DISTINCT_SEPARATOR),
                    ).joinToString(FIELD),
                )
            }
        }
    }

    fun decode(raw: String?): TaskState? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val lines = raw.lineSequence().filter { it.isNotBlank() }.toList()
            if (lines.isEmpty()) return@runCatching null
            val version = lines.first().removePrefix("v").toIntOrNull() ?: return@runCatching null
            if (version != VERSION) return@runCatching null

            var current: TaskPeriodState? = null
            val periods = mutableListOf<TaskPeriodState>()
            val instances = mutableListOf<TaskInstance>()

            fun flush() {
                current?.let { periods += it.copy(instances = instances.toList()) }
                instances.clear()
            }

            for (line in lines.drop(1)) {
                val fields = line.split(FIELD)
                when (fields.firstOrNull()) {
                    "P" -> {
                        flush()
                        if (fields.size != 5) return@runCatching null
                        val period = runCatching { TaskPeriod.valueOf(fields[1]) }.getOrNull()
                            ?: return@runCatching null
                        current = TaskPeriodState(
                            period = period,
                            key = fields[2],
                            startEpochDay = fields[3].toLongOrNull() ?: return@runCatching null,
                            milestoneClaimed = fields[4] == "1",
                        )
                    }
                    "T" -> {
                        if (fields.size != 6 || current == null) return@runCatching null
                        val definition = TaskConfig.definition(fields[1]) ?: continue
                        val progress = fields[2].toIntOrNull() ?: return@runCatching null
                        val completed = fields[3].toLongOrNull() ?: return@runCatching null
                        val claimed = fields[4].toLongOrNull() ?: return@runCatching null
                        val distinct = if (fields[5].isEmpty()) {
                            emptySet()
                        } else {
                            fields[5].split(DISTINCT_SEPARATOR).filter { it.isNotEmpty() }.toSet()
                        }
                        instances += TaskInstance(
                            definition = definition,
                            progress = progress.coerceIn(0, definition.target),
                            distinct = distinct,
                            completedAtMillis = completed,
                            claimedAtMillis = claimed,
                        )
                    }
                    else -> return@runCatching null
                }
            }
            flush()

            val byPeriod = periods.associateBy { it.period }
            TaskState(
                daily = byPeriod[TaskPeriod.DAILY],
                weekly = byPeriod[TaskPeriod.WEEKLY],
                monthly = byPeriod[TaskPeriod.MONTHLY],
            )
        }.getOrNull()
    }
}
