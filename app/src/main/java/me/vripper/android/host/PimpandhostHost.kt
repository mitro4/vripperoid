package me.vripper.android.host

import me.vripper.android.domain.Image
import me.vripper.android.exception.HostException
import me.vripper.android.exception.XpathException
import me.vripper.android.util.HtmlUtils
import me.vripper.android.util.XpathUtils
import org.w3c.dom.Node
import java.net.URI

class PimpandhostHost : Host("pimpandhost.com", 9) {
    private val TAG = "PimpandhostHost"
    private val IMG_XPATH = "//*[local-name()='img' and contains(@class, 'original')]"

    override fun resolve(image: Image): Pair<String, String> {
        val newUrl: String = try {
            appendUri(
                image.url.replace("http://", "https://").replace("-medium(\\.html)?".toRegex(), ""),
                "size=original"
            )
        } catch (e: Exception) {
            throw HostException("Failed to construct URL: ${e.message}")
        }

        val doc = fetchDocument(newUrl)
        val imgNode: Node = try {
            XpathUtils.getAsNode(doc, IMG_XPATH)
        } catch (e: XpathException) {
            throw HostException(e.message ?: "Xpath error")
        } ?: throw HostException("Xpath '$IMG_XPATH' cannot be found in '$newUrl'")

        return try {
            val imgTitle = imgNode.attributes.getNamedItem("alt")?.textContent?.trim() ?: ""
            val imgUrl = "https:" + (imgNode.attributes.getNamedItem("src")?.textContent?.trim() ?: "")
            val name = if (imgTitle.isEmpty()) imgUrl.substring(imgUrl.lastIndexOf('/') + 1) else imgTitle
            Pair(name, imgUrl)
        } catch (e: Exception) {
            throw HostException("Unexpected error occurred: ${e.message}")
        }
    }

    private fun appendUri(uri: String, appendQuery: String): String {
        val oldUri = URI(uri)
        var newQuery = oldUri.query
        if (newQuery == null) {
            newQuery = appendQuery
        } else {
            newQuery += "&$appendQuery"
        }
        return URI(
            oldUri.scheme, oldUri.authority, oldUri.path, newQuery, oldUri.fragment
        ).toString()
    }
}
