package com.example.darkmusic

import android.app.Application
import com.example.darkmusic.core.network.NewPipeDownloader
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization

@HiltAndroidApp
class DarkMusicApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Inicialización con localización explícita para evitar problemas con tendencias
        NewPipe.init(
            NewPipeDownloader(OkHttpClient()),
            Localization("US", "en")
        )
    }
}
