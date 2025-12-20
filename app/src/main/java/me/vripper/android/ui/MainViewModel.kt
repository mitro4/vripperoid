package me.vripper.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.vripper.android.data.ImageDao
import me.vripper.android.data.PostDao
import me.vripper.android.domain.Image
import me.vripper.android.domain.Post
import me.vripper.android.domain.Status
import me.vripper.android.vgapi.ThreadLookupAPIParser
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

class MainViewModel(application: Application) : AndroidViewModel(application), KoinComponent {

    private val postDao: PostDao by inject()
    private val imageDao: ImageDao by inject()
    val posts = postDao.getAll()

    fun addPost(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val threadId = extractThreadId(url)
                if (threadId != null) {
                    val parser = ThreadLookupAPIParser(threadId)
                    val threadItem = parser.parse()
                    
                    threadItem.postItemList.forEach { postItem ->
                        val post = Post(
                            postTitle = postItem.postTitle,
                            threadTitle = postItem.threadTitle,
                            forum = postItem.forum,
                            url = postItem.url,
                            token = postItem.securityToken,
                            vgPostId = postItem.postId,
                            vgThreadId = postItem.threadId,
                            total = postItem.imageCount,
                            hosts = "",
                            downloadDirectory = "VRipper/${postItem.threadId}",
                            addedOn = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).format(Date()),
                            folderName = postItem.threadTitle.replace("[^a-zA-Z0-9.-]", "_"),
                            status = Status.STOPPED
                        )
                        val postId = postDao.insert(post)
                        
                        val images = postItem.imageItemList.mapIndexed { index, imageItem ->
                            Image(
                                url = imageItem.mainUrl,
                                thumbUrl = imageItem.thumbUrl,
                                host = imageItem.host,
                                index = index,
                                postEntityId = postId,
                                status = Status.STOPPED
                            )
                        }
                        imageDao.insertAll(images)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun deletePost(post: Post) {
        viewModelScope.launch(Dispatchers.IO) {
            postDao.delete(post.id)
        }
    }
    
    fun startDownload(post: Post) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedPost = post.copy(status = Status.PENDING)
            postDao.update(updatedPost)
            
            // Also update all images for this post to PENDING
            // Note: In a real app we might want to check if they are already finished
            // But for simplicity here, we assume we want to retry/start all stopped/pending ones.
            // A more complex query in Dao might be better: "UPDATE image SET status = PENDING WHERE postEntityId = :id AND status != FINISHED"
            imageDao.updateStatusByPostId(post.id, Status.PENDING, Status.FINISHED)
        }
    }
    
    private fun extractThreadId(url: String): Long? {
        val pattern = Pattern.compile("threads/(\\d+)")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) {
            matcher.group(1)?.toLong()
        } else {
            null
        }
    }
}
