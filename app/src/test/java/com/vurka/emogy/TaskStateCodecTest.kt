package com.vurka.emogy

import com.vurka.emogy.data.tasks.TaskConfig
import com.vurka.emogy.data.tasks.TaskEngine
import com.vurka.emogy.data.tasks.TaskInstance
import com.vurka.emogy.data.tasks.TaskPeriod
import com.vurka.emogy.data.tasks.TaskState
import com.vurka.emogy.data.tasks.TaskStateCodec
import com.vurka.emogy.data.time.GameCalendar
import com.vurka.emogy.data.time.TaskClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TaskStateCodecTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val now = Calendar.getInstance(utc).apply {
        clear()
        set(2024, Calendar.JUNE, 5, 12, 0, 0)
    }.timeInMillis
    private val calendar = GameCalendar(TaskClock { now }, utc)
    private val engine = TaskEngine(calendar)

    @Test
    fun `round trip preserves progress claims and distinct sets`() {
        val base = engine.ensureCurrent(TaskState(), now)
        val daily = base.daily!!.copy(
            instances = base.daily.instances.mapIndexed { index, instance ->
                when (index) {
                    0 -> instance.copy(
                        progress = 2,
                        distinct = setOf("1", "2"),
                        completedAtMillis = 1_000L,
                        claimedAtMillis = 2_000L,
                    )
                    1 -> instance.copy(progress = 1)
                    else -> instance
                }
            },
        )
        val state = base.copy(daily = daily)

        val decoded = TaskStateCodec.decode(TaskStateCodec.encode(state))

        assertEquals(state, decoded)
    }

    @Test
    fun `unknown task ids are dropped instead of failing the whole save`() {
        val raw = buildString {
            appendLine("v${TaskStateCodec.VERSION}")
            appendLine("P|DAILY|D1|1|0")
            appendLine("T|removed_task|1|0|0|")
            appendLine("T|d_clear_2|1|0|0|")
        }

        val decoded = TaskStateCodec.decode(raw)

        assertTrue(decoded != null)
        val ids = decoded!!.daily?.instances?.map { it.definition.id }.orEmpty()
        assertEquals(listOf("d_clear_2"), ids)
    }

    @Test
    fun `progress is clamped to the configured target`() {
        val raw = buildString {
            appendLine("v${TaskStateCodec.VERSION}")
            appendLine("P|DAILY|D1|1|0")
            appendLine("T|d_clear_2|999|0|0|")
        }

        val decoded = TaskStateCodec.decode(raw)!!
        val instance = decoded.daily!!.instance("d_clear_2")!!

        assertEquals(instance.definition.target, instance.progress)
        assertTrue(instance.isComplete)
    }

    @Test
    fun `distinct sets survive encoding`() {
        val raw = buildString {
            appendLine("v${TaskStateCodec.VERSION}")
            appendLine("P|DAILY|D1|1|0")
            appendLine("T|d_distinct_2|2|0|0|7\u001F8")
        }

        val decoded = TaskStateCodec.decode(raw)!!

        assertEquals(setOf("7", "8"), decoded.daily!!.instance("d_distinct_2")!!.distinct)
    }

    @Test
    fun `unknown versions and garbage are rejected without crashing`() {
        assertNull(TaskStateCodec.decode("v99\nP|DAILY|D1|1|0"))
        assertNull(TaskStateCodec.decode("not a task file"))
        assertNull(TaskStateCodec.decode(""))
        assertNull(TaskStateCodec.decode(null))
    }

    @Test
    fun `malformed period line is rejected`() {
        assertNull(TaskStateCodec.decode("v1\nP|DAILY|D1"))
        assertNull(TaskStateCodec.decode("v1\nP|FORTNIGHTLY|D1|1|0"))
    }

    @Test
    fun `every configured definition is resolvable by id`() {
        TaskConfig.all.forEach { definition ->
            assertEquals(definition, TaskConfig.definition(definition.id))
        }
        assertTrue(TaskConfig.all.map { it.id }.toSet().size == TaskConfig.all.size)
    }

    @Test
    fun `generated monthly set is reproducible from its key`() {
        val first = TaskConfig.generate(TaskPeriod.MONTHLY, "M2024-06", 1L)
        val second = TaskConfig.generate(TaskPeriod.MONTHLY, "M2024-06", 1L)

        assertEquals(
            first.instances.map { it.definition.id },
            second.instances.map { it.definition.id },
        )
        assertEquals(5, first.instances.size)
        assertTrue(first.instances.all { it is TaskInstance && it.progress == 0 })
    }
}
