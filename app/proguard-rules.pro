# Keep Koin
-keep class org.koin.** { *; }

# Keep Room models
-keep class androidx.room.** { *; }
-keep class me.vripperoid.android.settings.** { *; }

# OkHttp/Jsoup
-dontwarn okhttp3.**
-dontwarn org.jsoup.**
