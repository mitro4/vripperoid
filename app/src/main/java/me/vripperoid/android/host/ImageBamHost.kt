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
            val continueLink = XpathUtils.getAsNode(document, CONTINUE_XPATH)
            if (continueLink != null) {
                 // Check if it is a link
                 if (continueLink.nodeName.equals("a", ignoreCase = true)) {
                     val href = continueLink.attributes?.getNamedItem("href")?.textContent
                     if (!href.isNullOrEmpty()) {
                         fetchDocument(href)
                     } else {
                         document
                     }
                 } else {
                     // Sometimes the continue button is just a button or span inside an 'a' tag or handled by JS.
                     // But typically for ImageBam, if there is a 'Continue' text, it's either an anchor or inside one.
                     // Let's try to find the parent 'a' tag if the current node is not 'a'.
                     var parent = continueLink.parentNode
                     var href: String? = null
                     while (parent != null && parent.nodeName != "#document") {
                         if (parent.nodeName.equals("a", ignoreCase = true)) {
                             href = parent.attributes?.getNamedItem("href")?.textContent
                             break
                         }
                         parent = parent.parentNode
                     }
                     
                     if (!href.isNullOrEmpty()) {
                         fetchDocument(href)
                     } else {
                         // If we can't find a link, maybe we just need to re-fetch the same URL with a cookie?
                         // Original vripper project sets a cookie 'nsfw_inter=1'.
                         // Since we don't have easy cookie injection here in this method, let's try fetching the same URL again.
                         // Often the first load sets the cookie and the second load works.
                         fetchDocument(image.url)
                     }
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
