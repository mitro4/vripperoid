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
        // Based on the reference implementation, we should try to use the 'nsfw_inter' cookie
        // But since we can't easily inject cookies into the global OkHttp client from here without changing architecture,
        // we will try to mimic the behavior by fetching, checking for "Continue", and then fetching again.
        // The original vripper implementation sets a cookie: BasicClientCookie("nsfw_inter", "1")
        // Our fetchDocument uses a shared OkHttpClient. If we want to support this properly, 
        // we might need to rely on the server setting the cookie on the first request (which it often does),
        // or we need to manually add the cookie header in our request if possible.
        // For now, let's stick to the link following logic which is also robust, 
        // but add the specific Xpath from the reference implementation as a backup or primary.
        
        // Reference implementation uses:
        // IMG_XPATH = "//img[contains(@class,'main-image')]" (matches ours mostly)
        // CONTINUE_XPATH = "//*[contains(text(), 'Continue')]" (matches ours)
        
        // It also cleans HTML using HtmlUtils.clean(content)
        
        val document = fetchDocument(image.url)
        val doc = try {
            val continueLink = XpathUtils.getAsNode(document, CONTINUE_XPATH)
            if (continueLink != null) {
                 // Try to find a link to follow
                 var href: String? = null
                 
                 // Check if the node itself is an anchor
                 if (continueLink.nodeName.equals("a", ignoreCase = true)) {
                     href = continueLink.attributes?.getNamedItem("href")?.textContent
                 } 
                 
                 // If not, check parents
                 if (href.isNullOrEmpty()) {
                     var parent = continueLink.parentNode
                     while (parent != null && parent.nodeName != "#document") {
                         if (parent.nodeName.equals("a", ignoreCase = true)) {
                             href = parent.attributes?.getNamedItem("href")?.textContent
                             break
                         }
                         parent = parent.parentNode
                     }
                 }
                 
                 if (!href.isNullOrEmpty()) {
                     fetchDocument(href)
                 } else {
                     // Fallback: just fetch the original URL again, hoping cookie was set
                     fetchDocument(image.url)
                 }
            } else {
                document
            }
        } catch (e: XpathException) {
            document
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
