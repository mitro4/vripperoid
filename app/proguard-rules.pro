# Keep Koin
-keep class org.koin.** { *; }

# Keep Room models
-keep class androidx.room.** { *; }
-keep class me.vripper.android.store.** { *; }

# OkHttp/Jsoup
-dontwarn okhttp3.**
-dontwarn org.jsoup.**
