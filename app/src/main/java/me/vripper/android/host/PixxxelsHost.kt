package me.vripper.android.host

import me.vripper.android.domain.Image
import me.vripper.android.exception.HostException
import me.vripper.android.exception.XpathException
import me.vripper.android.util.XpathUtils

class PixxxelsHost : Host("pixxxels.cc", 12) {
    private val TAG = "PixxxelsHost"
    private val IMG_XPATH = "//*[@id='download']"
    private val TITLE_XPATH = "//*[contains(@class,'imagename')]"

    override fun resolve(image: Image): Pair<String, String> {
        val document = fetchDocument(image.url)
        val imgNode = try {
            XpathUtils.getAsNode(document, IMG_XPATH)
        } catch (e: XpathException) {
            throw HostException(e.message ?: "Xpath error")
        } ?: throw HostException("Xpath '$IMG_XPATH' cannot be found in '${image.url}'")

        val titleNode = try {
            XpathUtils.getAsNode(document, TITLE_XPATH)
        } catch (e: XpathException) {
            throw HostException(e.message ?: "Xpath error")
        } ?: throw HostException("Xpath '$TITLE_XPATH' cannot be found in '${image.url}'")

        return try {
            val imgTitle = titleNode.textContent.trim()
            val imgUrl = imgNode.attributes.getNamedItem("href").textContent.trim()
            val name = if (imgTitle.isEmpty()) imgUrl.substring(imgUrl.lastIndexOf('/') + 1) else imgTitle
            Pair(name, imgUrl)
        } catch (e: Exception) {
            throw HostException("Unexpected error occurred: ${e.message}")
        }
    }
}
