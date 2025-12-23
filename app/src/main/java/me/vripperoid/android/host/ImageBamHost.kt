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
    // Use a broader xpath to find the link containing 'Continue' text, handling nested elements like spans
    private val CONTINUE_XPATH = "//a[contains(., 'Continue')]"

    override fun resolve(image: Image): Pair<String, String> {
        val document = fetchDocument(image.url)
        val doc = try {
            val continueLink = XpathUtils.getAsNode(document, CONTINUE_XPATH)
            if (continueLink != null) {
                 val href = continueLink.attributes?.getNamedItem("href")?.textContent
                 if (!href.isNullOrEmpty()) {
                     fetchDocument(href)
                 } else {
                     document
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
