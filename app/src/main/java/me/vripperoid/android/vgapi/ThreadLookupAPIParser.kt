package me.vripperoid.android.vgapi

import me.vripperoid.android.exception.PostParseException
import me.vripperoid.android.util.LogUtils
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

class ThreadLookupAPIParser(private val threadId: Long, private val baseUrl: String = "https://vipergirls.to") : KoinComponent {

    private val client: OkHttpClient by inject()
    private val TAG = "ThreadLookupAPIParser"

    fun parse(): ThreadItem {
        val url = "$baseUrl/vr.php?t=$threadId"
        LogUtils.d(TAG, "Parsing $url")
        
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw PostParseException("Failed to fetch $url: ${response.code}")
        }
        
        val body = response.body?.string() ?: throw PostParseException("Empty body")
        
        val handler = ResponseHandler()
        SAXParserFactory.newInstance().newSAXParser().parse(InputSource(StringReader(body)), handler)
        
        return handler.result
    }
    
    private class ResponseHandler : DefaultHandler() {
        var result: ThreadItem = ThreadItem(0, "", "", "", emptyList(), "")
        private val postItemList = mutableListOf<PostItem>()
        private val imageItemList = mutableListOf<ImageItem>()
        
        private var threadId: Long = -1
        private var threadTitle: String = ""
        private var forum: String = ""
        private var securityToken: String = ""
        private var postId: Long = -1
        private var postCounter: Int = 0
        private var postTitle: String = ""
        
        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            when (qName.lowercase()) {
                "thread" -> {
                    threadId = attributes.getValue("id")?.toLongOrNull() ?: -1
                    threadTitle = attributes.getValue("title") ?: ""
                }
                "forum" -> forum = attributes.getValue("title") ?: ""
                "user" -> securityToken = attributes.getValue("hash") ?: ""
                "post" -> {
                    postId = attributes.getValue("id")?.toLongOrNull() ?: -1
                    postCounter = attributes.getValue("number")?.toIntOrNull() ?: 0
                    val title = attributes.getValue("title") ?: ""
                    postTitle = title.ifBlank { threadTitle }
                }
                "image" -> {
                    val mainUrl = attributes.getValue("main_url") ?: ""
                    val thumbUrl = attributes.getValue("thumb_url") ?: ""
                    val type = attributes.getValue("type") ?: ""
                    if (type == "linked") {
                        val hostId: Byte = when {
                            mainUrl.contains("dpic.me") -> 1
                            mainUrl.contains("imagebam.com") -> 2
                            mainUrl.contains("imagetwist.com") -> 3
                            mainUrl.contains("imagevenue.com") -> 4
                            mainUrl.contains("imagezilla.net") -> 5
                            mainUrl.contains("imgbox.com") -> 6
                            mainUrl.contains("imgspice.com") -> 7
                            mainUrl.contains("imx.to") -> 8
                            mainUrl.contains("pimpandhost.com") -> 9
                            mainUrl.contains("pixhost.to") -> 10
                            mainUrl.contains("pixroute.com") -> 11
                            mainUrl.contains("pixxxels.cc") -> 12
                            mainUrl.contains("postimg.cc") -> 13
                            mainUrl.contains("turboimagehost.com") -> 14
                            mainUrl.contains("vipr.im") -> 15
                            else -> 0
                        }
                        if (hostId != 0.toByte()) {
                            imageItemList.add(ImageItem(mainUrl, thumbUrl, hostId))
                        }
                    }
                }
            }
        }
        
        override fun endElement(uri: String, localName: String, qName: String) {
            if (qName.equals("post", true)) {
                if (imageItemList.isNotEmpty()) {
                    postItemList.add(PostItem(
                        threadId, threadTitle, postId, postCounter, postTitle,
                        imageItemList.size,
                        "https://vipergirls.to/threads/$threadId?p=$postId&viewfull=1#post$postId",
                        emptyList(), securityToken, forum, imageItemList.toList()
                    ))
                }
                imageItemList.clear()
            }
        }
        
        override fun endDocument() {
            result = ThreadItem(threadId, threadTitle, securityToken, forum, postItemList.toList(), "")
        }
    }
}
