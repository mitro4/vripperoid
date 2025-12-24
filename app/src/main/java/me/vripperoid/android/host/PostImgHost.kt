package me.vripperoid.android.host

import me.vripperoid.android.domain.Image
import me.vripperoid.android.exception.HostException
import me.vripperoid.android.exception.XpathException
import me.vripperoid.android.util.XpathUtils

class PostImgHost : Host("postimg.cc", 13) {
    private val TAG = "PostImgHost"
    private val TITLE_XPATH = "//span[contains(@class,'imagename')]"

    override fun resolve(image: Image): Pair<String, String> {
        val document = fetchDocument(image.url)
        val titleNode = try {
            XpathUtils.getAsNode(document, TITLE_XPATH)
        } catch (e: XpathException) {
            null
        }

        var imgUrl: String? = null
        
        // Strategy 1: Download button
        try {
            val node = XpathUtils.getAsNode(document, "//a[@id='download']")
            imgUrl = node?.attributes?.getNamedItem("href")?.textContent
        } catch (e: Exception) { }

        // Strategy 2: Main Image (zoom class)
        if (imgUrl.isNullOrEmpty()) {
            try {
                val node = XpathUtils.getAsNode(document, "//img[contains(@class,'zoom')]")
                imgUrl = node?.attributes?.getNamedItem("src")?.textContent
            } catch (e: Exception) { }
        }

        // Strategy 3: Direct Link Input
        if (imgUrl.isNullOrEmpty()) {
            try {
                val node = XpathUtils.getAsNode(document, "//input[@id='direct']")
                imgUrl = node?.attributes?.getNamedItem("value")?.textContent
            } catch (e: Exception) { }
        }

        if (imgUrl.isNullOrEmpty()) {
             throw HostException("Could not find image url in '${image.url}'")
        }

        return try {
            var imgTitle = titleNode?.textContent?.trim() ?: ""
            val finalUrl = imgUrl!!.trim()
            if (imgTitle.isEmpty()) {
                imgTitle = finalUrl.substring(finalUrl.lastIndexOf('/') + 1)
            }
            Pair(imgTitle, finalUrl)
        } catch (e: Exception) {
            throw HostException("Unexpected error occurred: ${e.message}")
        }
    }
}
