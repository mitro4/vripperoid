package me.vripperoid.android.service

import android.app.Service
import android.content.Intent
import android.os.Environment
import android.os.IBinder
import kotlinx.coroutines.*
import java.util.concurrent.Executors
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

    // Create a dedicated dispatcher for downloads to avoid starving the main IO dispatcher (used for DB/Service loop)
    // Using a cached thread pool allows scaling up to the requested concurrency limit (e.g. 20 posts * 4 images = 80 threads)
    private val downloadDispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private val TAG = "DownloadService"
    
    // Map PostId -> List of Jobs for immediate cancellation
    private val activeJobMap = java.util.concurrent.ConcurrentHashMap<Long, MutableList<Job>>()
    
    // Broadcast Receiver for immediate stop
    private val stopReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == "me.vripperoid.android.ACTION_STOP_DOWNLOAD") {
                val postId = intent.getLongExtra("postId", -1L)
                if (postId != -1L) {
                    LogUtils.d(TAG, "Received immediate stop for post $postId")
                    val jobs = activeJobMap[postId]
                    if (jobs != null) {
                        synchronized(jobs) {
                            jobs.forEach { it.cancel() }
                            jobs.clear()
                        }
                        activeJobMap.remove(postId)
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            
            // Register receiver
            val filter = android.content.IntentFilter("me.vripperoid.android.ACTION_STOP_DOWNLOAD")
            androidx.core.content.ContextCompat.registerReceiver(this, stopReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
            
            startDownloading()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(stopReceiver)
        scope.cancel()
        downloadDispatcher.close()
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
                    
                    val maxConcurrentImagesPerPost = settingsStore.maxGlobalConcurrent
                    val maxConcurrentPosts = settingsStore.maxConcurrentPosts
                    
                    // Check active posts count
                    val activePostIds = imageDao.getActiveDownloadPostIds()
                    
                    // We need to count total active downloads to respect some global limit if we want?
                    // Or user said: "maxConcurrentImages" is PER POST.
                    // "20 posts at once, and inside each 20 images at once".
                    // So we DON'T check activeDownloads.size < maxConcurrentImages globally.
                    // We check PER POST.
                    
                    // But we have a loop here that picks ONE image at a time.
                    // We need to pick an image that satisfies:
                    // 1. Its post is already active OR (active posts < maxConcurrentPosts)
                    // 2. Its post has < maxConcurrentImagesPerPost active downloads currently
                    
                    // Let's iterate through candidate posts to find a valid image
                    
                    // 1. Get candidate posts: Active posts + (if space) Next pending posts
                    // We need a list of pending post IDs.
                    // This is getting complex to do purely in SQL one-liner.
                    
                    // Simplified approach:
                    // Get all PENDING images ordered by PostID, ID.
                    // Iterate and pick the first one that fits the criteria.
                    // To avoid fetching ALL images, we can fetch batch or use a smarter query.
                    
                    // Better approach:
                    // Get list of active posts.
                    // For each active post, check if it can download more images (count < limit).
                    // If yes, pick image from it.
                    // If we still have capacity for more posts, pick next pending post, check if it can download.
                    
                    var imageToDownload: me.vripperoid.android.domain.Image? = null
                    
                    // Priority 1: If we have room for MORE posts, try to pick a NEW post first.
                    // This ensures we reach 'maxConcurrentPosts' breadth-wise before deepening existing posts.
                    if (activePostIds.size < maxConcurrentPosts) {
                        imageToDownload = imageDao.getNextPendingExcludePosts(activePostIds)
                    }

                    // Priority 2: If we didn't pick a new post (either full or none available), refill ACTIVE posts.
                    if (imageToDownload == null) {
                        val sortedActivePostIds = activePostIds.sorted()
                        for (postId in sortedActivePostIds) {
                            val currentDownloadsForPost = imageDao.countDownloadingImagesForPost(postId)
                            if (currentDownloadsForPost < maxConcurrentImagesPerPost) {
                                val img = imageDao.getNextPendingForActivePosts(listOf(postId))
                                if (img != null) {
                                    imageToDownload = img
                                    break // Found one
                                }
                            }
                        }
                    }
                    
                    if (imageToDownload != null) {
                        val image = imageToDownload
                        LogUtils.d(TAG, "Processing image ${image.id} from post ${image.postEntityId}")
                            
                            // Mark as downloading immediately to prevent picking it up again
                            image.status = Status.DOWNLOADING
                            imageDao.update(image)
                            
                            // Also update Post status to DOWNLOADING if it was PENDING/STOPPED
                            val post = postDao.getById(image.postEntityId)
                            if (post != null && post.status != Status.DOWNLOADING) {
                                post.status = Status.DOWNLOADING
                                postDao.update(post)
                            }
                            
                            val job = launch(downloadDispatcher) {
                                val currentJobs = activeJobMap.computeIfAbsent(image.postEntityId) { mutableListOf() }
                                synchronized(currentJobs) {
                                    currentJobs.add(coroutineContext.job)
                                }
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
                                    if (e is CancellationException) {
                                        LogUtils.d(TAG, "Download cancelled for image ${image.id}")
                                        image.status = Status.STOPPED
                                    } else {
                                        LogUtils.e(TAG, "Download failed", e)
                                        image.status = Status.ERROR
                                    }
                                }
                                imageDao.update(image)
                                
                                // Remove job from map when done
                                val cleanupJobs = activeJobMap[image.postEntityId]
                                if (cleanupJobs != null) {
                                    synchronized(cleanupJobs) {
                                        // We need reference to self (job). 
                                        // Ideally we shouldn't iterate to remove, but list is small.
                                        // Actually we can't access 'job' variable here easily inside the lambda before it's assigned.
                                        // But we can use current coroutine context job?
                                        // jobs.remove(coroutineContext[Job])
                                        // Simplified: just let the map clean up when empty or periodically?
                                        // Or better: store job in map AFTER launch, and remove here.
                                        // We need to access 'job' here.
                                        // We can use `coroutineContext.job`.
                                        cleanupJobs.remove(coroutineContext.job)
                                    }
                                    if (cleanupJobs.isEmpty()) {
                                        activeJobMap.remove(image.postEntityId)
                                    }
                                }
                                
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
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error in download loop", e)
                    delay(1000)
                }
            }
        }
    }
}
