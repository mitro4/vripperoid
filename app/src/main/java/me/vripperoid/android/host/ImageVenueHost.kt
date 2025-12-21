package me.vripperoid.android.host

import me.vripperoid.android.domain.Image
import me.vripperoid.android.exception.HostException
import me.vripperoid.android.exception.XpathException
import me.vripperoid.android.util.XpathUtils
import org.w3c.dom.Node

class ImageVenueHost : Host("imagevenue.com", 4) {
    private val TAG = "ImageVenueHost"
    private val IMG_XPATH = "//*[local-name()='a' and @data-toggle='full']/*[local-name()='img' and @id='main-image']"
    private val CONTINUE_BUTTON_XPATH = "//*[local-name()='a' and @title='Continue to ImageVenue']"

    override fun resolve(image: Image): Pair<String, String> {
        val document = fetchDocument(image.url)
        val doc = try {
            if (XpathUtils.getAsNode(document, CONTINUE_BUTTON_XPATH) != null) {
                fetchDocument(image.url)
            } else {
                document
            }
        } catch (e: XpathException) {
            throw HostException(e.message ?: "Xpath error")
        }

        val imgNode: Node = try {
            XpathUtils.getAsNode(doc, IMG_XPATH)
        } catch (e: XpathException) {
            throw HostException(e.message ?: "Xpath error")
        } ?: throw HostException("Xpath '$IMG_XPATH' cannot be found in '${image.url}'")

        return try {
            val imgTitle = imgNode.attributes.getNamedItem("alt")?.textContent?.trim() ?: ""
            val imgUrl = imgNode.attributes.getNamedItem("src")?.textContent?.trim() ?: ""
            
            val name = if (imgTitle.isEmpty()) imgUrl.substring(imgUrl.lastIndexOf('/') + 1) else imgTitle
            Pair(name, imgUrl)
        } catch (e: Exception) {
            throw HostException("Unexpected error occurred: ${e.message}")
        }
    }
}
