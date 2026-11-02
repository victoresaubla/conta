package com.appscz.cbfinance.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.appscz.cbfinance.data.CatTotal
import com.appscz.cbfinance.data.Dashboard
import com.appscz.cbfinance.data.Filtro
import com.appscz.cbfinance.data.ReporteCompleto
import com.appscz.cbfinance.data.ResumenPlanilla
import com.appscz.cbfinance.data.Rec
import com.appscz.cbfinance.data.Serie
import com.appscz.cbfinance.data.TipoDocStat
import com.appscz.cbfinance.data.dbl
import com.appscz.cbfinance.data.str
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfColors {
    val PRIMARY = Color.parseColor("#2C3E50")
    val GREEN = Color.parseColor("#10B759")
    val AMBER = Color.parseColor("#FFB900")
    val RED = Color.parseColor("#E81123")
    val BLUE = Color.parseColor("#0078D4")
    val TEAL = Color.parseColor("#00B294")
    val GRAY = Color.parseColor("#605E5C")
    val LIGHT = Color.parseColor("#F3F2F1")
}

/** Generador de PDF con la API nativa de Android (sin librerías externas). */
class PdfBuilder(private val empresa: String, private val titulo: String, private val subtitulo: String) {
    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas = Canvas()
    private var pageNo = 0
    private var y = 0f
    private val w = 595
    private val h = 842
    private val m = 36f

    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8.5f; color = Color.parseColor("#323130") }
    private val pBold = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8.5f; typeface = Typeface.DEFAULT_BOLD; color = Color.parseColor("#323130") }
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EDEBE9"); strokeWidth = 0.6f }

    init {
        newPage()
    }

    private fun finishPage() {
        val p = page ?: return
        val foot = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = PdfColors.GRAY }
        canvas.drawLine(m, h - 36f, w - m, h - 36f, pLine)
        canvas.drawText("C&B Consultores - Generado el " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date()), m, h - 22f, foot)
        val label = "Página $pageNo"
        canvas.drawText(label, w - m - foot.measureText(label), h - 22f, foot)
        doc.finishPage(p)
        page = null
    }

    private fun newPage() {
        finishPage()
        pageNo++
        val p = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
        page = p
        canvas = p.canvas
        pFill.color = PdfColors.PRIMARY
        canvas.drawRect(0f, 0f, w.toFloat(), 62f, pFill)
        val t1 = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 15f; typeface = Typeface.DEFAULT_BOLD; color = Color.WHITE }
        val t2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.parseColor("#D0D7DE") }
        canvas.drawText(fit(empresa, t1, w - 2 * m), m, 26f, t1)
        canvas.drawText(fit("$titulo  |  $subtitulo", t2, w - 2 * m), m, 46f, t2)
        y = 82f
    }

    private fun fit(s: String, p: Paint, maxW: Float): String {
        if (p.measureText(s) <= maxW) return s
        var t = s
        while (t.isNotEmpty() && p.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }

    private fun ensure(hh: Float) {
        if (y + hh > h - 50f) newPage()
    }

    fun section(text: String, color: Int = PdfColors.PRIMARY) {
        ensure(40f)
        y += 8f
        pFill.color = color
        canvas.drawRect(m, y, m + 4f, y + 14f, pFill)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; typeface = Typeface.DEFAULT_BOLD; this.color = color }
        canvas.drawText(text, m + 10f, y + 11f, p)
        y += 22f
    }

    fun note(text: String) {
        ensure(16f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8.5f; color = PdfColors.GRAY }
        canvas.drawText(fit(text, p, w - 2 * m), m, y + 9f, p)
        y += 16f
    }

    /** Tabla con encabezado de color. Primera columna a la izquierda, el resto a la derecha. */
    fun table(headers: List<String>, rows: List<List<String>>, weights: List<Float>, headerColor: Int, lastRowBold: Boolean = false, leftCols: Int = 1) {
        if (rows.isEmpty()) {
            note("Sin datos para mostrar")
            return
        }
        val totalW = w - 2 * m
        val sum = weights.sum()
        val widths = weights.map { it / sum * totalW }
        val rowH = 16f

        fun header() {
            pFill.color = headerColor
            canvas.drawRect(m, y, m + totalW, y + rowH, pFill)
            val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8.5f; typeface = Typeface.DEFAULT_BOLD; color = Color.WHITE }
            var x = m
            for (i in headers.indices) {
                val txt = fit(headers[i], hp, widths[i] - 8f)
                if (i < leftCols) canvas.drawText(txt, x + 4f, y + 11f, hp)
                else canvas.drawText(txt, x + widths[i] - 4f - hp.measureText(txt), y + 11f, hp)
                x += widths[i]
            }
            y += rowH
        }
        ensure(rowH * 3)
        header()
        for ((ri, r) in rows.withIndex()) {
            if (y + rowH > h - 50f) {
                newPage()
                header()
            }
            if (ri % 2 == 1) {
                pFill.color = PdfColors.LIGHT
                canvas.drawRect(m, y, m + totalW, y + rowH, pFill)
            }
            val bold = lastRowBold && ri == rows.size - 1
            val paint = if (bold) pBold else pText
            var x = m
            for (i in headers.indices) {
                val cell = if (i < r.size) r[i] else ""
                val txt = fit(cell, paint, widths[i] - 8f)
                if (i < leftCols) canvas.drawText(txt, x + 4f, y + 11f, paint)
                else canvas.drawText(txt, x + widths[i] - 4f - paint.measureText(txt), y + 11f, paint)
                x += widths[i]
            }
            canvas.drawLine(m, y + rowH, m + totalW, y + rowH, pLine)
            y += rowH
        }
        y += 6f
    }

    /** Gráfico de barras agrupadas por periodo (ventas, compras, gastos, régimen). */
    fun chart(s: Serie) {
        if (s.periodos.isEmpty()) {
            note("No hay datos de tendencias")
            return
        }
        val chartH = 150f
        ensure(chartH + 40f)
        val n = minOf(s.periodos.size, 12)
        val start = s.periodos.size - n
        val series = listOf(
            Pair(s.ventas.drop(start), PdfColors.GREEN),
            Pair(s.compras.drop(start), PdfColors.AMBER),
            Pair(s.gastos.drop(start), PdfColors.RED),
            Pair(s.regimen.drop(start), PdfColors.TEAL)
        )
        var max = 1.0
        for ((vals, _) in series) for (v in vals) if (v > max) max = v
        val left = m + 40f
        val top = y + 6f
        val bottom = top + chartH - 22f
        val width = w - m - left
        // ejes
        canvas.drawLine(left, top, left, bottom, pLine)
        canvas.drawLine(left, bottom, left + width, bottom, pLine)
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 7f; color = PdfColors.GRAY }
        canvas.drawText(Fmt.money(max), m, top + 6f, small)
        canvas.drawText("0", m + 28f, bottom, small)
        val groupW = width / n
        val barW = (groupW - 6f) / 4f
        for (g in 0 until n) {
            for (k in 0 until 4) {
                val v = series[k].first[g]
                val bh = (v / max * (bottom - top - 4f)).toFloat()
                pFill.color = series[k].second
                val x0 = left + g * groupW + 3f + k * barW
                canvas.drawRect(x0, bottom - bh, x0 + barW - 1f, bottom, pFill)
            }
            val lab = Fmt.mesLabel(s.periodos[start + g])
            canvas.drawText(lab, left + g * groupW + (groupW - small.measureText(lab)) / 2f, bottom + 10f, small)
        }
        // leyenda
        var lx = left
        val names = listOf("Ingresos", "Egresos", "Gastos", "Régimen")
        for (k in 0 until 4) {
            pFill.color = series[k].second
            canvas.drawRect(lx, bottom + 14f, lx + 8f, bottom + 22f, pFill)
            canvas.drawText(names[k], lx + 11f, bottom + 21f, small)
            lx += 70f
        }
        y += chartH + 14f
    }

    fun save(file: File) {
        finishPage()
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
    }
}

