package me.vripper.android.di

import androidx.room.Room
import me.vripper.android.data.AppDatabase
import me.vripper.android.host.*
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

val appModule = module {
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
