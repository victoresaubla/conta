package com.appscz.cbfinance.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException

/** Guarda archivos directamente en la carpeta Descargas/CB_Consultores del dispositivo. */
object DownloadsSaver {
    const val FOLDER = "CB_Consultores"

    /** Copia el archivo a Descargas y devuelve la ruta visible para el usuario. */
    fun save(ctx: Context, src: File, mime: String): String {
        val name = src.name
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = ctx.contentResolver
            val cv = ContentValues()
            cv.put(MediaStore.Downloads.DISPLAY_NAME, name)
            cv.put(MediaStore.Downloads.MIME_TYPE, mime)
            cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER)
            cv.put(MediaStore.Downloads.IS_PENDING, 1)
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                ?: throw IOException("No se pudo crear el archivo en Descargas")
            val out = resolver.openOutputStream(uri) ?: throw IOException("No se pudo escribir en Descargas")
            out.use { o -> src.inputStream().use { it.copyTo(o) } }
            val done = ContentValues()
            done.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, done, null, null)
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
            if (!dir.exists() && !dir.mkdirs()) throw IOException("Sin permiso para escribir en Descargas")
            src.copyTo(File(dir, name), overwrite = true)
        }
        return "Descargas/$FOLDER/$name"
    }

    /**
     * Intenta guardar en Descargas. Si el sistema no lo permite (por ejemplo, Android 8/9 sin permiso),
     * abre el menú de compartir como alternativa para que el archivo no se pierda.
     */
    fun deliver(ctx: Context, src: File, mime: String): String {
        return try {
            save(ctx, src, mime)
        } catch (e: Exception) {
            val i = Files.shareIntent(ctx, src, mime, "Guardar " + src.name)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            "(elige dónde guardar el archivo)"
        }
    }
}
