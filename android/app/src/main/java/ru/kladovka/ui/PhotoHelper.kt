package ru.kladovka.ui

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object PhotoHelper {

    /** Создаёт временный Uri для камеры через FileProvider. */
    fun createCaptureUri(context: Context): Uri {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file
        )
    }
}