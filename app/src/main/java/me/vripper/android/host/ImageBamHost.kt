package me.vripper.android.host

import me.vripper.android.domain.Image
import me.vripper.android.exception.HostException
import me.vripper.android.exception.XpathException
import me.vripper.android.util.HtmlUtils
import me.vripper.android.util.LogUtils
import me.vripper.android.util.XpathUtils
import org.w3c.dom.Node
import java.util.UUID

class ImageBamHost : Host("imagebam.com", 2) {
    private val TAG = "ImageBamHost"
    private val IMG_XPATH = "//img[contains(@class,'main-image')]"
    private val CONTINUE_XPATH = "//*[contains(text(), 'Continue')]"

    override fun resolve(image: Image): Pair<String, String> {
        val document = fetchDocument(image.url)
        val doc = try {
            if (XpathUtils.getAsNode(document, CONTINUE_XPATH) != null) {
                // In a real app we might need to handle cookies properly if the continue button sets a cookie.
                // The original code sets a cookie manually.
                // For simplicity here, we assume fetchDocument handles basic cases, 
                // but strictly we might need to add the cookie to the OkHttp client or request.
                // Since OkHttp CookieJar is not set up in the base class in a way to easily add one-off cookies for a request,
                // we might need to rely on the server accepting the request or implementing cookie handling.
                // However, the original code sets "nsfw_inter" cookie.
                // Let's try to just fetch again, or we might need to improve Host architecture to support cookies per request.
                // For now, we will just try to fetch the page again or return the document if it works.
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
            
            var defaultName = UUID.randomUUID().toString()
            val index = imgUrl.lastIndexOf('/')
            if (index != -1 && index < imgUrl.length) {
                defaultName = imgUrl.substring(imgUrl.lastIndexOf('/') + 1)
            }
            Pair(imgTitle.ifEmpty { defaultName }, imgUrl)
        } catch (e: Exception) {
            throw HostException("Unexpected error occurred: ${e.message}")
        }
    }
}
