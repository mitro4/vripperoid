package me.vripper.android.host

import me.vripper.android.domain.Image
import me.vripper.android.exception.HostException
import me.vripper.android.exception.XpathException
import me.vripper.android.util.XpathUtils

class PostImgHost : Host("postimg.cc", 13) {
    private val TAG = "PostImgHost"
    private val TITLE_XPATH = "//span[contains(@class,'imagename')]"
    private val IMG_XPATH = "//a[@id='download']"

    override fun resolve(image: Image): Pair<String, String> {
        val document = fetchDocument(image.url)
        val titleNode = try {
            XpathUtils.getAsNode(document, TITLE_XPATH)
        } catch (e: XpathException) {
            throw HostException(e.message ?: "Xpath error")
        } ?: throw HostException("Xpath '$TITLE_XPATH' cannot be found in '${image.url}'")

        val urlNode = try {
            XpathUtils.getAsNode(document, IMG_XPATH)
        } catch (e: XpathException) {
            throw HostException(e.message ?: "Xpath error")
        } ?: throw HostException("Xpath '$IMG_XPATH' cannot be found in '${image.url}'")

        return try {
            var imgTitle = titleNode.textContent.trim()
            val imgUrl = urlNode.attributes.getNamedItem("href").textContent.trim()
            if (imgTitle.isEmpty()) {
                imgTitle = imgUrl.substring(imgUrl.lastIndexOf('/') + 1)
            }
            Pair(imgTitle, imgUrl)
        } catch (e: Exception) {
            throw HostException("Unexpected error occurred: ${e.message}")
        }
    }
}
