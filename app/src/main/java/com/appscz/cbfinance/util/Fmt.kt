package com.appscz.cbfinance.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale

/** Ejecuta trabajo pesado (base de datos, archivos) fuera del hilo principal. */
suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

object Fmt {
    fun money(v: Double): String = String.format(Locale.US, "%,.2f", v)

    fun bs(v: Double): String = "Bs. " + money(v)

    fun qty(v: Double): String =
        if (v == Math.floor(v) && Math.abs(v) < 1.0e12) v.toLong().toString() else String.format(Locale.US, "%.2f", v)

    fun pct(v: Double): String = String.format(Locale.US, "%.1f%%", v)

    fun today(): String = LocalDate.now().toString()

    fun firstOfMonth(): String = LocalDate.now().withDayOfMonth(1).toString()

    fun lastOfMonth(): String {
        val d = LocalDate.now()
        return d.withDayOfMonth(d.lengthOfMonth()).toString()
    }

    /** yyyy-MM-dd -> dd/MM/yyyy (solo para mostrar, igual que formatear_fecha del sistema web) */
    fun fecha(s: String?): String {
        if (s.isNullOrBlank()) return ""
        return try {
            val d = LocalDate.parse(s.trim().take(10))
            String.format(Locale.US, "%02d/%02d/%04d", d.dayOfMonth, d.monthValue, d.year)
        } catch (e: Exception) {
            s
        }
    }

    fun currentMonth(): String = LocalDate.now().toString().take(7)

    fun parseDouble(s: String?): Double? {
        if (s == null) return null
        var t = s.trim()
        if (t.isEmpty()) return null
        t = if (t.contains(",") && t.contains(".")) t.replace(",", "") else t.replace(",", ".")
        return t.toDoubleOrNull()
    }

    fun dbl(s: String?, default: Double = 0.0): Double = parseDouble(s) ?: default

    /** Normaliza fechas de Excel/CSV a yyyy-MM-dd. Acepta dd/MM/yyyy, yyyy-MM-dd, con hora, o número serial de Excel. */
    fun normalizeDate(raw: String?): String {
        if (raw == null) return ""
        val s = raw.trim()
        if (s.isEmpty()) return ""
        // Número serial de Excel
        val serial = s.toDoubleOrNull()
        if (serial != null && serial > 20000 && serial < 80000) {
            return LocalDate.of(1899, 12, 30).plusDays(serial.toLong()).toString()
        }
        val datePart = s.split(" ")[0].split("T")[0]
        try {
            if (datePart.contains("/")) {
                val p = datePart.split("/")
                if (p.size == 3) {
                    val d = p[0].toInt()
                    val m = p[1].toInt()
                    var y = p[2].toInt()
                    if (y < 100) y += 2000
                    return LocalDate.of(y, m, d).toString()
                }
            }
            if (datePart.contains("-")) {
                val p = datePart.split("-")
                if (p.size == 3) {
                    return if (p[0].length == 4) LocalDate.of(p[0].toInt(), p[1].toInt(), p[2].toInt()).toString()
                    else LocalDate.of(p[2].toInt(), p[1].toInt(), p[0].toInt()).toString()
                }
            }
        } catch (e: Exception) {
            return s
        }
        return s
    }

    fun mesLabel(ym: String): String {
        val meses = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
        return try {
            val p = ym.split("-")
            meses[p[1].toInt() - 1] + " " + p[0]
        } catch (e: Exception) {
            ym
        }
    }
}
