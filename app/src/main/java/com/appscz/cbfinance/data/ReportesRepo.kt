package com.appscz.cbfinance.data

class Filtro(
    val fi: String = "",
    val ff: String = "",
    val razon: String = "",
    val tipoDoc: String = ""
) {
    val activo: Boolean get() = fi.isNotBlank() || ff.isNotBlank() || razon.isNotBlank() || tipoDoc.isNotBlank()
    val desde: String get() = if (fi.isBlank()) "0000-01-01" else fi
    val hasta: String get() = if (ff.isBlank()) "9999-12-31" else ff
    val conFechas: Boolean get() = fi.isNotBlank() && ff.isNotBlank()
}

class Metricas(
    val totalVentas: Double, val numVentas: Int,
    val totalCompras: Double, val numCompras: Int,
    val totalGastos: Double, val numGastos: Int,
    val totalRegimen: Double, val numRegimen: Int,
    val totalPorCobrar: Double, val numPorCobrar: Int,
    val totalPorPagar: Double, val numPorPagar: Int
) {
    val utilidadBruta: Double get() = totalVentas - totalCompras
    val utilidadNeta: Double get() = utilidadBruta - totalGastos - totalRegimen
    val costoTotal: Double get() = totalCompras + totalGastos + totalRegimen
    val margen: Double get() = if (totalVentas > 0) utilidadNeta / totalVentas * 100.0 else 0.0
}

class Serie(
    val periodos: List<String>,
    val ventas: List<Double>,
    val compras: List<Double>,
    val gastos: List<Double>,
    val regimen: List<Double>
)

class CatTotal(val categoria: String, val total: Double, val cantidad: Int)

class TipoDocStat(val tipo: String, val cantidad: Int, val total: Double)

class EstadisticasTipoDoc(
    val ventas: List<TipoDocStat>,
    val compras: List<TipoDocStat>,
    val gastos: List<TipoDocStat>,
    val regimen: List<TipoDocStat>
)

class ResumenPlanilla(
    val mes: String, val totalNeto: Double, val totalAfp: Double,
    val otrosDescuentos: Double, val horasExtras: Double, val numEmpleados: Int
)

class Dashboard(
    val metricas: Metricas,
    val serie: Serie,
    val ventasRecientes: List<Rec>,
    val comprasRecientes: List<Rec>,
    val planillas: List<ResumenPlanilla>,
    val servicios: Map<String, List<Rec>>,
    val alquileres: Map<String, List<Rec>>
)

class ReporteCompleto(
    val metricas: Metricas,
    val serie: Serie,
    val ventasCat: List<CatTotal>,
    val comprasCat: List<CatTotal>,
    val gastosCat: List<CatTotal>,
    val regimenCat: List<CatTotal>,
    val cobrar: List<Rec>,
    val pagar: List<Rec>,
    val tipoDoc: EstadisticasTipoDoc,
    val resumenPlanillas: List<ResumenPlanilla>,
    val detallePlanillas: List<Rec>
)

object ReportesRepo {

    private fun razonCond(f: Filtro, params: MutableList<Any?>): String {
        if (f.razon.isBlank()) return ""
        params.add("%" + f.razon.trim().lowercase() + "%")
        return " AND LOWER(TRIM(razon_social)) LIKE ?"
    }

    private fun tipoCond(f: Filtro, params: MutableList<Any?>): String {
        if (f.tipoDoc.isBlank()) return ""
        params.add(f.tipoDoc.trim())
        return " AND tipo_documento = ?"
    }

    /** WHERE para tablas con columna fecha. Sin filtro, solo empresa. */
    private fun whereClause(empresaId: Long, f: Filtro, dateCol: String = "fecha"): Pair<String, List<Any?>> {
        val p = ArrayList<Any?>()
        p.add(empresaId)
        val sb = StringBuilder(" WHERE empresa_id = ?")
        if (f.activo) {
            sb.append(" AND $dateCol BETWEEN ? AND ?")
            p.add(f.desde); p.add(f.hasta)
            sb.append(razonCond(f, p))
            sb.append(tipoCond(f, p))
        }
        return Pair(sb.toString(), p)
    }

