package me.vripperoid.android.service

import android.app.Service
import android.content.Intent
import android.os.Environment
import android.os.IBinder
import kotlinx.coroutines.*
import me.vripperoid.android.data.ImageDao
import me.vripperoid.android.data.PostDao
import me.vripperoid.android.domain.Status
import me.vripperoid.android.host.*
import me.vripperoid.android.settings.SettingsStore
import me.vripperoid.android.util.LogUtils
import org.koin.android.ext.android.inject
import java.io.File

class DownloadService : Service() {

    private val imageDao: ImageDao by inject()
    private val postDao: PostDao by inject()
    private val settingsStore: SettingsStore by inject()
    private val dPicMeHost: DPicMeHost by inject()
    private val imageBamHost: ImageBamHost by inject()
    private val imageTwistHost: ImageTwistHost by inject()
    private val imageVenueHost: ImageVenueHost by inject()
    private val imageZillaHost: ImageZillaHost by inject()
    private val imgboxHost: ImgboxHost by inject()
    private val imgSpiceHost: ImgSpiceHost by inject()
    private val imxHost: ImxHost by inject()
    private val pimpandhostHost: PimpandhostHost by inject()
    private val pixhostHost: PixhostHost by inject()
    private val pixRouteHost: PixRouteHost by inject()
    private val pixxxelsHost: PixxxelsHost by inject()
    private val postImgHost: PostImgHost by inject()
    private val turboImageHost: TurboImageHost by inject()
    private val viprImHost: ViprImHost by inject()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private val TAG = "DownloadService"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            startDownloading()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun startDownloading() {
        scope.launch {
            LogUtils.d(TAG, "Download service started loop")
            
            // We use a semaphore logic or simple job list to limit concurrency
            val activeDownloads = mutableListOf<Job>()
            
            while (isActive) {
                try {
                    // Clean up finished jobs
                    activeDownloads.removeAll { !it.isActive }
                    
                    val maxConcurrentImages = settingsStore.maxGlobalConcurrent
                    val maxConcurrentPosts = settingsStore.maxConcurrentPosts
                    
                    // Check active posts count
                    // We need a way to count how many distinct posts are currently being downloaded
                    val activePostIds = imageDao.getActiveDownloadPostIds() // We need this query
                    
                    // Logic:
                    // 1. If activeDownloads (images) < maxConcurrentImages
                    // 2. AND (If the next image belongs to a post that is already active OR active posts count < maxConcurrentPosts)
                    
                    if (activeDownloads.size < maxConcurrentImages) {
                         // We need a smarter query than getNextPending()
                         // getNextPending() just returns any PENDING image.
                         // We should modify it or filter here.
                         
                         // Option A: Get a list of candidate images and filter in code (simple but less efficient)
                         // Option B: Complex SQL (better)
                         
                         // Let's try to get the next image, and check if we can start it.
                         // Ideally, we want to prioritize images from ALREADY ACTIVE posts to finish them first.
                         
                         // Let's modify logic:
                         // 1. Try to get a pending image from an already active post
                         // 2. We want to prioritize active posts based on their Post ID (FIFO) to finish first started first?
                         //    Or just any active post? Usually FIFO is better.
                         //    Our SQL `getNextPendingForActivePosts` sorts by image ID ASC.
                         var image = if (activePostIds.isNotEmpty()) {
                             // We want to prioritize based on Post ID order to keep consistency
                             val sortedActivePostIds = activePostIds.sorted()
                             imageDao.getNextPendingForActivePosts(sortedActivePostIds)
                         } else {
                             null
                         }
                         
                         // 2. If no image from active posts, check if we can start a new post
                         //    Only if activePostIds.size < maxConcurrentPosts
                         if (image == null && activePostIds.size < maxConcurrentPosts) {
                             // We want to pick images from the "next" post in queue.
                             // The queue is defined by Post creation time (ID).
                             // We want to pick the pending image with the LOWEST Post ID.
                             // Currently `getNextPending` sorts by Image ID ASC.
                             // Since Image IDs are sequential and tied to Post insertion, Image ID ASC implicitly sorts by Post ID ASC.
                             // So `getNextPending` already returns the image from the oldest pending post.
                             image = imageDao.getNextPending()
                         }
                         
                        if (image != null) {
                            LogUtils.d(TAG, "Processing image ${image.id}")
                            
                            // Mark as downloading immediately to prevent picking it up again
                            image.status = Status.DOWNLOADING
                            imageDao.update(image)
                            
                            // Also update Post status to DOWNLOADING if it was PENDING/STOPPED
                            val post = postDao.getById(image.postEntityId)
                            if (post != null && post.status != Status.DOWNLOADING) {
                                post.status = Status.DOWNLOADING
                                postDao.update(post)
                            }
                            
                            val job = launch {
                                try {
                                    val host = when (image.host) {
                                        1.toByte() -> dPicMeHost
                                        2.toByte() -> imageBamHost
                                        3.toByte() -> imageTwistHost
                                        4.toByte() -> imageVenueHost
                                        5.toByte() -> imageZillaHost
                                        6.toByte() -> imgboxHost
                                        7.toByte() -> imgSpiceHost
                                        8.toByte() -> imxHost
                                        9.toByte() -> pimpandhostHost
                                        10.toByte() -> pixhostHost
                                        11.toByte() -> pixRouteHost
                                        12.toByte() -> pixxxelsHost
                                        13.toByte() -> postImgHost
                                        14.toByte() -> turboImageHost
                                        15.toByte() -> viprImHost
                                        else -> null
                                    }
                                    
                                    if (host != null) {
                                        // Use app-specific storage to avoid permission issues
                                        val post = postDao.getById(image.postEntityId)
                                        val folderName = if (post != null) {
                                            post.folderName
                                        } else {
                                            image.postEntityId.toString()
                                        }
                                        
                                        host.download(image, applicationContext, folderName) { _, _ ->
                                            // Update progress
                                        }
                                        
                                        image.status = Status.FINISHED
                                        postDao.incrementDownloaded(image.postEntityId)
                                    } else {
                                        LogUtils.e(TAG, "No host found for ${image.url}")
                                        image.status = Status.ERROR
                                    }
                                } catch (e: Exception) {
                                    LogUtils.e(TAG, "Download failed", e)
                                    image.status = Status.ERROR
                                }
                                imageDao.update(image)
                                
                                // Check for completion after updating status (success or error)
                                // We check if there are any PENDING or DOWNLOADING images left for this post.
                                // If none, then the post is finished (either fully or partially with errors).
                                val remainingPending = imageDao.countPendingByPostId(image.postEntityId)
                                val remainingDownloading = imageDao.countDownloadingByPostId(image.postEntityId) // We need this query
                                
                                if (remainingPending == 0 && remainingDownloading == 0) {
                                    val post = postDao.getById(image.postEntityId)
                                    if (post != null) {
                                        val errorCount = imageDao.countErrorByPostId(image.postEntityId)
                                        post.status = if (errorCount > 0) Status.NOT_FULL_FINISHED else Status.FINISHED
                                        postDao.update(post)
                                    }
                                }
                            }
                            activeDownloads.add(job)
                        } else {
                            delay(1000)
                        }
                    } else {
                        delay(100) // Wait a bit if max concurrent reached
                    }
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error in download loop", e)
                    delay(1000)
                }
            }
        }
    }
}
