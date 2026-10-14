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
}
