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
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.DocumentsContract
import java.io.File
import android.net.Uri
import me.vripper.android.settings.SettingsStore

class MainViewModel(application: Application) : AndroidViewModel(application), KoinComponent {

    private val postDao: PostDao by inject()
    private val imageDao: ImageDao by inject()
    private val settingsStore: SettingsStore by inject()
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
                            status = Status.STOPPED,
                            previewUrls = postItem.imageItemList.take(4).map { it.thumbUrl }
                        )
                        
                        // Check if a folder with this name already exists in DB to append post ID
                        // This logic should be better handled, but for now we will rely on PostDao returning the ID
                        // and we can update folderName if needed, but the requirement says "if in the thread multiple posts with images"
                        // which implies we might want to check existing posts for this thread.
                        
                        // Let's first insert to get ID
                        val postId = postDao.insert(post)
                        
                        // Check if there are other posts for this thread
                        val count = postDao.countByThreadId(postItem.threadId)
                        val finalFolderName = if (count > 1) {
                             "${post.folderName}_${postItem.postId}"
                        } else {
                             post.folderName
                        }
                        
                        // Update post with final folder name if changed
                        if (finalFolderName != post.folderName) {
                            val updatedPost = post.copy(id = postId, folderName = finalFolderName)
                            postDao.update(updatedPost)
                        }
                        
                        // Check if folder already exists on disk
                        val folderExists = checkFolderExists(finalFolderName)
                        val initialStatus = if (folderExists) Status.ALREADY_DOWNLOADED else Status.STOPPED
                        val initialDownloaded = if (folderExists) post.total else 0
                        
                        if (folderExists) {
                             val updatedPost = post.copy(
                                 id = postId, 
                                 folderName = finalFolderName, 
                                 status = Status.ALREADY_DOWNLOADED,
                                 downloaded = post.total
                             )
                             postDao.update(updatedPost)
                        }
                        
                        val images = postItem.imageItemList.mapIndexed { index, imageItem ->
                            Image(
                                url = imageItem.mainUrl,
                                thumbUrl = imageItem.thumbUrl,
                                host = imageItem.host,
                                index = index,
                                postEntityId = postId,
                                status = if (folderExists) Status.ALREADY_DOWNLOADED else Status.STOPPED
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
        val pattern = java.util.regex.Pattern.compile("threads/(\\d+)")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) {
            matcher.group(1)?.toLong()
        } else {
            null
        }
    }

    private fun checkFolderExists(folderName: String): Boolean {
        val context = getApplication<Application>()
        val customUri = settingsStore.downloadPathUri
        if (customUri != null) {
            try {
                val treeUri = Uri.parse(customUri)
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                
                // We need to check if folderName exists as a child of root
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
                
                context.contentResolver.query(
                    childrenUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null, null, null
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            val name = cursor.getString(nameIndex)
                            // folderName passed here is usually just the thread name for check
                            if (name == folderName) {
                                return true
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%VRipper/$folderName/%")
            
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                return cursor.count > 0
            }
            return false
        } else {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val targetDir = File(picturesDir, "VRipper/$folderName")
            return targetDir.exists()
        }
    }
}
