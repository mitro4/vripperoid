package me.vripper.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.vripper.android.domain.Image
import me.vripper.android.domain.Status

@Dao
interface ImageDao {
    @Query("SELECT * FROM image WHERE postEntityId = :postId")
    fun getByPostId(postId: Long): Flow<List<Image>>

    @Query("SELECT * FROM image WHERE postEntityId = :postId")
    suspend fun getByPostIdSync(postId: Long): List<Image>

    @Query("SELECT * FROM image WHERE status = :status LIMIT 1")
    suspend fun getNextPending(status: Status = Status.PENDING): Image?

    @Insert
    suspend fun insertAll(images: List<Image>)

    @Update
    suspend fun update(image: Image)

    @Query("UPDATE image SET status = :newStatus WHERE postEntityId = :postId AND status != :excludeStatus")
    suspend fun updateStatusByPostId(postId: Long, newStatus: Status, excludeStatus: Status)
}
