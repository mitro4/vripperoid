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
        // Based on the reference implementation, we use the 'nsfw_inter' cookie strategy.
        // Reference implementation: https://github.com/dev-claw/vripper-project/blob/main/vripper-core/src/main/kotlin/me/vripper/host/ImageBamHost.kt
        
        // IMG_XPATH = "//img[contains(@class,'main-image')]" 
        // CONTINUE_XPATH = "//*[contains(text(), 'Continue')]"
        
        var document = fetchDocument(image.url)
        val continueNode = try { XpathUtils.getAsNode(document, CONTINUE_XPATH) } catch (e: XpathException) { null }
        
        if (continueNode != null) {
             // The reference implementation sets a cookie "nsfw_inter=1" and re-fetches the page.
             // It does NOT follow a link.
             // We mimic this behavior by passing the cookie in the header.
             document = fetchDocument(image.url, mapOf("nsfw_inter" to "1"))
        }

        val imgNode: Node = try {
            XpathUtils.getAsNode(document, IMG_XPATH)
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
