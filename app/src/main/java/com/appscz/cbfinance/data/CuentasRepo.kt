package com.appscz.cbfinance.data

import com.appscz.cbfinance.util.Fmt

enum class CuentaTipo(
    val table: String,
    val pagosTable: String,
    val fkPagos: String,
    val title: String,
    val party: String,
    val totalLabel: String,
    val doneLabel: String
) {
    COBRAR("cuentas_cobrar", "pagos_cuentas_cobrar", "cuenta_cobrar_id", "Cuentas por Cobrar", "Cliente", "Total por Cobrar", "Total Cobrado"),
    PAGAR("cuentas_pagar", "pagos_cuentas_pagar", "cuenta_pagar_id", "Cuentas por Pagar", "Proveedor", "Total por Pagar", "Total Pagado")
}

class CuentasResult(
    val rows: List<Rec>,
    val partes: List<String>,
    val totalPendiente: Double,
    val totalHecho: Double,
    val totalVencido: Double,
    val totalPorVencer: Double
)

class CuentaInput(
    val razonSocial: String,
    val nitCi: String,
    val tipoDocumento: String,
    val concepto: String,
    val montoTotal: Double,
    val fechaEmision: String,
    val fechaVencimiento: String,
    val observaciones: String,
    val numeroDocumento: String = ""
)

object CuentasRepo {

    fun listar(tipo: CuentaTipo, empresaId: Long, fi: String, ff: String, parte: String, estado: String): CuentasResult {
        val db = DbManager.db()
        val sql = StringBuilder("SELECT * FROM ${tipo.table} WHERE empresa_id = ?")
        val params = ArrayList<Any?>()
        params.add(empresaId)
        if (fi.isNotBlank()) {
            sql.append(" AND fecha_emision >= ?"); params.add(fi)
        }
        if (ff.isNotBlank()) {
            sql.append(" AND fecha_emision <= ?"); params.add(ff)
        }
        if (parte.isNotBlank()) {
            sql.append(" AND razon_social LIKE ?"); params.add("%$parte%")
        }
        if (estado.isNotBlank()) {
            sql.append(" AND estado = ?"); params.add(estado)
        }
        sql.append(" ORDER BY id DESC")
        val rows = db.rows(sql.toString(), params)
        val partes = db.rows(
            "SELECT DISTINCT razon_social FROM ${tipo.table} WHERE empresa_id = ? ORDER BY razon_social",
            listOf(empresaId)
        ).map { it.str("razon_social") }

        val hoy = Fmt.today()
        val tot = StringBuilder(
            """SELECT
                COALESCE(SUM(CASE WHEN estado != 'Pagada' THEN saldo ELSE 0 END), 0) as pendiente,
                COALESCE(SUM(CASE WHEN estado = 'Pagada' THEN monto_total ELSE 0 END), 0) as hecho,
                COALESCE(SUM(CASE WHEN fecha_vencimiento < ? AND estado != 'Pagada' THEN saldo ELSE 0 END), 0) as vencido,
                COALESCE(SUM(CASE WHEN fecha_vencimiento >= ? AND estado != 'Pagada' THEN saldo ELSE 0 END), 0) as porvencer
               FROM ${tipo.table} WHERE empresa_id = ?"""
        )
        val tp = ArrayList<Any?>()
        tp.add(hoy); tp.add(hoy); tp.add(empresaId)
        if (fi.isNotBlank()) {
            tot.append(" AND fecha_emision >= ?"); tp.add(fi)
        }
        if (ff.isNotBlank()) {
            tot.append(" AND fecha_emision <= ?"); tp.add(ff)
        }
        val t = db.row(tot.toString(), tp)
        return CuentasResult(
            rows, partes,
            t?.dbl("pendiente") ?: 0.0, t?.dbl("hecho") ?: 0.0, t?.dbl("vencido") ?: 0.0, t?.dbl("porvencer") ?: 0.0
        )
    }

    fun agregar(tipo: CuentaTipo, empresaId: Long, c: CuentaInput): OpResult {
        if (c.razonSocial.isBlank()) return OpResult(false, "La razón social es obligatoria")
        if (c.concepto.isBlank()) return OpResult(false, "El concepto es obligatorio")
        if (c.montoTotal <= 0) return OpResult(false, "El monto total debe ser mayor a 0")
        if (c.fechaEmision.isBlank() || c.fechaVencimiento.isBlank()) return OpResult(false, "Las fechas son obligatorias")
        return try {
            DbManager.db().insertRow(
                tipo.table,
                mapOf(
                    "empresa_id" to empresaId, "fecha_emision" to c.fechaEmision, "fecha_vencimiento" to c.fechaVencimiento,
                    "tipo_documento" to c.tipoDocumento, "numero_documento" to c.numeroDocumento,
                    "razon_social" to c.razonSocial.trim(), "nit_ci" to c.nitCi.trim(), "concepto" to c.concepto.trim(),
                    "monto_total" to c.montoTotal, "monto_pagado" to 0.0, "saldo" to c.montoTotal,
                    "estado" to "Pendiente", "observaciones" to c.observaciones
                )
            )
            OpResult(true, if (tipo == CuentaTipo.COBRAR) "Cuenta por cobrar agregada correctamente" else "Cuenta por pagar agregada correctamente")
        } catch (e: Exception) {
            OpResult(false, "Error al agregar cuenta: ${e.message}")
        }
    }

