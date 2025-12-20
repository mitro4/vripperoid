package me.vripper.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.vripper.android.domain.Post

@Dao
interface PostDao {
    @Query("SELECT * FROM post ORDER BY addedOn DESC")
    fun getAll(): Flow<List<Post>>

    @Query("UPDATE post SET downloaded = downloaded + 1 WHERE id = :postId")
    suspend fun incrementDownloaded(postId: Long)

    @Query("SELECT * FROM post WHERE id = :id")
    suspend fun getById(id: Long): Post?

    @Insert
    suspend fun insert(post: Post): Long

    @Update
    suspend fun update(post: Post)

    @Query("DELETE FROM post WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM post WHERE vgThreadId = :threadId")
    suspend fun countByThreadId(threadId: Long): Int
}
