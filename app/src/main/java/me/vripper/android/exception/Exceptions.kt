package me.vripper.android.exception

class HtmlProcessorException(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    constructor(cause: Throwable) : this(null, cause)
}

class XpathException(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    constructor(cause: Throwable) : this(null, cause)
}

class DownloadException(message: String) : Exception(message)

class HostException(message: String) : Exception(message)

class PostParseException(message: String) : Exception(message)
