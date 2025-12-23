package me.vripperoid.android.host

import me.vripperoid.android.domain.Image
import me.vripperoid.android.exception.HostException
import me.vripperoid.android.exception.XpathException
import me.vripperoid.android.util.HtmlUtils
import me.vripperoid.android.util.LogUtils
import me.vripperoid.android.util.XpathUtils
import org.w3c.dom.Node
import java.util.UUID

class ImageBamHost : Host("imagebam.com", 2) {
    private val TAG = "ImageBamHost"
    private val IMG_XPATH = "//*[local-name()='img' and contains(@class,'main-image')]"
    private val CONTINUE_XPATH = "//*[contains(text(), 'Continue')]"

    override fun resolve(image: Image): Pair<String, String> {
        val document = fetchDocument(image.url)
        val doc = try {
            if (XpathUtils.getAsNode(document, CONTINUE_XPATH) != null) {
                 // Try to fetch again, expecting the cookie to be set or the interstitial to be bypassed.
                 // In many cases, simply requesting the page again or following the link works.
                 // If the 'Continue' button is a link, we should follow it.
                 val continueNode = XpathUtils.getAsNode(document, CONTINUE_XPATH)
                 // Check if it's an 'a' tag
                 if (continueNode?.nodeName.equals("a", ignoreCase = true)) {
                     val href = continueNode?.attributes?.getNamedItem("href")?.textContent
                     if (!href.isNullOrEmpty()) {
                         fetchDocument(href)
                     } else {
                         fetchDocument(image.url)
                     }
                 } else {
                     fetchDocument(image.url)
                 }
            } else {
                document
            }
        } catch (e: XpathException) {
            // If xpath fails, just ignore and try with original document
            document
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
