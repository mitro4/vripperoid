package me.vripperoid.android.host

import me.vripperoid.android.domain.Image
import me.vripperoid.android.exception.HostException
import me.vripperoid.android.exception.XpathException
import me.vripperoid.android.util.XpathUtils
import org.w3c.dom.Node

class ImageZillaHost : Host("imagezilla.net", 5) {
    private val TAG = "ImageZillaHost"
    private val IMG_XPATH = "//*[local-name()='img' and @id='photo']"

    override fun resolve(image: Image): Pair<String, String> {
        val document = fetchDocument(image.url)
        val titleNode = try {
            XpathUtils.getAsNode(document, IMG_XPATH)
        } catch (e: XpathException) {
            throw HostException(e.message ?: "Xpath error")
        } ?: throw HostException("Xpath '$IMG_XPATH' cannot be found in '${image.url}'")

        var title = titleNode.attributes.getNamedItem("title")?.textContent?.trim() ?: ""
        if (title.isEmpty()) {
            title = image.url.substring(image.url.lastIndexOf('/') + 1)
        }
        
        return try {
            Pair(title, image.url.replace("show", "images"))
        } catch (e: Exception) {
            throw HostException("Unexpected error occurred: ${e.message}")
        }
    }
}
