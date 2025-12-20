package me.vripper.android.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import me.vripper.android.domain.Image
import me.vripper.android.domain.Post

@Database(entities = [Post::class, Image::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun postDao(): PostDao
    abstract fun imageDao(): ImageDao
}
