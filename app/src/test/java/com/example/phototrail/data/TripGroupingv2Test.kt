package com.example.phototrail.data

import org.junit.Assert.*
import org.junit.Test

class TripGroupingv2Test {

    private val generator = TripAlbumGeneratorV2()

    private fun createDay(
        date: String,
        photos: Int = 10,
        places: Int = 2,
        center: Pair<Double, Double>? = 37.5665 to 126.9780, // Seoul
        first: Pair<Double, Double>? = null,
        last: Pair<Double, Double>? = null,
        placeCenters: List<Pair<Double, Double>> = emptyList()
    ) = DaySummary(
        dateKey = date,
        totalCount = photos,
        locationCount = if (center != null) photos else 0,
        noLocationCount = if (center != null) 0 else photos,
        placeGroupCount = places,
        centerLat = center?.first,
        centerLng = center?.second,
        firstLat = first?.first ?: center?.first,
        firstLng = first?.second ?: center?.second,
        lastLat = last?.first ?: center?.first,
        lastLng = last?.second ?: center?.second,
        startTime = 0L,
        endTime = 0L,
        representativeUri = null,
        placeGroupCenters = placeCenters
    )

    @Test
    fun testSameLocation3Days() {
        val days = listOf(
            createDay("2026-06-10"),
            createDay("2026-06-11"),
            createDay("2026-06-12")
        )
        val result = generator.groupDays(days)
        assertEquals(1, result.size)
        assertEquals(3, result[0].size)
    }

    @Test
    fun testSeoulThenBusan() {
        val days = listOf(
            createDay("2026-06-10", center = 37.5665 to 126.9780), // Seoul
            createDay("2026-06-11", center = 35.1796 to 129.0756)  // Busan (~325km)
        )
        val result = generator.groupDays(days)
        assertEquals(2, result.size)
    }

    @Test
    fun testMax7DaysLimit() {
        val days = (10..20).map { createDay("2026-06-$it") }
        val result = generator.groupDays(days)
        // 10~16 (7 days), 17~20 (4 days) -> 2 trips
        // Wait, diffDays = (currDate.time - firstDate.time) / 1day
        // 16 - 10 = 6 (7th day), 17 - 10 = 7. 
        // My code: if (diffDays >= 7) return true (force split)
        // Day 10: trip=[10]
        // Day 11: diff=1, ok, trip=[10,11]
        // ...
        // Day 16: diff=6, ok, trip=[10..16]
        // Day 17: diff=7, split! trip=[10..16], newTrip=[17]
        assertEquals(2, result.size)
        assertEquals(7, result[0].size)
    }

    @Test
    fun testNoGpsDay() {
        val days = listOf(
            createDay("2026-06-10", center = 37.5665 to 126.9780),
            createDay("2026-06-11", center = null, photos = 20), // No GPS
            createDay("2026-06-12", center = 37.5665 to 126.9780)
        )
        val result = generator.groupDays(days)
        // Should split between 10 and 11, and between 11 and 12
        assertEquals(3, result.size)
    }

    @Test
    fun testNonCandidateDay() {
        val days = listOf(
            createDay("2026-06-10"),
            createDay("2026-06-11", photos = 2, places = 1), // Not a candidate
            createDay("2026-06-12")
        )
        val result = generator.groupDays(days)
        // 2026-06-11 is filtered out. 10 and 12 are not consecutive.
        assertEquals(2, result.size)
        assertEquals("2026-06-10", result[0][0].dateKey)
        assertEquals("2026-06-12", result[1][0].dateKey)
    }

    @Test
    fun testEdgeDistanceMerge() {
        // Centers are far (>50km), but last of day1 and first of day2 are close (<30km)
        val days = listOf(
            createDay("2026-06-10", center = 37.5 to 127.0, last = 37.2 to 127.2),
            createDay("2026-06-11", center = 36.8 to 127.5, first = 37.1 to 127.3)
        )
        // Distance between (37.2, 127.2) and (37.1, 127.3)
        // Roughly 0.1 deg lat (~11km) + 0.1 deg lng (~9km) = ~14km. Should merge.
        val result = generator.groupDays(days)
        assertEquals(1, result.size)
    }

    @Test
    fun testGapInDates() {
        val days = listOf(
            createDay("2026-06-10"),
            createDay("2026-06-12") // Gap on 11th
        )
        val result = generator.groupDays(days)
        assertEquals(2, result.size)
    }
}
