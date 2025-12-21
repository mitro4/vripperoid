package me.vripperoid.android.host

import me.vripperoid.android.domain.Image
import java.util.Locale

class ImxHost : Host("imx.to", 8) {

    override fun resolve(image: Image): Pair<String, String> {
        val imgTitle = String.format(Locale.US, "IMG_%04d", image.index + 1)
        val imgUrl = image.thumbUrl
            .replace("http:", "https:")
            .replace("upload/small/", "u/i/")
            .replace("u/t/", "u/i/")
        return Pair(imgTitle, imgUrl)
    }
}
