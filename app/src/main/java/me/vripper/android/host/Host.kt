package me.vripper.android.host

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import me.vripper.android.domain.Image
import me.vripper.android.exception.HostException
import me.vripper.android.util.HtmlUtils
import me.vripper.android.util.LogUtils
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.w3c.dom.Document
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

abstract class Host(val hostName: String, val hostId: Byte) : KoinComponent {

    private val client: OkHttpClient by inject()
    private val TAG = "Host"

    abstract fun resolve(image: Image): Pair<String, String>

    fun download(image: Image, context: Context, folderName: String, onProgress: (Long, Long) -> Unit): DownloadedImage {
        val resolved = resolve(image)
        val name = resolved.first
        val url = resolved.second
        
        LogUtils.d(TAG, "Downloading $name from $url")

        val request = Request.Builder().url(url).header("Referer", image.url).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw HostException("Failed to download ${image.url}: ${response.code}")
        }

        val body = response.body ?: throw HostException("Empty body")
        val contentType = body.contentType()?.toString()
        val type = ImageMimeType.fromContentType(contentType ?: "")
            ?: throw HostException("Unsupported mime type: $contentType")

        // Use name from resolve, but ensure extension is correct or added
        var fileName = name
        val extension = type.name.substringAfter("_").lowercase()
        if (!fileName.lowercase().endsWith(".$extension")) {
            fileName = "$fileName.$extension"
        }

        val outputStream: OutputStream
        val file: File?
        var uri: android.net.Uri? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, type.strValue)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + folderName)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val contentResolver = context.contentResolver
            val mediaUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw HostException("Failed to create MediaStore entry")
            
            outputStream = contentResolver.openOutputStream(mediaUri)
                ?: throw HostException("Failed to open output stream")
            file = null
            uri = mediaUri
        } else {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val targetDir = File(picturesDir, folderName)
            if (!targetDir.exists()) targetDir.mkdirs()
            file = File(targetDir, fileName)
            outputStream = FileOutputStream(file)
        }
        
        val contentLength = body.contentLength()
        var downloaded = 0L
        
        val inputStream = body.byteStream()
        
        try {
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                downloaded += bytesRead
                onProgress(downloaded, contentLength)
            }
            outputStream.flush()
        } finally {
            inputStream.close()
            outputStream.close()
            body.close()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != null) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                context.contentResolver.update(uri!!, contentValues, null, null)
            }
        }
        
        return DownloadedImage(name, file, type)
    }

    fun fetchDocument(url: String): Document {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw HostException("Failed to fetch document: ${response.code}")
        return HtmlUtils.clean(response.body!!.byteStream())
    }
}
