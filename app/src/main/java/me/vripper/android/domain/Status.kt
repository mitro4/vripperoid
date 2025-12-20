package me.vripper.android.domain

enum class Status(val stringValue: String) {
    PENDING("Pending"),
    DOWNLOADING("Downloading"),
    FINISHED("Finished"),
    ERROR("Error"),
    STOPPED("Stopped"),
    ALREADY_DOWNLOADED("Already Downloaded"),
    NOT_FULL_FINISHED("Not Full Finished")
}
