package me.vripper.android.domain

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "image",
    foreignKeys = [ForeignKey(
        entity = Post::class,
        parentColumns = ["id"],
        childColumns = ["postEntityId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["postEntityId"])]
)
data class Image(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val thumbUrl: String,
    val host: Byte,
    val index: Int,
    val postEntityId: Long,
    var size: Long = -1,
    var downloaded: Long = 0,
    var status: Status = Status.STOPPED,
    var filename: String = "",
)