    fun registrarPago(
        tipo: CuentaTipo, empresaId: Long, id: Long, monto: Double, fechaPago: String,
        tipoPago: String, cheque: String, banco: String, obs: String
    ): OpResult {
        if (monto <= 0) return OpResult(false, "El monto del pago debe ser mayor a 0")
        if (fechaPago.isBlank()) return OpResult(false, "La fecha de pago es obligatoria")
        val db = DbManager.db()
        return try {
            val cuenta = db.row("SELECT * FROM ${tipo.table} WHERE id = ? AND empresa_id = ?", listOf(id, empresaId))
                ?: return OpResult(false, "Cuenta no encontrada o no tienes permiso")
            val nuevoPagado = cuenta.dbl("monto_pagado") + monto
            val nuevoSaldo = cuenta.dbl("monto_total") - nuevoPagado
            val nuevoEstado = if (nuevoPagado >= cuenta.dbl("monto_total")) "Pagada" else "Parcial"
            val extra = ArrayList<String>()
            db.tx {
                db.updateRow(
                    tipo.table,
                    mapOf("monto_pagado" to nuevoPagado, "saldo" to nuevoSaldo, "estado" to nuevoEstado),
                    "id = ?", listOf(id)
                )
                db.insertRow(
                    tipo.pagosTable,
                    mapOf(
                        "empresa_id" to empresaId, tipo.fkPagos to id, "fecha_pago" to fechaPago, "monto" to monto,
                        "tipo_pago" to tipoPago, "numero_cheque" to cheque, "banco" to banco, "observaciones" to obs
                    )
                )
                if (tipo == CuentaTipo.COBRAR) {
                    val ventaId = cuenta.lng("venta_id")
                    if (ventaId > 0L) {
                        val ev = when (nuevoEstado) {
                            "Pagada" -> "Cobrada"
                            "Parcial" -> "Cobro Parcial"
                            else -> "Por Cobrar"
                        }
                        db.updateRow("ventas", mapOf("estado_cobro" to ev), "id = ?", listOf(ventaId))
                        extra.add("Venta #$ventaId")
                    }
                } else {
                    val ep = when (nuevoEstado) {
                        "Pagada" -> "Pagada"
                        "Parcial" -> "Pagada Parcial"
                        else -> "Por Pagar"
                    }
                    val compraId = cuenta.lng("compra_id")
                    val gastoId = cuenta.lng("gasto_id")
                    val regimenId = cuenta.lng("regimen_id")
                    if (compraId > 0L) {
                        db.updateRow("compras", mapOf("estado_pago" to ep), "id = ?", listOf(compraId)); extra.add("Compra #$compraId")
                    }
                    if (gastoId > 0L) {
                        db.updateRow("gastos", mapOf("estado_pago" to ep), "id = ?", listOf(gastoId)); extra.add("Gasto #$gastoId")
                    }
                    if (regimenId > 0L) {
                        db.updateRow("regimen", mapOf("estado_pago" to ep), "id = ?", listOf(regimenId)); extra.add("Régimen #$regimenId")
                    }
                }
            }
            val verbo = if (tipo == CuentaTipo.COBRAR) "Cobro" else "Pago"
            if (extra.isNotEmpty()) OpResult(true, "$verbo registrado y estado actualizado en: ${extra.joinToString(", ")}")
            else OpResult(true, "$verbo registrado correctamente")
        } catch (e: Exception) {
            OpResult(false, "Error al registrar pago: ${e.message}")
        }
    }

    fun eliminar(tipo: CuentaTipo, empresaId: Long, id: Long): OpResult {
        val db = DbManager.db()
        return try {
            val c = db.row("SELECT empresa_id FROM ${tipo.table} WHERE id = ?", listOf(id))
                ?: return OpResult(false, "Cuenta no encontrada")
            if (c.lng("empresa_id") != empresaId) return OpResult(false, "No tienes permiso para eliminar esta cuenta")
            db.tx {
                db.exec("DELETE FROM ${tipo.pagosTable} WHERE ${tipo.fkPagos} = ?", listOf(id))
                db.exec("DELETE FROM ${tipo.table} WHERE id = ?", listOf(id))
            }
            OpResult(true, if (tipo == CuentaTipo.COBRAR) "Cuenta por cobrar eliminada correctamente" else "Cuenta por pagar eliminada correctamente")
        } catch (e: Exception) {
            OpResult(false, "Error al eliminar: ${e.message}")
        }
    }

    fun historial(tipo: CuentaTipo, empresaId: Long, id: Long): List<Rec> {
        val db = DbManager.db()
        val c = db.row("SELECT id FROM ${tipo.table} WHERE id = ? AND empresa_id = ?", listOf(id, empresaId)) ?: return emptyList()
        return db.rows(
            "SELECT * FROM ${tipo.pagosTable} WHERE ${tipo.fkPagos} = ? ORDER BY fecha_pago DESC, id DESC",
            listOf(c.lng("id"))
        )
    }

