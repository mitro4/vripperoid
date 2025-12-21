package me.vripperoid.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.vripperoid.android.data.ImageDao
import me.vripperoid.android.data.PostDao
import me.vripperoid.android.domain.Image
import me.vripperoid.android.domain.Post
import me.vripperoid.android.domain.Status
import me.vripperoid.android.vgapi.ThreadLookupAPIParser
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
import me.vripperoid.android.settings.SettingsStore

class MainViewModel(application: Application) : AndroidViewModel(application), KoinComponent {

    private val postDao: PostDao by inject()
    private val imageDao: ImageDao by inject()
    private val settingsStore: SettingsStore by inject()
    val posts = postDao.getAll()

    fun addPost(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val threadId = extractThreadId(url)
                val host = extractHost(url) ?: "https://vipergirls.to"
                if (threadId != null) {
                    val parser = ThreadLookupAPIParser(threadId, host)
                    val threadItem = parser.parse()
                    
                    val isBatchMultiple = threadItem.postItemList.size > 1

                    threadItem.postItemList.forEach { postItem ->
                        if (postDao.countByVgPostId(postItem.postId) > 0) return@forEach

                        val existingCount = postDao.countByThreadId(postItem.threadId)
                        val forceSuffix = isBatchMultiple || existingCount > 0

                        var folderName = postItem.threadTitle.replace("[^a-zA-Z0-9.-]", "_")
                        if (forceSuffix) {
                            folderName += "_${postItem.postId}"
                        }
                        
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
                            folderName = folderName,
                            status = Status.STOPPED,
                            previewUrls = postItem.imageItemList.take(4).map { it.thumbUrl }
                        )
                        
                        val postId = postDao.insert(post)
                        
                        // Check if folder already exists on disk
                        val folderExists = checkFolderExists(folderName)
                        // ... rest of logic
                        if (folderExists) {
                             val updatedPost = post.copy(
                                 id = postId, 
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
            if (settingsStore.deleteFromStorage) {
                deletePostFiles(post)
            }
            postDao.delete(post.id)
        }
    }

    private fun deletePostFiles(post: Post) {
        val context = getApplication<Application>()
        val customUri = settingsStore.downloadPathUri
        // Use the same path convention as DownloadService
        val fullPath = "VRipper/${post.folderName}"

        try {
            if (customUri != null) {
                val treeUri = Uri.parse(customUri)
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                
                var currentDocId = docId
                val parts = fullPath.split("/")
                var targetUri: Uri? = null
                
                // Traverse to find the target directory
                for (i in parts.indices) {
                    val part = parts[i]
                    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentDocId)
                    var foundId: String? = null
                    
                    context.contentResolver.query(
                        childrenUri,
                        arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                        null, null, null
                    )?.use { cursor ->
                        while (cursor.moveToNext()) {
                            val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                            val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                            if (nameIndex >= 0 && idIndex >= 0) {
                                val name = cursor.getString(nameIndex)
                                if (name == part) {
                                    foundId = cursor.getString(idIndex)
                                    break
                                }
                            }
                        }
                    }
                    
                    if (foundId != null) {
                        currentDocId = foundId
                        if (i == parts.lastIndex) {
                            targetUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, foundId)
                        }
                    } else {
                        // Path not found
                        break
                    }
                }
                
                if (targetUri != null) {
                     DocumentsContract.deleteDocument(context.contentResolver, targetUri)
                }
                
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // MediaStore deletion
                val projection = arrayOf(MediaStore.Images.Media._ID)
                val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf("%$fullPath/%")
                
                val idsToDelete = mutableListOf<Long>()
                
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                    while (cursor.moveToNext()) {
                        idsToDelete.add(cursor.getLong(idColumn))
                    }
                }
                
                idsToDelete.forEach { id ->
                    val uri = android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    try {
                        context.contentResolver.delete(uri, null, null)
                    } catch (e: Exception) {
                         e.printStackTrace()
                    }
                }
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val targetDir = File(picturesDir, fullPath)
                if (targetDir.exists()) {
                    targetDir.deleteRecursively()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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

    private fun extractHost(url: String): String? {
        val uri = Uri.parse(url)
        return if (uri.scheme != null && uri.host != null) {
            "${uri.scheme}://${uri.host}"
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
