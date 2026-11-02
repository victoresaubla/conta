package com.appscz.cbfinance.util

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.core.content.FileProvider
import com.appscz.cbfinance.data.DbManager
import com.appscz.cbfinance.data.OpResult
import com.appscz.cbfinance.data.Schema
import com.appscz.cbfinance.data.Session
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Files {
    fun exportsDir(ctx: Context): File {
        val d = File(ctx.cacheDir, "exports")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    fun uriFor(ctx: Context, f: File): Uri =
        FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)

    fun shareIntent(ctx: Context, f: File, mime: String, title: String): Intent {
        val send = Intent(Intent.ACTION_SEND)
        send.type = mime
        send.putExtra(Intent.EXTRA_STREAM, uriFor(ctx, f))
        send.putExtra(Intent.EXTRA_SUBJECT, f.name)
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, title)
    }

    fun displayName(ctx: Context, uri: Uri): String {
        var name = ""
        try {
            val c = ctx.contentResolver.query(uri, null, null, null, null)
            if (c != null) {
                val idx = c.getColumnIndex("_display_name")
                if (idx >= 0 && c.moveToFirst()) name = c.getString(idx) ?: ""
                c.close()
            }
        } catch (_: Exception) {
        }
        if (name.isEmpty()) name = uri.lastPathSegment ?: "archivo"
        return name
    }
}

/** Respaldo y restauración de la base de datos local (finanzas.db). */
object Backup {
    const val MIME_DB = "application/octet-stream"

    fun suggestedName(): String = "CB_Consultores_Respaldo_" + Files.stamp() + ".db"

    private fun autoDir(ctx: Context): File {
        val d = File(ctx.filesDir, "backups")
        if (!d.exists()) d.mkdirs()
        return d
    }

    /** Copia consistente de la base de datos actual a un archivo temporal. */
    fun snapshot(ctx: Context): File {
        DbManager.flush()
        val out = File(Files.exportsDir(ctx), suggestedName())
        DbManager.dbFile().copyTo(out, overwrite = true)
        return out
    }

    /** Guarda el respaldo en la ubicación elegida por el usuario (Descargas, Drive, USB, etc.). */
    fun saveTo(ctx: Context, uri: Uri): OpResult {
        return try {
            DbManager.flush()
            val os = ctx.contentResolver.openOutputStream(uri, "wt") ?: return OpResult(false, "No se pudo abrir el destino")
            os.use { out ->
                DbManager.dbFile().inputStream().use { it.copyTo(out) }
            }
            OpResult(true, "Respaldo guardado correctamente")
        } catch (e: Exception) {
            OpResult(false, "Error al guardar el respaldo: ${e.message}")
        }
    }

    /** Verifica que el archivo sea una base de datos SQLite válida del sistema. */
    fun validate(f: File): String? {
        if (!f.exists() || f.length() < 100) return "El archivo está vacío o dañado"
        try {
            val header = ByteArray(16)
            f.inputStream().use { it.read(header) }
            if (String(header, Charsets.ISO_8859_1).substring(0, 15) != "SQLite format 3") {
                return "El archivo no es una base de datos SQLite válida"
            }
        } catch (e: Exception) {
            return "No se pudo leer el archivo"
        }
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READONLY)
            val c = db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null)
            val names = HashSet<String>()
            while (c.moveToNext()) names.add(c.getString(0))
            c.close()
            val faltan = Schema.REQUIRED_TABLES.filter { !names.contains(it) }
            if (faltan.isNotEmpty()) return "No es una base de datos de este sistema (faltan tablas: ${faltan.joinToString(", ")})"
            val ic = db.rawQuery("PRAGMA integrity_check", null)
            var ok = true
            if (ic.moveToFirst()) ok = ic.getString(0) == "ok"
            ic.close()
            if (!ok) return "La base de datos está dañada (falló la verificación de integridad)"
        } catch (e: Exception) {
            return "No se pudo abrir la base de datos: ${e.message}"
        } finally {
            try {
                db?.close()
            } catch (_: Exception) {
            }
        }
        return null
    }

    /** Restaura desde un archivo elegido por el usuario. Antes guarda una copia de seguridad de lo actual. */
    fun restoreFrom(ctx: Context, uri: Uri): OpResult {
        return try {
            val tmp = File(ctx.cacheDir, "restore_tmp.db")
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            } ?: return OpResult(false, "No se pudo leer el archivo seleccionado")
            restoreFromFile(ctx, tmp)
        } catch (e: Exception) {
            OpResult(false, "Error al restaurar: ${e.message}")
        }
    }

    fun restoreFromFile(ctx: Context, src: File): OpResult {
        val err = validate(src)
        if (err != null) return OpResult(false, err)
        return try {
            // Copia de seguridad previa por si el usuario se equivoca de archivo
            DbManager.flush()
            val pre = File(autoDir(ctx), "antes_de_restaurar.db")
            DbManager.dbFile().copyTo(pre, overwrite = true)
            DbManager.replaceWith(src)
            Session.logout()
            OpResult(true, "Base de datos restaurada correctamente. Inicia sesión nuevamente.")
        } catch (e: Exception) {
            OpResult(false, "Error al restaurar: ${e.message}")
        }
    }

    // ----------------------------------------------------- Copias automáticas internas (diarias)
    fun autoBackupIfNeeded(ctx: Context) {
        try {
            val prefs = ctx.getSharedPreferences("cb_backup", Context.MODE_PRIVATE)
            val today = Fmt.today()
            if (prefs.getString("last_auto", "") == today) return
            DbManager.flush()
            val f = File(autoDir(ctx), "auto_" + today.replace("-", "") + ".db")
            DbManager.dbFile().copyTo(f, overwrite = true)
            val autos = autoDir(ctx).listFiles { x -> x.name.startsWith("auto_") }?.sortedBy { it.name } ?: emptyList()
            if (autos.size > 7) {
                for (old in autos.take(autos.size - 7)) old.delete()
            }
            prefs.edit().putString("last_auto", today).apply()
        } catch (_: Exception) {
        }
    }

    fun listAuto(ctx: Context): List<File> =
        (autoDir(ctx).listFiles { x -> x.name.endsWith(".db") }?.toList() ?: emptyList()).sortedByDescending { it.name }
}
