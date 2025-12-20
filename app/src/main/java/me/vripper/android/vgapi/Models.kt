package me.vripper.android.vgapi

data class ThreadItem(
    val threadId: Long,
    val threadTitle: String,
    val securityToken: String,
    val forum: String,
    val postItemList: List<PostItem>,
    val error: String
)

data class PostItem(
    val threadId: Long,
    val threadTitle: String,
    val postId: Long,
    val postCounter: Int,
    val postTitle: String,
    val imageCount: Int,
    val url: String,
    val hosts: List<Pair<String, Int>>,
    val securityToken: String,
    val forum: String,
    val imageItemList: List<ImageItem>
)

data class ImageItem(
    val mainUrl: String,
    val thumbUrl: String,
    val host: Byte // Host ID
)
