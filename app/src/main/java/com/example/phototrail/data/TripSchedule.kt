package com.example.phototrail.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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
