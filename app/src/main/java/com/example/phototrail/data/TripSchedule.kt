package com.example.phototrail.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val DAY_RECORD = "\uD558\uB8E8 \uAE30\uB85D"
private const val TRIP_RECORD = "\uC5EC\uD589 \uAE30\uB85D"
private const val MERGED_TRIP_RECORD = "\uBCD1\uD569 \uC5EC\uD589 \uAE30\uB85D"
private const val SPLIT_RECORD = "\uBD84\uB9AC \uAE30\uB85D"

internal fun buildInclusiveDateKeys(startDateKey: String, endDateKey: String): List<String> {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    val start = runCatching { format.parse(startDateKey) }.getOrNull() ?: return emptyList()
    val end = runCatching { format.parse(endDateKey) }.getOrNull() ?: return emptyList()
    if (start.after(end)) return emptyList()

    val calendar = Calendar.getInstance().apply { time = start }
    return buildList {
        while (!calendar.time.after(end)) {
            add(format.format(calendar.time))
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
    }
}

internal fun buildGeneratedTripTitle(startDateKey: String, endDateKey: String): String =
    if (startDateKey == endDateKey) "$startDateKey $DAY_RECORD"
    else "$startDateKey ~ $endDateKey $TRIP_RECORD"

internal fun buildMergedTripTitle(startDateKey: String, endDateKey: String): String =
    "$startDateKey ~ $endDateKey $MERGED_TRIP_RECORD"

internal fun buildSplitTripTitle(startDateKey: String, endDateKey: String): String =
    if (startDateKey == endDateKey) "$startDateKey $SPLIT_RECORD"
    else "$startDateKey ~ $endDateKey $SPLIT_RECORD"
