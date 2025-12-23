package me.vripperoid.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.vripperoid.android.domain.Image
import me.vripperoid.android.domain.Status

@Dao
interface ImageDao {
    @Query("SELECT * FROM image WHERE postEntityId = :postId")
    fun getByPostId(postId: Long): Flow<List<Image>>

    @Query("SELECT * FROM image WHERE postEntityId = :postId")
    suspend fun getByPostIdSync(postId: Long): List<Image>

    @Query("SELECT * FROM image WHERE status = :status ORDER BY postEntityId ASC, id ASC LIMIT 1")
    suspend fun getNextPending(status: Status = Status.PENDING): Image?

    @Query("SELECT * FROM image WHERE status = :status AND postEntityId IN (:postIds) ORDER BY postEntityId ASC, id ASC LIMIT 1")
    suspend fun getNextPendingForActivePosts(postIds: List<Long>, status: Status = Status.PENDING): Image?

    @Query("SELECT DISTINCT postEntityId FROM image WHERE status = 'DOWNLOADING'")
    suspend fun getActiveDownloadPostIds(): List<Long>
    
    @Query("SELECT COUNT(*) FROM image WHERE postEntityId = :postId AND status = 'DOWNLOADING'")
    suspend fun countDownloadingByPostId(postId: Long): Int

    @Insert
    suspend fun insertAll(images: List<Image>)

    @Update
    suspend fun update(image: Image)

    @Query("UPDATE image SET status = :newStatus WHERE postEntityId = :postId AND status != :excludeStatus")
    suspend fun updateStatusByPostId(postId: Long, newStatus: Status, excludeStatus: Status)

    @Query("SELECT COUNT(*) FROM image WHERE postEntityId = :postId AND status = 'PENDING'")
    suspend fun countPendingByPostId(postId: Long): Int

    @Query("SELECT COUNT(*) FROM image WHERE postEntityId = :postId AND status = 'ERROR'")
    suspend fun countErrorByPostId(postId: Long): Int
}
