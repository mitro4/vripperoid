package me.vripperoid.android.di

import androidx.room.Room
import me.vripperoid.android.data.AppDatabase
import me.vripperoid.android.host.*
import me.vripperoid.android.settings.SettingsStore
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

val appModule = module {
    single { SettingsStore(androidContext()) }

    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java, "vripper-db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }
    single { get<AppDatabase>().postDao() }
    single { get<AppDatabase>().imageDao() }
    
    single {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    // Register Hosts
    factory { DPicMeHost() }
    factory { ImageBamHost() }
    factory { ImageTwistHost() }
    factory { ImageVenueHost() }
    factory { ImageZillaHost() }
    factory { ImgboxHost() }
    factory { ImgSpiceHost() }
    factory { ImxHost() }
    factory { PimpandhostHost() }
    factory { PixhostHost() }
    factory { PixRouteHost() }
    factory { PixxxelsHost() }
    factory { PostImgHost() }
    factory { TurboImageHost() }
    factory { ViprImHost() }
}
