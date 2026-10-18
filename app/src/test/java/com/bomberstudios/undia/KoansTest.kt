package com.bomberstudios.undia

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KoansTest {
    @Test fun `enough koans for a leap year without a repeat`() {
        assertTrue("only ${Koans.all.size} koans", Koans.all.size > 366)
    }

    @Test fun `no duplicate koans`() {
        assertEquals(emptyList<String>(), Koans.all.groupBy { it }.filter { it.value.size > 1 }.keys.toList())
    }

    @Test fun `every day of a leap year gets a different koan`() {
        val start = LocalDate.of(2028, 1, 1)
        val shown = (0 until 366).map { Koans.forDay(start.plusDays(it.toLong())) }
        assertEquals(366, shown.toSet().size)
    }

    @Test fun `the same day always gets the same koan`() {
        val d = LocalDate.of(2026, 8, 19)
        assertEquals(Koans.forDay(d), Koans.forDay(d))
    }

    @Test fun `the birthday is pinned`() {
        assertEquals(
            "A man asked the sea for directions. The sea said: yes.",
            Koans.forDay(LocalDate.of(2026, 8, 19)),
        )
    }

    @Test fun `days before the epoch still work`() {
        assertTrue(Koans.forDay(LocalDate.of(1960, 1, 1)).isNotBlank())
    }

    @Test fun `day placeholder becomes the weekday and never leaks`() {
        val withDay = Koans.all.first { "{day}" in it }
        val monday = generateSequence(LocalDate.of(2026, 1, 5)) { it.plusDays(7) }
            .first { d -> Koans.forDay(d) != withDay && Koans.all[Math.floorMod(d.toEpochDay() + 89L, Koans.all.size.toLong()).toInt()] == withDay }
        assertTrue("Monday" in Koans.forDay(monday))
        for (i in 0 until Koans.all.size) {
            assertFalse(Koans.forDay(LocalDate.of(2027, 1, 1).plusDays(i.toLong())).contains("{day}"))
        }
    }

    @Test fun `typography is clean`() {
        for (k in Koans.all) {
            assertFalse("straight quote in: $k", '"' in k || '\'' in k)
            assertEquals("untrimmed: $k", k.trim(), k)
            assertFalse("double space in: $k", "  " in k)
        }
    }
}