object PdfReports {

    private fun m(v: Double) = Fmt.bs(v)

    private fun periodo(f: Filtro): String =
        if (f.fi.isNotBlank() && f.ff.isNotBlank()) "Período: ${Fmt.fecha(f.fi)} al ${Fmt.fecha(f.ff)}" else "Período: todos los registros"

    private fun cats(b: PdfBuilder, titulo: String, data: List<CatTotal>, color: Int) {
        if (data.isEmpty()) return
        b.section(titulo, color)
        val total = data.sumOf { it.total }
        val rows = data.map { listOf(it.categoria, Fmt.money(it.total), if (total > 0) Fmt.pct(it.total / total * 100.0) else "0%") }.toMutableList()
        rows.add(listOf("TOTAL", Fmt.money(total), "100%"))
        b.table(listOf("Categoría", "Total (Bs.)", "%"), rows, listOf(3f, 1.5f, 1f), color, lastRowBold = true)
    }

    private fun tipoDoc(b: PdfBuilder, titulo: String, data: List<TipoDocStat>, color: Int) {
        if (data.isEmpty()) return
        b.section(titulo, color)
        val rows = data.map { listOf(it.tipo, it.cantidad.toString(), Fmt.money(it.total)) }.toMutableList()
        rows.add(listOf("TOTAL", data.sumOf { it.cantidad }.toString(), Fmt.money(data.sumOf { it.total })))
        b.table(listOf("Tipo de documento", "Cant.", "Total (Bs.)"), rows, listOf(3f, 1f, 1.5f), color, lastRowBold = true)
    }

