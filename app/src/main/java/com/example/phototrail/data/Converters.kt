package com.example.phototrail.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromOverrideType(value: OverrideType): String {
        return value.name
    }

    @TypeConverter
    fun toOverrideType(value: String): OverrideType {
        return OverrideType.valueOf(value)
    }
}
