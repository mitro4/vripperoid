package me.vripper.android.host

import me.vripper.android.domain.Image
import me.vripper.android.exception.HostException
import me.vripper.android.exception.XpathException
import me.vripper.android.util.XpathUtils
import org.w3c.dom.Node

class PixhostHost : Host("pixhost.to", 10) {
    private val TAG = "PixhostHost"
    private val IMG_XPATH = "//img[@id='image']"

    override fun resolve(image: Image): Pair<String, String> {
        val document = fetchDocument(image.url)
        val imgNode: Node = try {
            XpathUtils.getAsNode(document, IMG_XPATH)
        } catch (e: XpathException) {
            throw HostException(e.message ?: "Xpath error")
        } ?: throw HostException("Xpath '$IMG_XPATH' cannot be found in '${image.url}'")

        return try {
            val imgTitle = imgNode.attributes.getNamedItem("alt")?.textContent?.trim() ?: ""
            val imgUrl = imgNode.attributes.getNamedItem("src")?.textContent?.trim() ?: ""
            val name = if (imgTitle.contains('_')) imgTitle.substring(imgTitle.indexOf('_') + 1) else imgTitle
            Pair(name, imgUrl)
        } catch (e: Exception) {
            throw HostException("Unexpected error occurred: ${e.message}")
        }
    }
}