    fun plantilla(tipo: CuentaTipo): TableData {
        val cobrar = tipo == CuentaTipo.COBRAR
        return TableData(
            tipo.title,
            listOf("fecha_emision", "fecha_vencimiento", "tipo_documento", "nit_ci", "razon_social", "concepto", "monto_total", "observaciones"),
            listOf(
                listOf("2024-01-15", "2024-02-15", "Factura", "123456789", if (cobrar) "CLIENTE ABC S.R.L." else "PROVEEDOR ABC S.R.L.", if (cobrar) "Venta de productos" else "Compra de insumos", 1500.0, ""),
                listOf("2024-01-20", "2024-02-20", "Recibo", "987654321", if (cobrar) "EMPRESA XYZ" else "COMERCIAL XYZ", if (cobrar) "Servicios prestados" else "Servicios recibidos", 2500.0, "Pago en cuotas")
            ),
            if (cobrar) "28A745" else "DC3545"
        )
    }

    fun exportarDatos(tipo: CuentaTipo, empresaId: Long, fi: String, ff: String): TableData {
        val cols = listOf("id", "fecha_emision", "fecha_vencimiento", "tipo_documento", "numero_documento", "nit_ci", "razon_social", "concepto", "monto_total", "monto_pagado", "saldo", "estado", "observaciones")
        val sql = StringBuilder("SELECT ${cols.joinToString(", ")} FROM ${tipo.table} WHERE empresa_id = ?")
        val p = ArrayList<Any?>()
        p.add(empresaId)
        if (fi.isNotBlank()) {
            sql.append(" AND fecha_emision >= ?"); p.add(fi)
        }
        if (ff.isNotBlank()) {
            sql.append(" AND fecha_emision <= ?"); p.add(ff)
        }
        sql.append(" ORDER BY id DESC")
        val data = DbManager.db().rows(sql.toString(), p).map { r -> cols.map { c -> r[c] } }
        return TableData(tipo.title, cols, data, if (tipo == CuentaTipo.COBRAR) "28A745" else "DC3545")
    }

    fun importar(tipo: CuentaTipo, empresaId: Long, raw: List<Map<String, String>>): ImportResult {
        val map = mapOf(
            "fecha_emision" to "fecha_emision", "fecha_emisión" to "fecha_emision", "fecha_vencimiento" to "fecha_vencimiento",
            "tipo_doc" to "tipo_documento", "tipo_documento" to "tipo_documento", "nro_documento" to "numero_documento",
            "numero_documento" to "numero_documento", "nit_ci" to "nit_ci", "nit" to "nit_ci", "ci" to "nit_ci",
            "razón_social" to "razon_social", "razon_social" to "razon_social", "concepto" to "concepto",
            "monto_total" to "monto_total", "monto" to "monto_total", "observaciones" to "observaciones"
        )
        val filas = raw.map { r ->
            val m = LinkedHashMap<String, String>()
            for ((k, v) in r) {
                val key = k.trim().lowercase().replace(" ", "_").replace(".", "")
                m[map[key] ?: key] = v
            }
            m
        }
        if (filas.isEmpty()) return ImportResult(0, 0, emptyList(), "El archivo no contiene filas de datos")
        val req = listOf("fecha_emision", "fecha_vencimiento", "tipo_documento", "nit_ci", "razon_social", "concepto", "monto_total")
        val faltan = req.filter { !filas[0].containsKey(it) }
        if (faltan.isNotEmpty()) {
            return ImportResult(0, 0, emptyList(), "Faltan columnas requeridas: ${faltan.joinToString(", ")}. Columnas encontradas: ${filas[0].keys.joinToString(", ")}")
        }
        val db = DbManager.db()
        var ok = 0
        val errores = ArrayList<String>()
        db.beginTransaction()
        try {
            for ((idx, r) in filas.withIndex()) {
                try {
                    val monto = Fmt.parseDouble(r["monto_total"]) ?: throw IllegalArgumentException("monto inválido")
                    db.insertRow(
                        tipo.table,
                        mapOf(
                            "empresa_id" to empresaId, "fecha_emision" to Fmt.normalizeDate(r["fecha_emision"]),
                            "fecha_vencimiento" to Fmt.normalizeDate(r["fecha_vencimiento"]),
                            "tipo_documento" to r["tipo_documento"].orEmpty(), "numero_documento" to r["numero_documento"].orEmpty(),
                            "razon_social" to r["razon_social"].orEmpty(), "nit_ci" to r["nit_ci"].orEmpty().removeSuffix(".0"),
                            "concepto" to r["concepto"].orEmpty(), "monto_total" to monto, "monto_pagado" to 0.0,
                            "saldo" to monto, "estado" to "Pendiente", "observaciones" to r["observaciones"].orEmpty()
                        )
                    )
                    ok++
                } catch (e: Exception) {
                    errores.add("Fila ${idx + 2}: ${e.message}")
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return ImportResult(ok, 0, errores, null)
    }
}