    private fun planillas(b: PdfBuilder, data: List<ResumenPlanilla>) {
        if (data.isEmpty()) return
        b.section("Resumen de planillas por mes")
        val rows = data.map {
            listOf(it.mes, it.numEmpleados.toString(), Fmt.money(it.totalAfp), Fmt.money(it.otrosDescuentos), Fmt.money(it.horasExtras), Fmt.money(it.totalNeto))
        }.toMutableList()
        rows.add(listOf("TOTAL", data.sumOf { it.numEmpleados }.toString(), Fmt.money(data.sumOf { it.totalAfp }), Fmt.money(data.sumOf { it.otrosDescuentos }), Fmt.money(data.sumOf { it.horasExtras }), Fmt.money(data.sumOf { it.totalNeto })))
        b.table(listOf("Mes", "Empl.", "AFP", "Otros desc.", "Hrs. extra", "Total neto"), rows, listOf(1.6f, 0.8f, 1.2f, 1.2f, 1.2f, 1.4f), PdfColors.BLUE, lastRowBold = true)
    }

    private fun mesesDetalle(b: PdfBuilder, titulo: String, data: Map<String, List<Rec>>, color: Int) {
        if (data.isEmpty()) return
        b.section(titulo, color)
        val rows = ArrayList<List<String>>()
        for ((per, items) in data) {
            for (r in items) rows.add(listOf(Fmt.mesLabel(per), r.str("descripcion"), r.str("categoria"), Fmt.money(r.dbl("total"))))
        }
        b.table(listOf("Mes", "Descripción", "Categoría", "Total (Bs.)"), rows, listOf(1.2f, 3f, 2f, 1.4f), color, leftCols = 3)
    }

    fun dashboard(ctx: Context, empresa: String, d: Dashboard, f: Filtro): File {
        val b = PdfBuilder(empresa, "Dashboard financiero", periodo(f))
        val k = d.metricas
        b.section("Indicadores principales")
        b.table(
            listOf("Indicador", "Monto (Bs.)", "Registros"),
            listOf(
                listOf("Ingresos totales", Fmt.money(k.totalVentas), k.numVentas.toString()),
                listOf("Egresos (compras)", Fmt.money(k.totalCompras), k.numCompras.toString()),
                listOf("Gastos operativos", Fmt.money(k.totalGastos), k.numGastos.toString()),
                listOf("Régimen", Fmt.money(k.totalRegimen), k.numRegimen.toString()),
                listOf("Utilidad bruta", Fmt.money(k.utilidadBruta), ""),
                listOf("Utilidad neta", Fmt.money(k.utilidadNeta), ""),
                listOf("Margen de utilidad", Fmt.pct(k.margen), "")
            ),
            listOf(3f, 2f, 1f), PdfColors.PRIMARY
        )
        b.section("Cuentas pendientes", PdfColors.AMBER)
        b.table(
            listOf("Concepto", "Saldo (Bs.)", "Cuentas"),
            listOf(
                listOf("Por cobrar", Fmt.money(k.totalPorCobrar), k.numPorCobrar.toString()),
                listOf("Por pagar", Fmt.money(k.totalPorPagar), k.numPorPagar.toString())
            ),
            listOf(3f, 2f, 1f), PdfColors.AMBER
        )
        b.section("Tendencia mensual", PdfColors.BLUE)
        b.chart(d.serie)
        b.section("Ventas recientes", PdfColors.GREEN)
        b.table(
            listOf("Fecha", "Producto", "Cant.", "Total (Bs.)"),
            d.ventasRecientes.map { listOf(Fmt.fecha(it.str("fecha")), it.str("producto"), it.str("cantidad"), Fmt.money(it.dbl("total"))) },
            listOf(1.2f, 3.5f, 0.8f, 1.5f), PdfColors.GREEN, leftCols = 2
        )
        b.section("Compras recientes", PdfColors.AMBER)
        b.table(
            listOf("Fecha", "Producto", "Cant.", "Total (Bs.)"),
            d.comprasRecientes.map { listOf(Fmt.fecha(it.str("fecha")), it.str("producto"), it.str("cantidad"), Fmt.money(it.dbl("total"))) },
            listOf(1.2f, 3.5f, 0.8f, 1.5f), PdfColors.AMBER, leftCols = 2
        )
        planillas(b, d.planillas)
        mesesDetalle(b, "Servicios básicos por mes", d.servicios, PdfColors.TEAL)
        mesesDetalle(b, "Alquileres por mes", d.alquileres, PdfColors.RED)
        val file = File(Files.exportsDir(ctx), "dashboard_" + Files.stamp() + ".pdf")
        b.save(file)
        return file
    }

