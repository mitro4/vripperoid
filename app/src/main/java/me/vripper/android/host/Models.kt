package me.vripper.android.host

import java.io.File

data class DownloadedImage(val name: String, val file: File?, val type: ImageMimeType)

enum class ImageMimeType(val strValue: String) {
    IMAGE_BMP("image/bmp"),
    IMAGE_GIF("image/gif"),
    IMAGE_JPEG("image/jpeg"),
    IMAGE_PNG("image/png"),
    IMAGE_WEBP("image/webp");
    
    companion object {
        fun fromContentType(contentType: String): ImageMimeType? {
            return entries.find { contentType.contains(it.strValue, true) }
        }
    }
}
