package me.vripper.android.host

import me.vripper.android.domain.Image
import me.vripper.android.exception.HostException
import me.vripper.android.exception.XpathException
import me.vripper.android.util.XpathUtils
import org.w3c.dom.Node

class TurboImageHost : Host("turboimagehost.com", 14) {
    private val TAG = "TurboImageHost"
    private val TITLE_XPATH = "//div[contains(@class,'titleFullS')]/h1"
    private val IMG_XPATH = "//img[@id='imageid']"

    override fun resolve(image: Image): Pair<String, String> {
        val document = fetchDocument(image.url)
        var title: String?
        title = try {
            val titleNode = XpathUtils.getAsNode(document, TITLE_XPATH)
            titleNode?.textContent?.trim()
        } catch (e: XpathException) {
            throw HostException(e.message ?: "Xpath error")
        }

        if (title.isNullOrEmpty()) {
            title = image.url.substring(image.url.lastIndexOf('/') + 1)
        }

        val urlNode: Node = XpathUtils.getAsNode(document, IMG_XPATH)
            ?: throw HostException("Xpath '$IMG_XPATH' cannot be found in '${image.url}'")

        val imgUrl = urlNode.attributes.getNamedItem("src").textContent.trim()
        return Pair(title, imgUrl)
    }
}
