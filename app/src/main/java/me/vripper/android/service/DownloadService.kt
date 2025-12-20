package me.vripper.android.service

import android.app.Service
import android.content.Intent
import android.os.Environment
import android.os.IBinder
import kotlinx.coroutines.*
import me.vripper.android.data.ImageDao
import me.vripper.android.data.PostDao
import me.vripper.android.domain.Status
import me.vripper.android.host.*
import me.vripper.android.util.LogUtils
import org.koin.android.ext.android.inject
import java.io.File

class DownloadService : Service() {

    private val imageDao: ImageDao by inject()
    private val postDao: PostDao by inject()
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
            while (isActive) {
                try {
                    val image = imageDao.getNextPending()
                    if (image != null) {
                        LogUtils.d(TAG, "Processing image ${image.id}")
                        
                        image.status = Status.DOWNLOADING
                        imageDao.update(image)
                        
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
                                val folderName = "VRipper/${image.postEntityId}"
                                
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
