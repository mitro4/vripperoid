package me.vripperoid.android.data

import androidx.room.TypeConverter
import me.vripperoid.android.domain.Status

class Converters {
    @TypeConverter
    fun fromStatus(status: Status): String {
        return status.name
    }

    @TypeConverter
    fun toStatus(value: String): Status {
        return Status.valueOf(value)
    }

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return value.joinToString(",")
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return if (value.isEmpty()) emptyList() else value.split(",")
    }
}
