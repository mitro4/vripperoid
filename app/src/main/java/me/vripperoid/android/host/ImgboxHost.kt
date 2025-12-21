package me.vripperoid.android.host

import me.vripperoid.android.domain.Image
import me.vripperoid.android.exception.HostException
import me.vripperoid.android.exception.XpathException
import me.vripperoid.android.util.LogUtils
import me.vripperoid.android.util.XpathUtils
import org.w3c.dom.Node

class ImgboxHost : Host("imgbox.com", 6) {
    private val TAG = "ImgboxHost"
    private val IMG_XPATH = "//img[@id='img']"

    override fun resolve(image: Image): Pair<String, String> {
        val document = fetchDocument(image.url)
        val imgNode: Node = try {
            XpathUtils.getAsNode(document, IMG_XPATH)
        } catch (e: XpathException) {
            throw HostException(e.message ?: "Xpath error")
        } ?: throw HostException("Xpath '$IMG_XPATH' cannot be found in '${image.url}'")

        return try {
            val imgTitle = imgNode.attributes.getNamedItem("title").textContent.trim()
            val imgUrl = imgNode.attributes.getNamedItem("src").textContent.trim()
            Pair(imgTitle, imgUrl)
        } catch (e: Exception) {
            throw HostException("Unexpected error occurred: ${e.message}")
        }
    }
}
