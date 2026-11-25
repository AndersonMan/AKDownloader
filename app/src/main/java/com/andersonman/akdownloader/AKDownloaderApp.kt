package com.andersonman.akdownloader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.room.Room
import com.andersonman.akdownloader.data.db.AppDatabase
import com.andersonman.akdownloader.data.net.SegmentedDownloader
import com.andersonman.akdownloader.data.repo.DownloadRepository
import com.andersonman.akdownloader.ui.crash.CrashReporter
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AKDownloaderApp : Application() {

    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "akdownloader.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    val downloadDao get() = database.downloadDao()

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    val downloader: SegmentedDownloader by lazy {
        SegmentedDownloader(okHttpClient, SegmentedDownloader.DEFAULT_SEGMENTS)
    }

    val downloadRepository: DownloadRepository by lazy {
        DownloadRepository(downloadDao)
    }

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Téléchargements",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Progression des téléchargements"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "ak_downloads"
    }
}
