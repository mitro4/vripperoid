package me.vripper.android.data

import androidx.room.TypeConverter
import me.vripper.android.domain.Status

class Converters {
    @TypeConverter
    fun fromStatus(status: Status): String {
        return status.name
    }

    @TypeConverter
    fun toStatus(value: String): Status {
        return Status.valueOf(value)
    }
}
