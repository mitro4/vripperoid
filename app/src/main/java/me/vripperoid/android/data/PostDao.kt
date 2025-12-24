package me.vripperoid.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.vripperoid.android.domain.Post

@Dao
interface PostDao {
    @Query("SELECT * FROM post ORDER BY addedOn ASC, id ASC")
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

    @Query("SELECT COUNT(*) FROM post WHERE vgPostId = :vgPostId")
    suspend fun countByVgPostId(vgPostId: Long): Int

    @Query("UPDATE post SET status = 'PENDING' WHERE status = 'DOWNLOADING'")
    suspend fun resetAllDownloadingToPending()
}
