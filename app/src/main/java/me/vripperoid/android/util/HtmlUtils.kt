package me.vripperoid.android.util

import me.vripperoid.android.exception.HtmlProcessorException
import org.jsoup.Jsoup
import org.jsoup.helper.W3CDom
import org.w3c.dom.Document
import java.io.InputStream

object HtmlUtils {

    @Throws(HtmlProcessorException::class)
    fun clean(htmlContent: InputStream): Document {
        return try {
            val jsoupDoc = Jsoup.parse(htmlContent, "UTF-8", "")
            W3CDom().fromJsoup(jsoupDoc)
        } catch (e: Exception) {
            throw HtmlProcessorException(e)
        }
    }

    @Throws(HtmlProcessorException::class)
    fun clean(htmlContent: String): Document {
        return try {
            val jsoupDoc = Jsoup.parse(htmlContent)
            W3CDom().fromJsoup(jsoupDoc)
        } catch (e: Exception) {
            throw HtmlProcessorException(e)
        }
    }
}
