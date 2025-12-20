package me.vripper.android.host

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.DocumentsContract
import android.net.Uri
import me.vripper.android.domain.Image
import me.vripper.android.exception.HostException
import me.vripper.android.util.HtmlUtils
import me.vripper.android.util.LogUtils
import me.vripper.android.settings.SettingsStore
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
    private val settingsStore: SettingsStore by inject()
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
        var uri: Uri? = null
        
        val customUriString = settingsStore.downloadPathUri

        if (customUriString != null) {
            try {
                val treeUri = Uri.parse(customUriString)
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                
                var currentDocId = docId
                val parts = folderName.split("/")
                
                parts.forEach { part ->
                    if (part.isNotEmpty()) {
                        var foundId: String? = null
                        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentDocId)
                        
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
                        
                        if (foundId == null) {
                            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, currentDocId)
                            val newDir = DocumentsContract.createDocument(context.contentResolver, parentUri, DocumentsContract.Document.MIME_TYPE_DIR, part)
                                ?: throw HostException("Failed to create directory $part")
                            foundId = DocumentsContract.getDocumentId(newDir)
                        }
                        currentDocId = foundId!!
                    }
                }
                
                // Now create file in currentDocId
                // Check if exists
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentDocId)
                var existingFileUri: Uri? = null
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
                            if (name == fileName) {
                                val id = cursor.getString(idIndex)
                                existingFileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                                break
                            }
                        }
                    }
                }
                
                if (existingFileUri != null) {
                    DocumentsContract.deleteDocument(context.contentResolver, existingFileUri!!)
                }
                
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, currentDocId)
                val newFileUri = DocumentsContract.createDocument(context.contentResolver, parentUri, type.strValue, fileName)
                    ?: throw HostException("Failed to create file $fileName")
                    
                uri = newFileUri
                outputStream = context.contentResolver.openOutputStream(uri) ?: throw HostException("Failed to open stream")
                file = null
            } catch (e: Exception) {
                LogUtils.e(TAG, "Error writing to custom directory", e)
                throw HostException("Error writing to custom directory: ${e.message}")
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
                context.contentResolver.update(uri, contentValues, null, null)
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
