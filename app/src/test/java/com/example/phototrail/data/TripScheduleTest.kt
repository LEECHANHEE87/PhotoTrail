package com.example.phototrail.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripScheduleTest {
    @Test
    fun `date range includes start and end dates`() {
        assertEquals(
            listOf("2026-10-04", "2026-10-05", "2026-10-06", "2026-10-07"),
            buildInclusiveDateKeys("2026-10-04", "2026-10-07")
        )
    }

    @Test
    fun `invalid or reversed range is rejected`() {
        assertTrue(buildInclusiveDateKeys("2026-10-07", "2026-10-06").isEmpty())
        assertTrue(buildInclusiveDateKeys("not-a-date", "2026-10-06").isEmpty())
    }

    @Test
    fun `generated titles use encoding safe Korean labels`() {
        assertEquals(
            "2026-10-12 \uD558\uB8E8 \uAE30\uB85D",
            buildGeneratedTripTitle("2026-10-12", "2026-10-12")
        )
        assertEquals(
            "2026-10-10 ~ 2026-10-11 \uBD84\uB9AC \uAE30\uB85D",
            buildSplitTripTitle("2026-10-10", "2026-10-11")
        )
        assertEquals(
            "2026-10-03 ~ 2026-10-05 \uBCD1\uD569 \uC5EC\uD589 \uAE30\uB85D",
            buildMergedTripTitle("2026-10-03", "2026-10-05")
        )
    }
}