    fun reporte(ctx: Context, empresa: String, r: ReporteCompleto, f: Filtro): File {
        val extra = StringBuilder(periodo(f))
        if (f.razon.isNotBlank()) extra.append("  |  Razón social: ${f.razon}")
        if (f.tipoDoc.isNotBlank()) extra.append("  |  Documento: ${f.tipoDoc}")
        val b = PdfBuilder(empresa, "Reporte financiero", extra.toString())
        val k = r.metricas
        b.section("Indicadores principales")
        b.table(
            listOf("Indicador", "Monto (Bs.)", "Registros"),
            listOf(
                listOf("Ingresos totales", Fmt.money(k.totalVentas), k.numVentas.toString()),
                listOf("Egresos (compras)", Fmt.money(k.totalCompras), k.numCompras.toString()),
                listOf("Gastos", Fmt.money(k.totalGastos), k.numGastos.toString()),
                listOf("Régimen", Fmt.money(k.totalRegimen), k.numRegimen.toString()),
                listOf("Costo total", Fmt.money(k.costoTotal), ""),
                listOf("Utilidad bruta", Fmt.money(k.utilidadBruta), ""),
                listOf("Utilidad neta", Fmt.money(k.utilidadNeta), ""),
                listOf("Margen de utilidad", Fmt.pct(k.margen), "")
            ),
            listOf(3f, 2f, 1f), PdfColors.PRIMARY
        )
        b.section("Cuentas pendientes", PdfColors.AMBER)
        b.table(
            listOf("Concepto", "Saldo (Bs.)", "Cuentas"),
            listOf(
                listOf("Cuentas por cobrar", Fmt.money(k.totalPorCobrar), k.numPorCobrar.toString()),
                listOf("Cuentas por pagar", Fmt.money(k.totalPorPagar), k.numPorPagar.toString())
            ),
            listOf(3f, 2f, 1f), PdfColors.AMBER
        )
        b.section("Estado de flujo de efectivo por tipo de documento", PdfColors.BLUE)
        tipoDoc(b, "Ingresos", r.tipoDoc.ventas, PdfColors.GREEN)
        tipoDoc(b, "Egresos", r.tipoDoc.compras, PdfColors.AMBER)
        tipoDoc(b, "Gastos", r.tipoDoc.gastos, PdfColors.RED)
        tipoDoc(b, "Régimen", r.tipoDoc.regimen, PdfColors.TEAL)
        b.section("Análisis de tendencias", PdfColors.BLUE)
        b.chart(r.serie)
        planillas(b, r.resumenPlanillas)
        b.section("Distribución por categorías")
        cats(b, "Ventas por categoría", r.ventasCat, PdfColors.GREEN)
        cats(b, "Compras por categoría", r.comprasCat, PdfColors.AMBER)
        cats(b, "Gastos por categoría", r.gastosCat, PdfColors.RED)
        cats(b, "Régimen por categoría", r.regimenCat, PdfColors.TEAL)
        if (r.cobrar.isNotEmpty()) {
            b.section("Detalle de cuentas por cobrar", PdfColors.GREEN)
            b.table(
                listOf("Cliente", "Fecha", "Monto", "Saldo", "Estado"),
                r.cobrar.map { listOf(it.str("razon_social"), Fmt.fecha(it.str("fecha")), Fmt.money(it.dbl("monto_total")), Fmt.money(it.dbl("saldo")), it.str("estado")) },
                listOf(3f, 1.2f, 1.3f, 1.3f, 1.2f), PdfColors.GREEN
            )
        }
        if (r.pagar.isNotEmpty()) {
            b.section("Detalle de cuentas por pagar", PdfColors.RED)
            b.table(
                listOf("Proveedor", "Fecha", "Monto", "Saldo", "Estado"),
                r.pagar.map { listOf(it.str("razon_social"), Fmt.fecha(it.str("fecha")), Fmt.money(it.dbl("monto_total")), Fmt.money(it.dbl("saldo")), it.str("estado")) },
                listOf(3f, 1.2f, 1.3f, 1.3f, 1.2f), PdfColors.RED
            )
        }
        val file = File(Files.exportsDir(ctx), "reporte_" + Files.stamp() + ".pdf")
        b.save(file)
        return file
    }
}
