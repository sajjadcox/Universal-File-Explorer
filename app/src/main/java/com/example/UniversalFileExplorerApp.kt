package com.example

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import com.example.data.db.AppDatabase
import com.example.data.db.LocalDataRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class UniversalFileExplorerApp : Application(), ImageLoaderFactory {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val localDataRepository: LocalDataRepository by lazy { LocalDataRepository(database) }

    override fun onCreate() {
        super.onCreate()
        try {
            PDFBoxResourceLoader.init(applicationContext)
        } catch (_: Throwable) {
            // Safe fallback if initialized in unit tests
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(SvgDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }
}