    fun metricas(empresaId: Long, f: Filtro): Metricas {
        val db = DbManager.db()
        fun suma(tabla: String): Pair<Double, Int> {
            val (w, p) = whereClause(empresaId, f)
            val r = db.row("SELECT COALESCE(SUM(total), 0) as t, COUNT(*) as n FROM $tabla$w", p)
            return Pair(r?.dbl("t") ?: 0.0, r?.int("n") ?: 0)
        }
        fun cuentas(tabla: String): Pair<Double, Int> {
            return try {
                val (w, p) = whereClause(empresaId, f, "fecha_emision")
                val r = db.row("SELECT COALESCE(SUM(saldo), 0) as t, COUNT(*) as n FROM $tabla$w AND saldo > 0", p)
                Pair(r?.dbl("t") ?: 0.0, r?.int("n") ?: 0)
            } catch (e: Exception) {
                Pair(0.0, 0)
            }
        }
        val v = suma("ventas")
        val c = suma("compras")
        val g = suma("gastos")
        val r = suma("regimen")
        val cc = cuentas("cuentas_cobrar")
        val cp = cuentas("cuentas_pagar")
        return Metricas(v.first, v.second, c.first, c.second, g.first, g.second, r.first, r.second, cc.first, cc.second, cp.first, cp.second)
    }

    private fun periodos(tabla: String, empresaId: Long, f: Filtro, ultimos6: Boolean): Map<String, Double> {
        val (w, p) = whereClause(empresaId, f)
        val sql = if (ultimos6) {
            "SELECT strftime('%Y-%m', fecha) as periodo, SUM(total) as total FROM $tabla$w GROUP BY strftime('%Y-%m', fecha) ORDER BY periodo DESC LIMIT 6"
        } else {
            "SELECT strftime('%Y-%m', fecha) as periodo, SUM(total) as total FROM $tabla$w GROUP BY strftime('%Y-%m', fecha) ORDER BY periodo ASC"
        }
        val out = LinkedHashMap<String, Double>()
        for (r in DbManager.db().rows(sql, p)) {
            val per = r.str("periodo")
            if (per.isNotEmpty()) out[per] = r.dbl("total")
        }
        return out
    }

    fun serie(empresaId: Long, f: Filtro, ultimos6: Boolean): Serie {
        val v = periodos("ventas", empresaId, f, ultimos6)
        val c = periodos("compras", empresaId, f, ultimos6)
        val g = periodos("gastos", empresaId, f, ultimos6)
        val r = periodos("regimen", empresaId, f, ultimos6)
        val all = (v.keys + c.keys + g.keys + r.keys).toSortedSet().toList()
        return Serie(
            all,
            all.map { v[it] ?: 0.0 }, all.map { c[it] ?: 0.0 },
            all.map { g[it] ?: 0.0 }, all.map { r[it] ?: 0.0 }
        )
    }

    fun categorias(tabla: String, empresaId: Long, f: Filtro): List<CatTotal> {
        val (w, p) = whereClause(empresaId, f)
        return DbManager.db().rows(
            "SELECT COALESCE(categoria, 'Sin categoría') as categoria, SUM(total) as total, COUNT(*) as cantidad FROM $tabla$w GROUP BY COALESCE(categoria, 'Sin categoría') ORDER BY total DESC",
            p
        ).map { CatTotal(it.str("categoria"), it.dbl("total"), it.int("cantidad")) }
    }

