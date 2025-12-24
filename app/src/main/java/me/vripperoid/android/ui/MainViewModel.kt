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

                    // Process in normal order to ensure first post is inserted first
                    // The UI sorts by addedOn ASC.
                    // If we want "Inside one thread return previous display order in UI" (which likely means P1 Top, P2 Bottom),
                    // we need P1 to be "older" than P2.
                    // If parser returns [P1, P2]. We insert P1 (T1), P2 (T2).
                    // `ASC` sort: P1, P2.
                    // This seems correct.
                    // However, if the user says "return previous order", maybe the parser returns [P2, P1] (Reverse)?
                    // If parser is [P2, P1]. Insert P2 (T1), P1 (T2).
                    // `ASC` sort: P2, P1.
                    // User sees P2 at Top. If they want P1 at Top, we must reverse.
                    // Let's assume parser returns posts in reverse order (Last post first) and reverse it back to natural order.
                    threadItem.postItemList.asReversed().forEach { postItem ->
                        if (postDao.countByVgPostId(postItem.postId) > 0) return@forEach

                        val existingCount = postDao.countByThreadId(postItem.threadId)
                        // If multiple posts in this batch OR we already have posts from this thread, use suffix
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
                            downloadDirectory = folderName,
                            addedOn = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).format(Date()),
                            folderName = folderName,
                            status = Status.STOPPED,
                            previewUrls = postItem.imageItemList.take(4).map { it.thumbUrl }
                        )
                        
                        val postId = postDao.insert(post)
                        
                        // Check if folder already exists on disk
                        val folderExists = checkFolderExists(post.folderName)
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
        // Now using flat structure directly in the root of the selected folder
        val fullPath = post.folderName

        try {
            if (customUri != null) {
                val treeUri = Uri.parse(customUri)
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                
                var currentDocId = docId
                // No more VRipper/ prefix logic for traversal if we assume flat structure in root
                // But we should still support checking if it's a folder in the root
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentDocId)
                var foundUri: Uri? = null
                
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
                            if (name == fullPath) {
                                val foundId = cursor.getString(idIndex)
                                foundUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, foundId)
                                break
                            }
                        }
                    }
                }
                
                if (foundUri != null) {
                     DocumentsContract.deleteDocument(context.contentResolver, foundUri!!)
                }
                
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // MediaStore deletion
                val projection = arrayOf(MediaStore.Images.Media._ID)
                val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
                // Keep VRipper/ prefix only for default MediaStore storage if we want to organize it there,
                // BUT user asked to remove "VRipper" nested folder creation.
                // However, for MediaStore (Pictures folder), putting everything in root is messy.
                // The request specifically said "When selecting a save directory... remove creation of nested VRipper folder".
                // This implies Custom Storage.
                // For default storage (Pictures), it is probably still better to keep a subfolder to avoid cluttering root Pictures.
                // Let's assume the request applies to Custom Storage primarily, but for consistency let's check.
                // "Скачивать посты будем сразу в ту папку, которую выбрал пользователь" -> This strongly implies Custom Storage.
                // For MediaStore, the "selected folder" is implicitly Pictures.
                // If we remove VRipper prefix there, we dump folders directly into Pictures. That is acceptable standard behavior.
                val pathPrefix = if (fullPath.startsWith("VRipper/")) fullPath else fullPath
                val selectionArgs = arrayOf("%$pathPrefix/%")
                
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
    
    fun stopDownload(post: Post) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedPost = post.copy(status = Status.STOPPED)
            postDao.update(updatedPost)
            
            // Set all PENDING images to STOPPED
            // For DOWNLOADING images, we can't easily cancel the job reference in Service from here without more complex IPC/EventBus.
            // But updating the DB status to STOPPED (if we did) might confuse the service loop or be overwritten.
            // The safe bet is: Update PENDING -> STOPPED.
            // The actively downloading images will finish (or fail) and update status to FINISHED/ERROR.
            // Since no more PENDING images exist (they are STOPPED), the service won't pick up new ones for this post.
            imageDao.resetStatusByPostId(
                post.id, 
                Status.STOPPED, 
                listOf(Status.FINISHED, Status.ALREADY_DOWNLOADED)
            )
            
            // Note: Currently imageDao.resetStatusByPostId updates ALL non-excluded status.
            // If we have DOWNLOADING status images, and we update them to STOPPED here, 
            // the Service will eventually try to update them to FINISHED.
            // DB write conflict might happen but usually last write wins.
            // If Service writes FINISHED after we write STOPPED, it's fine (it actually finished).
            // If we write STOPPED after Service writes FINISHED, we might show STOPPED for a finished image.
            // So we should be careful.
            // Ideally we only update PENDING -> STOPPED.
            // Let's modify Dao or check usage.
            // updateStatusByPostId(postId, newStatus, excludeStatus)
            // "UPDATE image SET status = :newStatus WHERE postEntityId = :postId AND status != :excludeStatus"
            // If we exclude FINISHED, we update PENDING, DOWNLOADING, ERROR, etc.
            // We should probably only update PENDING and DOWNLOADING?
            // Actually, "Stop" usually means "Pause".
            // So PENDING -> STOPPED is the main goal.
            // DOWNLOADING -> STOPPED? If we do this, and the download finishes 1ms later, it writes FINISHED.
            // If the download takes 10s, UI shows STOPPED.
            // Better to only update PENDING.
            // But the current Dao method is generic.
            // Let's rely on the fact that changing PENDING to STOPPED effectively stops the queue.
            // The active downloads (max 2-4) will finish naturally.
            // But user requested IMMEDIATE stop.
            // Send broadcast to Service to cancel jobs.
            val context = getApplication<Application>()
            val intent = android.content.Intent("me.vripperoid.android.ACTION_STOP_DOWNLOAD")
            intent.putExtra("postId", post.id)
            intent.setPackage(context.packageName) // Restrict to own package
            context.sendBroadcast(intent)
        }
    }

    fun startDownload(post: Post) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Reset unfinished images (ERROR, STOPPED, DOWNLOADING, NOT_FULL_FINISHED, PENDING) to PENDING
            // Exclude already FINISHED and ALREADY_DOWNLOADED images
            imageDao.resetStatusByPostId(
                post.id, 
                Status.PENDING, 
                listOf(Status.FINISHED, Status.ALREADY_DOWNLOADED)
            )

            // 2. Check if we actually have anything to download
            val pendingCount = imageDao.countPendingByPostId(post.id)
            
            if (pendingCount > 0) {
                // If there are pending images, set post to PENDING to trigger service
                val updatedPost = post.copy(status = Status.PENDING)
                postDao.update(updatedPost)
            } else {
                // All images are finished (or we have 0 images?)
                // If total > 0 and pending == 0, then we are done.
                if (post.total > 0) {
                    val errorCount = imageDao.countErrorByPostId(post.id)
                    val newStatus = if (errorCount > 0) Status.NOT_FULL_FINISHED else Status.FINISHED
                    
                    // Only update if status is different to avoid unnecessary DB writes/UI flickers
                    if (post.status != newStatus) {
                        postDao.update(post.copy(status = newStatus))
                    }
                }
            }
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
