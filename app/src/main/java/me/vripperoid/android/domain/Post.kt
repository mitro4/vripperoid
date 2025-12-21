package me.vripperoid.android.domain

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "post")
data class Post(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val postTitle: String,
    val threadTitle: String,
    val forum: String,
    val url: String,
    val token: String,
    val vgPostId: Long,
    val vgThreadId: Long,
    val total: Int,
    val hosts: String, // Comma separated
    val downloadDirectory: String,
    val addedOn: String, // ISO8601
    var folderName: String,
    var status: Status = Status.STOPPED,
    var done: Int = 0,
    var size: Long = -1,
    var downloaded: Int = 0,
    val previewUrls: List<String> = emptyList()
)