    fun detalleCuentas(tabla: String, empresaId: Long, f: Filtro): List<Rec> {
        return try {
            val (w, p) = whereClause(empresaId, f, "fecha_emision")
            DbManager.db().rows(
                "SELECT id, razon_social, fecha_emision as fecha, monto_total, saldo, tipo_documento, numero_documento, estado FROM $tabla$w AND saldo > 0 ORDER BY fecha_emision DESC, id DESC LIMIT 100",
                p
            )
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun estadisticasTipoDoc(empresaId: Long, f: Filtro): EstadisticasTipoDoc {
        val db = DbManager.db()
        val p = ArrayList<Any?>()
        p.add(empresaId)
        val sb = StringBuilder(" WHERE empresa_id = ?")
        if (f.conFechas) {
            sb.append(" AND fecha BETWEEN ? AND ?"); p.add(f.fi); p.add(f.ff)
        }
        if (f.razon.isNotBlank()) {
            sb.append(" AND LOWER(TRIM(razon_social)) LIKE ?"); p.add("%" + f.razon.trim().lowercase() + "%")
        }
        fun stat(tabla: String): List<TipoDocStat> = try {
            db.rows("SELECT tipo_documento, COUNT(*) as cantidad, SUM(total) as total FROM $tabla$sb GROUP BY tipo_documento ORDER BY total DESC", p)
                .map {
                    val t = it.str("tipo_documento")
                    TipoDocStat(if (t.isBlank()) "Sin especificar" else t, it.int("cantidad"), it.dbl("total"))
                }
        } catch (e: Exception) {
            emptyList()
        }
        return EstadisticasTipoDoc(stat("ventas"), stat("compras"), stat("gastos"), stat("regimen"))
    }

    fun razonesSocialesTodas(empresaId: Long): List<String> {
        val db = DbManager.db()
        val set = java.util.TreeSet<String>()
        for (t in listOf("ventas", "compras", "gastos", "cuentas_cobrar", "cuentas_pagar")) {
            try {
                for (r in db.rows("SELECT DISTINCT TRIM(razon_social) as r FROM $t WHERE empresa_id = ? AND razon_social IS NOT NULL AND TRIM(razon_social) != ''", listOf(empresaId))) {
                    set.add(r.str("r"))
                }
            } catch (_: Exception) {
            }
        }
        return set.toList()
    }

    fun tiposDocumentoTodos(empresaId: Long): List<String> {
        val db = DbManager.db()
        val set = java.util.TreeSet<String>()
        for (t in listOf("ventas", "compras", "gastos", "cuentas_cobrar", "cuentas_pagar")) {
            try {
                for (r in db.rows("SELECT DISTINCT tipo_documento as r FROM $t WHERE empresa_id = ? AND tipo_documento IS NOT NULL AND TRIM(tipo_documento) != ''", listOf(empresaId))) {
                    set.add(r.str("r"))
                }
            } catch (_: Exception) {
            }
        }
        return set.toList()
    }

    private fun resumenPlanillas(empresaId: Long, f: Filtro, limit6: Boolean): List<ResumenPlanilla> {
        val p = ArrayList<Any?>()
        p.add(empresaId)
        var w = " WHERE empresa_id = ?"
        if (f.activo) {
            w += " AND fecha BETWEEN ? AND ?"; p.add(f.desde); p.add(f.hasta)
        }
        val order = if (limit6) "ORDER BY MAX(fecha) DESC LIMIT 6" else "ORDER BY mes DESC"
        return try {
            DbManager.db().rows(
                """SELECT mes, SUM(total_neto) as total_neto, SUM(descuento_afp_monto) as total_afp,
                          SUM(descuento_caja_nacional + descuento_ministerio_trabajo) as otros,
                          SUM(horas_extras) as horas, COUNT(*) as n
                   FROM planillas$w GROUP BY mes $order""",
                p
            ).map { ResumenPlanilla(it.str("mes"), it.dbl("total_neto"), it.dbl("total_afp"), it.dbl("otros"), it.dbl("horas"), it.int("n")) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun detallePlanillas(empresaId: Long, f: Filtro): List<Rec> {
        val p = ArrayList<Any?>()
        p.add(empresaId)
        var w = " WHERE empresa_id = ?"
        if (f.activo) {
            w += " AND fecha BETWEEN ? AND ?"; p.add(f.desde); p.add(f.hasta)
        }
        return try {
            DbManager.db().rows(
                """SELECT id, fecha, empleado, mes, monto_bruto, descuento_afp_porcentaje, descuento_afp_monto,
                          descuento_caja_nacional, descuento_ministerio_trabajo, horas_extras, total_neto, observaciones
                   FROM planillas$w ORDER BY fecha DESC, id DESC LIMIT 100""",
                p
            )
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun gastosPorMes(empresaId: Long, f: Filtro, condiciones: String): Map<String, List<Rec>> {
        val p = ArrayList<Any?>()
        p.add(empresaId)
        var w = " WHERE empresa_id = ?"
        if (f.activo) {
            w += " AND fecha BETWEEN ? AND ?"; p.add(f.desde); p.add(f.hasta)
        }
        return try {
            val rows = DbManager.db().rows(
                """SELECT strftime('%Y-%m', fecha) as periodo, categoria, descripcion, SUM(total) as total
                   FROM gastos$w AND ($condiciones)
                   GROUP BY strftime('%Y-%m', fecha), categoria, descripcion
                   ORDER BY periodo DESC, total DESC""",
                p
            )
            val out = LinkedHashMap<String, MutableList<Rec>>()
            for (r in rows) out.getOrPut(r.str("periodo")) { ArrayList() }.add(r)
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private const val COND_SERVICIOS = """LOWER(categoria) = 'servicios' OR LOWER(categoria) LIKE '%servicio%' OR LOWER(descripcion) LIKE '%luz%'
        OR LOWER(descripcion) LIKE '%agua%' OR LOWER(descripcion) LIKE '%internet%' OR LOWER(descripcion) LIKE '%gas%'
        OR LOWER(descripcion) LIKE '%electricidad%' OR LOWER(descripcion) LIKE '%telefono%' OR LOWER(descripcion) LIKE '%teléfono%'"""

    private const val COND_ALQUILER = """LOWER(categoria) = 'alquiler' OR LOWER(categoria) LIKE '%alquiler%' OR LOWER(categoria) LIKE '%arriendo%'
        OR LOWER(descripcion) LIKE '%alquiler%' OR LOWER(descripcion) LIKE '%arriendo%' OR LOWER(descripcion) LIKE '%renta%'"""

    private fun recientes(tabla: String, empresaId: Long, f: Filtro): List<Rec> {
        val (w, p) = whereClause(empresaId, Filtro(f.fi, f.ff))
        return DbManager.db().rows(
            "SELECT fecha, producto, cantidad, total, tipo_documento FROM $tabla$w ORDER BY fecha DESC, id DESC LIMIT 5", p
        )
    }

    fun dashboard(empresaId: Long, f: Filtro): Dashboard {
        val fd = Filtro(f.fi, f.ff)
        return Dashboard(
            metricas(empresaId, fd),
            serie(empresaId, fd, !fd.activo),
            recientes("ventas", empresaId, fd),
            recientes("compras", empresaId, fd),
            resumenPlanillas(empresaId, fd, !fd.activo),
            gastosPorMes(empresaId, fd, COND_SERVICIOS),
            gastosPorMes(empresaId, fd, COND_ALQUILER)
        )
    }

    fun reporte(empresaId: Long, f: Filtro): ReporteCompleto = ReporteCompleto(
        metricas(empresaId, f),
        serie(empresaId, f, false),
        categorias("ventas", empresaId, f),
        categorias("compras", empresaId, f),
        categorias("gastos", empresaId, f),
        categorias("regimen", empresaId, f),
        detalleCuentas("cuentas_cobrar", empresaId, f),
        detalleCuentas("cuentas_pagar", empresaId, f),
        estadisticasTipoDoc(empresaId, f),
        resumenPlanillas(empresaId, f, false),
        detallePlanillas(empresaId, f)
    )
}
