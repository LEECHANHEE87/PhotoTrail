package com.example.phototrail.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

object TripGroupingConfig {
    const val MIN_DAY_PHOTO_COUNT = 10
    const val MIN_DAY_PLACE_GROUP_COUNT = 2
    
    const val MERGE_CENTER_DISTANCE_KM = 50.0
    const val MERGE_EDGE_DISTANCE_KM = 30.0
    const val MERGE_NEAREST_PLACE_DISTANCE_KM = 10.0
    
    const val FORCE_SPLIT_DISTANCE_KM = 150.0
    const val MAX_AUTO_TRIP_DAYS = 7
}

data class DaySummary(
    val dateKey: String,
    val totalCount: Int,
    val locationCount: Int,
    val noLocationCount: Int,
    val placeGroupCount: Int,
    val centerLat: Double?,
    val centerLng: Double?,
    val firstLat: Double?,
    val firstLng: Double?,
    val lastLat: Double?,
    val lastLng: Double?,
    val startTime: Long,
    val endTime: Long,
    val representativeUri: String?,
    val placeGroupCenters: List<Pair<Double, Double>> = emptyList()
)

class TripAlbumGeneratorV2 {
    
    private val dateKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun groupDays(days: List<DaySummary>): List<List<DaySummary>> {
        if (days.isEmpty()) return emptyList()
        
        val candidates = days.filter { isCandidate(it) }.sortedBy { it.dateKey }
        if (candidates.isEmpty()) return emptyList()
        
        val trips = mutableListOf<MutableList<DaySummary>>()
        var currentTrip = mutableListOf(candidates[0])
        trips.add(currentTrip)
        
        for (i in 1 until candidates.size) {
            val prevDay = currentTrip.last()
            val currDay = candidates[i]
            
            if (shouldMerge(currentTrip, prevDay, currDay)) {
                currentTrip.add(currDay)
            } else {
                currentTrip = mutableListOf(currDay)
                trips.add(currentTrip)
            }
        }
        
        return trips
    }

    private fun isCandidate(day: DaySummary): Boolean {
        return day.totalCount >= TripGroupingConfig.MIN_DAY_PHOTO_COUNT || 
               day.placeGroupCount >= TripGroupingConfig.MIN_DAY_PLACE_GROUP_COUNT
    }

    private fun shouldMerge(currentTrip: List<DaySummary>, prevDay: DaySummary, currDay: DaySummary): Boolean {
        // 1. Check if consecutive
        if (!isConsecutive(prevDay.dateKey, currDay.dateKey)) return false
        
        // 2. Check force split conditions
        if (isForceSplit(currentTrip, prevDay, currDay)) return false
        
        // 3. Check location continuity
        return hasLocationContinuity(prevDay, currDay)
    }

    private fun isForceSplit(currentTrip: List<DaySummary>, prevDay: DaySummary, currDay: DaySummary): Boolean {
        // Center distance > 150km
        if (prevDay.centerLat != null && prevDay.centerLng != null && 
            currDay.centerLat != null && currDay.centerLng != null) {
            val dist = calculateDistanceKm(prevDay.centerLat, prevDay.centerLng, currDay.centerLat, currDay.centerLng)
            if (dist >= TripGroupingConfig.FORCE_SPLIT_DISTANCE_KM) return true
        }
        
        // Total duration > 7 days
        val firstDate = dateKeyFormat.parse(currentTrip.first().dateKey)
        val currDate = dateKeyFormat.parse(currDay.dateKey)
        if (firstDate != null && currDate != null) {
            val diffMs = currDate.time - firstDate.time
            val diffDays = diffMs / (24 * 60 * 60 * 1000L)
            if (diffDays >= TripGroupingConfig.MAX_AUTO_TRIP_DAYS) return true
        }

        // TODO: 이전 날짜와 다음 날짜 사이에 사진이 없는 날짜가 존재함 (이미 isCandidate에서 걸러지므로 
        // 실제 날짜 차이가 1일보다 크면 위 isConsecutive에서 false 반환됨)
        
        return false
    }

    private fun hasLocationContinuity(prevDay: DaySummary, currDay: DaySummary): Boolean {
        // One of them has no location info - Conservative split
        if (prevDay.locationCount == 0 || currDay.locationCount == 0) return false
        
        // Condition A: Center distance <= 50km
        if (prevDay.centerLat != null && prevDay.centerLng != null && 
            currDay.centerLat != null && currDay.centerLng != null) {
            val dist = calculateDistanceKm(prevDay.centerLat, prevDay.centerLng, currDay.centerLat, currDay.centerLng)
            if (dist <= TripGroupingConfig.MERGE_CENTER_DISTANCE_KM) return true
        }
        
        // Condition B: Edge distance (prev last to curr first) <= 30km
        if (prevDay.lastLat != null && prevDay.lastLng != null && 
            currDay.firstLat != null && currDay.firstLng != null) {
            val dist = calculateDistanceKm(prevDay.lastLat, prevDay.lastLng, currDay.firstLat, currDay.firstLng)
            if (dist <= TripGroupingConfig.MERGE_EDGE_DISTANCE_KM) return true
        }
        
        // Condition C: Nearest place group distance <= 10km
        for (prevPlace in prevDay.placeGroupCenters) {
            for (currPlace in currDay.placeGroupCenters) {
                val dist = calculateDistanceKm(prevPlace.first, prevPlace.second, currPlace.first, currPlace.second)
                if (dist <= TripGroupingConfig.MERGE_NEAREST_PLACE_DISTANCE_KM) return true
            }
        }
        
        return false
    }

    private fun isConsecutive(date1: String, date2: String): Boolean {
        return try {
            val d1 = dateKeyFormat.parse(date1)!!
            val d2 = dateKeyFormat.parse(date2)!!
            val diff = d2.time - d1.time
            // Allow up to 1.5 days to be safe with timezones/precision, 
            // but strict 1 day is usually better for "consecutive".
            // The instruction says "정확히 하루 차이로 연속되어야 한다".
            val oneDayMs = 24 * 60 * 60 * 1000L
            diff == oneDayMs
        } catch (e: Exception) {
            false
        }
    }

    fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
