package com.appscz.cbfinance.data

import com.appscz.cbfinance.util.Fmt

enum class Kind(val table: String, val catTable: String, val singular: String, val title: String) {
    VENTAS("ventas", "categorias_ventas", "Venta", "Ingresos"),
    COMPRAS("compras", "categorias_compras", "Compra", "Egresos"),
    GASTOS("gastos", "categorias_gastos", "Gasto", "Gastos"),
    REGIMEN("regimen", "categorias_regimen", "Régimen", "Régimen");

    val newLabel: String get() = when (this) {
        VENTAS -> "Nueva Venta"
        COMPRAS -> "Nueva Compra"
        GASTOS -> "Nuevo Gasto"
        REGIMEN -> "Nuevo Régimen"
    }
    val hasPlanilla: Boolean get() = this == GASTOS || this == REGIMEN
    val planillaTable: String get() = if (this == GASTOS) "planillas" else "planillas_regimen"
    val planillaFk: String get() = if (this == GASTOS) "gasto_id" else "regimen_id"
    val estadoCol: String get() = if (this == VENTAS) "estado_cobro" else "estado_pago"
    val usesProducto: Boolean get() = this == VENTAS || this == COMPRAS
    val detailCol: String get() = if (usesProducto) "producto" else "descripcion"
    val hasExcento: Boolean get() = this != VENTAS
}

class MovInput(
    val fecha: String = "",
    val tipoDocumento: String = "Factura",
    val razonSocial: String = "",
    val numeroDocumento: String = "",
    val numeroFactura: String = "",
    val detalle: String = "",
    val categoria: String = "",
    val cantidad: Double = 1.0,
    val precioUnitario: Double = 0.0,
    val descuento: Double = 0.0,
    val excento: Double = 0.0,
    val tipoPago: String = "Efectivo",
    val numeroCheque: String = "",
    val banco: String = "",
    val credito: Boolean = false,
    val fechaVencimiento: String = "",
    val observaciones: String = "",
    // Planilla
    val empleado: String = "",
    val mes: String = "",
    val montoBruto: Double = 0.0,
    val afpPorcentaje: Double = 19.92,
    val cajaNacional: Double = 0.0,
    val ministerioTrabajo: Double = 0.0,
    val horasExtras: Double = 0.0
) {
    val esPlanilla: Boolean get() = tipoDocumento == "Planilla"
    fun totalNeto(): Double = montoBruto - montoBruto * (afpPorcentaje / 100.0) - cajaNacional - ministerioTrabajo + horasExtras
}

class PageResult(val rows: List<Rec>, val total: Int, val totalPages: Int, val sumTotal: Double)

class ImportResult(val inserted: Int, val extra: Int, val errors: List<String>, val fatal: String?)

class TableData(val sheet: String, val headers: List<String>, val rows: List<List<Any?>>, val headerColor: String)

object MovRepo {

    fun listar(kind: Kind, empresaId: Long, fi: String, ff: String, razon: String, page: Int, perPage: Int): PageResult {
        val db = DbManager.db()
        val where = StringBuilder(" WHERE empresa_id = ?")
        val params = ArrayList<Any?>()
        params.add(empresaId)
        if (fi.isNotBlank()) {
            where.append(" AND fecha >= ?"); params.add(fi)
        }
        if (ff.isNotBlank()) {
            where.append(" AND fecha <= ?"); params.add(ff)
        }
        if (razon.isNotBlank()) {
            where.append(" AND razon_social LIKE ?"); params.add("%$razon%")
        }
        val total = db.scalarLong("SELECT COUNT(*) FROM ${kind.table}$where", params).toInt()
        val sum = db.scalarDouble("SELECT COALESCE(SUM(total),0) FROM ${kind.table}$where", params)
        val pages = if (perPage > 0) (total + perPage - 1) / perPage else 1
        val offset = (page - 1) * perPage
        val rows = db.rows("SELECT * FROM ${kind.table}$where ORDER BY id DESC LIMIT $perPage OFFSET $offset", params)
        return PageResult(rows, total, pages, sum)
    }

    fun razonesSociales(kind: Kind, empresaId: Long): List<String> =
        DbManager.db().rows(
            "SELECT DISTINCT razon_social FROM ${kind.table} WHERE empresa_id = ? AND razon_social IS NOT NULL AND TRIM(razon_social) != '' ORDER BY razon_social",
            listOf(empresaId)
        ).map { it.str("razon_social") }

    // ------------------------------------------------------------------ AGREGAR
    fun agregar(kind: Kind, empresaId: Long, i: MovInput, confirmarDuplicado: Boolean): OpResult {
        if (i.fecha.isBlank()) return OpResult(false, "La fecha es obligatoria")
        if (i.tipoDocumento.isBlank()) return OpResult(false, "El tipo de documento es obligatorio")
        if (i.categoria.isBlank()) return OpResult(false, "La categoría es obligatoria")
        return try {
            when (kind) {
                Kind.VENTAS -> agregarVenta(empresaId, i, confirmarDuplicado)
                Kind.COMPRAS -> agregarCompra(empresaId, i, confirmarDuplicado)
                Kind.GASTOS, Kind.REGIMEN -> agregarGastoRegimen(kind, empresaId, i, confirmarDuplicado)
            }
        } catch (e: Exception) {
            OpResult(false, "Error al registrar: ${e.message}")
        }
    }

    private fun agregarVenta(empresaId: Long, i: MovInput, confirmar: Boolean): OpResult {
        if (i.razonSocial.isBlank()) return OpResult(false, "La razón social es obligatoria")
        if (i.detalle.isBlank()) return OpResult(false, "El detalle / producto es obligatorio")
        val db = DbManager.db()
        val total = i.cantidad * i.precioUnitario - i.descuento
        val nf = i.numeroFactura.trim()
        if (nf.isNotEmpty() && !confirmar) {
            val d = db.row(
                "SELECT id, fecha, razon_social, producto, total FROM ventas WHERE empresa_id = ? AND numero_factura = ? AND numero_factura != ''",
                listOf(empresaId, nf)
            )
            if (d != null) {
                val msg = "Ya existe una venta con el número de factura \"$nf\" para \"${d.str("razon_social")}\", registrada el ${d.str("fecha")} (${d.str("producto")}, Total: ${Fmt.money(d.dbl("total"))})."
                return OpResult(false, msg, warning = true, duplicate = true)
            }
        }
        var message = ""
        db.tx {
            val ventaId = db.insertRow(
                "ventas",
                mapOf(
                    "empresa_id" to empresaId, "fecha" to i.fecha, "tipo_documento" to i.tipoDocumento,
                    "razon_social" to i.razonSocial.trim(), "numero_documento" to i.numeroDocumento.trim(),
                    "numero_factura" to nf, "producto" to i.detalle.trim(), "categoria" to i.categoria,
                    "cantidad" to i.cantidad, "precio_unitario" to i.precioUnitario, "descuento" to i.descuento,
                    "total" to total, "tipo_pago" to i.tipoPago, "numero_cheque" to i.numeroCheque,
                    "banco" to i.banco, "estado_cobro" to (if (i.credito) "Por Cobrar" else "Cobrada")
                )
            )
            if (i.credito) {
                val venc = if (i.fechaVencimiento.isBlank()) i.fecha else i.fechaVencimiento
                val cuentaId = db.insertRow(
                    "cuentas_cobrar",
                    mapOf(
                        "empresa_id" to empresaId, "fecha_emision" to i.fecha, "fecha_vencimiento" to venc,
                        "tipo_documento" to i.tipoDocumento,
                        "numero_documento" to (if (nf.isNotEmpty()) nf else i.numeroDocumento.trim()),
                        "razon_social" to i.razonSocial.trim(), "nit_ci" to i.numeroDocumento.trim(),
                        "concepto" to ("Venta: " + i.detalle.trim() + (if (i.categoria.isNotEmpty()) " - " + i.categoria else "")),
                        "monto_total" to total, "monto_pagado" to 0.0, "saldo" to total, "estado" to "Pendiente",
                        "observaciones" to (if (i.observaciones.isNotBlank()) i.observaciones else "Generado automáticamente desde Venta #$ventaId"),
                        "venta_id" to ventaId
                    )
                )
                db.updateRow("ventas", mapOf("cuenta_cobrar_id" to cuentaId), "id = ?", listOf(ventaId))
                message = "Venta a crédito registrada (#$ventaId) y creada en Cuentas por Cobrar (#$cuentaId)"
            } else {
                message = "Venta al contado registrada correctamente (#$ventaId)"
            }
        }
        return OpResult(true, message)
    }

    private fun agregarCompra(empresaId: Long, i: MovInput, confirmar: Boolean): OpResult {
        if (i.razonSocial.isBlank()) return OpResult(false, "La razón social es obligatoria")
        if (i.detalle.isBlank()) return OpResult(false, "El detalle / producto es obligatorio")
        val db = DbManager.db()
        val total = i.cantidad * i.precioUnitario - i.descuento - i.excento
        val nf = i.numeroFactura.trim()
        if (nf.isNotEmpty() && !confirmar) {
            val d = db.row(
                "SELECT id, fecha, razon_social, producto, total FROM compras WHERE empresa_id = ? AND numero_factura = ? AND razon_social = ? AND numero_factura != ''",
                listOf(empresaId, nf, i.razonSocial)
            )
            if (d != null) {
                val msg = "Ya existe una compra con el número de documento \"$nf\" del proveedor \"${i.razonSocial}\", registrada el ${d.str("fecha")} (${d.str("producto")}, Total: ${Fmt.money(d.dbl("total"))})."
                return OpResult(false, msg, warning = true, duplicate = true)
            }
        }
        var message = ""
        db.tx {
            val compraId = db.insertRow(
                "compras",
                mapOf(
                    "empresa_id" to empresaId, "fecha" to i.fecha, "tipo_documento" to i.tipoDocumento,
                    "razon_social" to i.razonSocial.trim(), "numero_documento" to i.numeroDocumento.trim(),
                    "numero_factura" to nf, "producto" to i.detalle.trim(), "categoria" to i.categoria,
                    "cantidad" to i.cantidad, "precio_unitario" to i.precioUnitario, "descuento" to i.descuento,
                    "excento" to i.excento, "total" to total, "tipo_pago" to i.tipoPago,
                    "numero_cheque" to i.numeroCheque, "banco" to i.banco,
                    "estado_pago" to (if (i.credito) "Por Pagar" else "Pagada")
                )
            )
            if (i.credito) {
                val cuentaId = crearCuentaPagar(
                    db, empresaId, i, i.numeroDocumento.trim(),
                    "Compra: " + i.detalle.trim() + (if (i.categoria.isNotEmpty()) " - " + i.categoria else ""),
                    total, "compra_id", compraId, "Compra"
                )
                db.updateRow("compras", mapOf("cuenta_pagar_id" to cuentaId), "id = ?", listOf(compraId))
                message = "Compra a crédito registrada (#$compraId) y creada en Cuentas por Pagar (#$cuentaId)"
            } else {
                message = "Compra al contado registrada correctamente (#$compraId)"
            }
        }
        return OpResult(true, message)
    }

    private fun crearCuentaPagar(
        db: android.database.sqlite.SQLiteDatabase, empresaId: Long, i: MovInput, nitCi: String, concepto: String,
        total: Double, linkCol: String, linkId: Long, origen: String
    ): Long {
        val venc = if (i.fechaVencimiento.isBlank()) i.fecha else i.fechaVencimiento
        val nf = i.numeroFactura.trim()
        return db.insertRow(
            "cuentas_pagar",
            mapOf(
                "empresa_id" to empresaId, "fecha_emision" to i.fecha, "fecha_vencimiento" to venc,
                "tipo_documento" to i.tipoDocumento,
                "numero_documento" to (if (nf.isNotEmpty()) nf else nitCi),
                "razon_social" to i.razonSocial.trim(), "nit_ci" to nitCi, "concepto" to concepto,
                "monto_total" to total, "monto_pagado" to 0.0, "saldo" to total, "estado" to "Pendiente",
                "observaciones" to (if (i.observaciones.isNotBlank()) i.observaciones else "Generado automáticamente desde $origen #$linkId"),
                linkCol to linkId
            )
        )
    }

    private fun agregarGastoRegimen(kind: Kind, empresaId: Long, i: MovInput, confirmar: Boolean): OpResult {
        val db = DbManager.db()
        val t = kind.table
        val nombreKind = if (kind == Kind.GASTOS) "Gasto" else "Régimen"
        val linkCol = if (kind == Kind.GASTOS) "gasto_id" else "regimen_id"

        if (i.esPlanilla) {
            val empleado = i.empleado.trim()
            val mes = i.mes.trim()
            if (empleado.isEmpty()) return OpResult(false, "El nombre del empleado es obligatorio para planillas")
            if (mes.isEmpty()) return OpResult(false, "El mes es obligatorio para planillas")
            if (i.montoBruto <= 0) return OpResult(false, "El monto bruto debe ser mayor a 0")
            if (!confirmar) {
                val d = db.row(
                    """SELECT p.id, p.fecha, p.empleado, p.mes, p.total_neto
                       FROM ${kind.planillaTable} p JOIN $t g ON p.${kind.planillaFk} = g.id
                       WHERE p.empresa_id = ? AND LOWER(TRIM(p.empleado)) = LOWER(TRIM(?)) AND LOWER(TRIM(p.mes)) = LOWER(TRIM(?))""",
                    listOf(empresaId, empleado, mes)
                )
                if (d != null) {
                    val msg = "Ya existe una planilla para \"$empleado\" del mes \"$mes\", registrada el ${d.str("fecha")} (Total Neto: Bs. ${Fmt.money(d.dbl("total_neto"))})."
                    return OpResult(false, msg, warning = true, duplicate = true)
                }
            }
            val afpMonto = i.montoBruto * (i.afpPorcentaje / 100.0)
            val neto = i.totalNeto()
            val defDesc = if (kind == Kind.GASTOS) "Planilla $mes" else "Planilla Régimen $mes"
            db.tx {
                val id = db.insertRow(
                    t,
                    mapOf(
                        "empresa_id" to empresaId, "fecha" to i.fecha, "tipo_documento" to "Planilla",
                        "numero_documento" to "-", "numero_factura" to "", "razon_social" to empleado,
                        "descripcion" to (if (i.detalle.isNotBlank()) i.detalle else defDesc),
                        "categoria" to i.categoria, "cantidad" to 1.0, "precio_unitario" to neto,
                        "descuento" to 0.0, "excento" to 0.0, "total" to neto, "tipo_pago" to "Transferencia",
                        "numero_cheque" to "", "banco" to ""
                    )
                )
                db.insertRow(
                    kind.planillaTable,
                    mapOf(
                        "empresa_id" to empresaId, kind.planillaFk to id, "fecha" to i.fecha, "empleado" to empleado,
                        "mes" to mes, "monto_bruto" to i.montoBruto, "descuento_afp_porcentaje" to i.afpPorcentaje,
                        "descuento_afp_monto" to afpMonto, "descuento_caja_nacional" to i.cajaNacional,
                        "descuento_ministerio_trabajo" to i.ministerioTrabajo, "horas_extras" to i.horasExtras,
                        "total_neto" to neto, "observaciones" to (if (i.detalle.isNotBlank()) i.detalle else null)
                    )
                )
            }
            val prefijo = if (kind == Kind.GASTOS) "Planilla" else "Planilla de Régimen"
            return OpResult(true, "$prefijo de $empleado registrada - Total Neto: Bs. ${Fmt.money(neto)}")
        }

        val nit = i.numeroDocumento.trim()
        val razon = i.razonSocial.trim()
        val total = i.cantidad * i.precioUnitario - i.descuento - i.excento
        val etiqueta = if (kind == Kind.GASTOS) "gastos normales" else "régimen normal"
        if (nit.isEmpty()) return OpResult(false, "El NIT/CI es obligatorio para $etiqueta")
        if (razon.isEmpty()) return OpResult(false, "La razón social es obligatoria para $etiqueta")
        if (total <= 0) return OpResult(false, "El total debe ser mayor a 0")
        val nf = i.numeroFactura.trim()
        if (nf.isNotEmpty() && !confirmar) {
            val d = db.row(
                "SELECT id, fecha, razon_social, categoria, total FROM $t WHERE empresa_id = ? AND numero_factura = ? AND razon_social = ? AND numero_factura != '' AND tipo_documento != 'Planilla'",
                listOf(empresaId, nf, razon)
            )
            if (d != null) {
                val que = if (kind == Kind.GASTOS) "un gasto" else "un registro de régimen"
                val msg = "Ya existe $que con el número de factura \"$nf\" del proveedor \"$razon\", registrado el ${d.str("fecha")} (${d.str("categoria")}, Total: Bs. ${Fmt.money(d.dbl("total"))})."
                return OpResult(false, msg, warning = true, duplicate = true)
            }
        }
        var message = ""
        db.tx {
            val id = db.insertRow(
                t,
                mapOf(
                    "empresa_id" to empresaId, "fecha" to i.fecha, "tipo_documento" to i.tipoDocumento,
                    "numero_documento" to nit, "numero_factura" to nf, "razon_social" to razon,
                    "descripcion" to (if (i.detalle.isNotBlank()) i.detalle else "-"), "categoria" to i.categoria,
                    "cantidad" to i.cantidad, "precio_unitario" to i.precioUnitario, "descuento" to i.descuento,
                    "excento" to i.excento, "total" to total, "tipo_pago" to i.tipoPago,
                    "numero_cheque" to i.numeroCheque, "banco" to i.banco,
                    "estado_pago" to (if (i.credito) "Por Pagar" else "Pagada")
                )
            )
            if (i.credito) {
                val concepto = "$nombreKind: ${i.categoria}" + (if (i.detalle.isNotBlank() && i.detalle != "-") " - " + i.detalle else "")
                val cuentaId = crearCuentaPagar(db, empresaId, i, nit, concepto, total, linkCol, id, nombreKind)
                db.updateRow(t, mapOf("cuenta_pagar_id" to cuentaId), "id = ?", listOf(id))
                message = "$nombreKind a crédito registrado (#$id) y creado en Cuentas por Pagar (#$cuentaId)"
            } else {
                message = "$nombreKind al contado registrado correctamente (#$id)"
            }
        }
        return OpResult(true, message)
    }

    // ------------------------------------------------------------------ EDITAR (gastos / régimen)
    fun editar(kind: Kind, empresaId: Long, id: Long, i: MovInput): OpResult {
        if (!kind.hasPlanilla) return OpResult(false, "Este módulo no permite edición")
        if (i.fecha.isBlank() || i.tipoDocumento.isBlank() || i.categoria.isBlank()) {
            return OpResult(false, "Todos los campos obligatorios deben estar completos")
        }
        val db = DbManager.db()
        return try {
            if (i.esPlanilla) {
                val empleado = i.empleado.trim()
                val mes = i.mes.trim()
                if (empleado.isEmpty() || mes.isEmpty() || i.montoBruto <= 0) return OpResult(false, "Datos de planilla incompletos o inválidos")
                val afpMonto = i.montoBruto * (i.afpPorcentaje / 100.0)
                val neto = i.totalNeto()
                val defDesc = if (kind == Kind.GASTOS) "Planilla $mes" else "Planilla Régimen $mes"
                db.tx {
                    db.updateRow(
                        kind.table,
                        mapOf(
                            "fecha" to i.fecha, "tipo_documento" to "Planilla", "numero_documento" to "-",
                            "razon_social" to empleado, "descripcion" to (if (i.detalle.isNotBlank()) i.detalle else defDesc),
                            "categoria" to i.categoria, "cantidad" to 1.0, "precio_unitario" to neto, "descuento" to 0.0,
                            "excento" to 0.0, "total" to neto, "tipo_pago" to "Transferencia", "numero_cheque" to "", "banco" to ""
                        ),
                        "id = ? AND empresa_id = ?", listOf(id, empresaId)
                    )
                    val existe = db.scalarLong(
                        "SELECT COUNT(*) FROM ${kind.planillaTable} WHERE ${kind.planillaFk} = ? AND empresa_id = ?",
                        listOf(id, empresaId)
                    )
                    val vals = mapOf(
                        "fecha" to i.fecha, "empleado" to empleado, "mes" to mes, "monto_bruto" to i.montoBruto,
                        "descuento_afp_porcentaje" to i.afpPorcentaje, "descuento_afp_monto" to afpMonto,
                        "descuento_caja_nacional" to i.cajaNacional, "descuento_ministerio_trabajo" to i.ministerioTrabajo,
                        "horas_extras" to i.horasExtras, "total_neto" to neto, "observaciones" to i.detalle
                    )
                    if (existe > 0L) {
                        db.updateRow(kind.planillaTable, vals, "${kind.planillaFk} = ? AND empresa_id = ?", listOf(id, empresaId))
                    } else {
                        val ins = HashMap<String, Any?>(vals)
                        ins["empresa_id"] = empresaId
                        ins[kind.planillaFk] = id
                        db.insertRow(kind.planillaTable, ins)
                    }
                }
                OpResult(true, "Planilla actualizada - Total Neto: Bs. ${Fmt.money(neto)}")
            } else {
                val nit = i.numeroDocumento.trim()
                val razon = i.razonSocial.trim()
                val total = i.cantidad * i.precioUnitario - i.descuento - i.excento
                if (nit.isEmpty() || razon.isEmpty() || total <= 0) return OpResult(false, "Datos incompletos o inválidos")
                db.updateRow(
                    kind.table,
                    mapOf(
                        "fecha" to i.fecha, "tipo_documento" to i.tipoDocumento, "numero_documento" to nit,
                        "razon_social" to razon, "descripcion" to (if (i.detalle.isNotBlank()) i.detalle else "-"),
                        "categoria" to i.categoria, "cantidad" to i.cantidad, "precio_unitario" to i.precioUnitario,
                        "descuento" to i.descuento, "excento" to i.excento, "total" to total, "tipo_pago" to i.tipoPago,
                        "numero_cheque" to i.numeroCheque, "banco" to i.banco
                    ),
                    "id = ? AND empresa_id = ?", listOf(id, empresaId)
                )
                OpResult(true, "${kind.singular} actualizado exitosamente")
            }
        } catch (e: Exception) {
            OpResult(false, "Error al actualizar: ${e.message}")
        }
    }

    fun detallePlanilla(kind: Kind, empresaId: Long, id: Long): Rec? {
        if (!kind.hasPlanilla) return null
        val db = DbManager.db()
        val g = db.row("SELECT * FROM ${kind.table} WHERE id = ? AND empresa_id = ?", listOf(id, empresaId)) ?: return null
        val p = db.row("SELECT * FROM ${kind.planillaTable} WHERE ${kind.planillaFk} = ? AND empresa_id = ?", listOf(id, empresaId)) ?: return null
        val m = LinkedHashMap<String, Any?>()
        m.putAll(p)
        m["gasto_fecha"] = g.str("fecha")
        m["categoria"] = g.str("categoria")
        m["descripcion"] = g.str("descripcion")
        return m
    }

    // ------------------------------------------------------------------ ELIMINAR
    fun eliminar(kind: Kind, empresaId: Long, id: Long): OpResult {
        val db = DbManager.db()
        return try {
            val r = db.row("SELECT empresa_id, ${kind.estadoCol} as estado FROM ${kind.table} WHERE id = ?", listOf(id))
                ?: return OpResult(false, "${kind.singular} no encontrado")
            if (r.lng("empresa_id") != empresaId) return OpResult(false, "No tienes permiso para eliminar este registro")
            var warn = ""
            if (kind == Kind.VENTAS && r.str("estado") == "Por Cobrar") {
                warn = "⚠️ Esta venta está en Cuentas por Cobrar. Recuerda eliminarla también de ahí si es necesario. "
            } else if (kind != Kind.VENTAS && r.str("estado") == "Por Pagar") {
                warn = "⚠️ Este registro está en Cuentas por Pagar. Recuerda eliminarlo también de ahí si es necesario. "
            }
            db.tx {
                if (kind.hasPlanilla) {
                    db.exec("DELETE FROM ${kind.planillaTable} WHERE ${kind.planillaFk} = ? AND empresa_id = ?", listOf(id, empresaId))
                }
                db.exec("DELETE FROM ${kind.table} WHERE id = ? AND empresa_id = ?", listOf(id, empresaId))
            }
            OpResult(true, warn + "${kind.singular} eliminado correctamente", warning = warn.isNotEmpty())
        } catch (e: Exception) {
            OpResult(false, "Error al eliminar: ${e.message}")
        }
    }

    fun eliminarMasivo(kind: Kind, empresaId: Long, idIni: Long, idFin: Long): OpResult {
        if (idIni > idFin) return OpResult(false, "El ID de inicio debe ser menor o igual al ID de fin")
        val db = DbManager.db()
        return try {
            val total = db.scalarLong(
                "SELECT COUNT(*) FROM ${kind.table} WHERE empresa_id = ? AND id BETWEEN ? AND ?",
                listOf(empresaId, idIni, idFin)
            )
            if (total == 0L) return OpResult(false, "No se encontraron registros en ese rango de IDs para esta empresa", warning = true)
            db.tx {
                if (kind.hasPlanilla) {
                    db.exec(
                        "DELETE FROM ${kind.planillaTable} WHERE ${kind.planillaFk} IN (SELECT id FROM ${kind.table} WHERE empresa_id = ? AND id BETWEEN ? AND ?) AND empresa_id = ?",
                        listOf(empresaId, idIni, idFin, empresaId)
                    )
                }
                db.exec("DELETE FROM ${kind.table} WHERE empresa_id = ? AND id BETWEEN ? AND ?", listOf(empresaId, idIni, idFin))
            }
            OpResult(true, "Se eliminaron $total registros correctamente (IDs $idIni - $idFin)")
        } catch (e: Exception) {
            OpResult(false, "Error al eliminar: ${e.message}")
        }
    }

    // ------------------------------------------------------------------ CATEGORÍAS
    fun categoriasActivas(kind: Kind, empresaId: Long): List<Rec> = DbManager.db().rows(
        "SELECT id, nombre, descripcion FROM ${kind.catTable} WHERE empresa_id = ? AND activo = 1 ORDER BY nombre",
        listOf(empresaId)
    )

    fun categoriasTodas(kind: Kind, empresaId: Long): List<Rec> = DbManager.db().rows(
        "SELECT id, nombre, descripcion, activo FROM ${kind.catTable} WHERE empresa_id = ? ORDER BY nombre",
        listOf(empresaId)
    )

    fun agregarCategoria(kind: Kind, empresaId: Long, nombre: String, descripcion: String): OpResult {
        val n = nombre.trim()
        if (n.isEmpty()) return OpResult(false, "El nombre de la categoría es obligatorio")
        val db = DbManager.db()
        return try {
            val ex = db.row(
                "SELECT id FROM ${kind.catTable} WHERE empresa_id = ? AND LOWER(TRIM(nombre)) = LOWER(?)",
                listOf(empresaId, n)
            )
            if (ex != null) return OpResult(false, "Ya existe una categoría con ese nombre", warning = true)
            db.insertRow(
                kind.catTable,
                mapOf("empresa_id" to empresaId, "nombre" to n, "descripcion" to (if (descripcion.isBlank()) null else descripcion.trim()), "activo" to 1)
            )
            OpResult(true, "Categoría \"$n\" creada exitosamente")
        } catch (e: Exception) {
            OpResult(false, "Error al crear categoría: ${e.message}")
        }
    }

    fun eliminarCategoria(kind: Kind, empresaId: Long, id: Long): OpResult {
        val db = DbManager.db()
        return try {
            val asociados = db.scalarLong(
                "SELECT COUNT(*) FROM ${kind.table} g JOIN ${kind.catTable} c ON g.categoria = c.nombre WHERE c.id = ? AND c.empresa_id = ?",
                listOf(id, empresaId)
            )
            if (asociados > 0L) {
                db.updateRow(kind.catTable, mapOf("activo" to 0), "id = ? AND empresa_id = ?", listOf(id, empresaId))
                OpResult(true, "Categoría desactivada (tiene registros asociados)", warning = true)
            } else {
                db.exec("DELETE FROM ${kind.catTable} WHERE id = ? AND empresa_id = ?", listOf(id, empresaId))
                OpResult(true, "Categoría eliminada exitosamente")
            }
        } catch (e: Exception) {
            OpResult(false, "Error al eliminar categoría: ${e.message}")
        }
    }

    // ------------------------------------------------------------------ EXPORTAR
    fun exportarDatos(kind: Kind, empresaId: Long, fi: String, ff: String): TableData {
        val db = DbManager.db()
        val cols: List<String>
        val headers: List<String>
        val sheet: String
        val color: String
        when (kind) {
            Kind.VENTAS -> {
                cols = listOf("fecha", "tipo_documento", "razon_social", "numero_documento", "numero_factura", "producto", "categoria", "cantidad", "precio_unitario", "descuento", "total", "tipo_pago", "numero_cheque", "banco", "estado_cobro")
                headers = cols
                sheet = "Ventas"; color = "4472C4"
            }
            Kind.COMPRAS -> {
                cols = listOf("fecha", "tipo_documento", "numero_documento", "numero_factura", "razon_social", "producto", "categoria", "cantidad", "precio_unitario", "descuento", "excento", "total", "tipo_pago", "numero_cheque", "banco", "estado_pago")
                headers = cols
                sheet = "Compras"; color = "FF9800"
            }
            else -> {
                cols = listOf("id", "fecha", "tipo_documento", "numero_factura", "numero_documento", "razon_social", "descripcion", "categoria", "cantidad", "precio_unitario", "descuento", "excento", "total", "estado_pago", "tipo_pago", "numero_cheque", "banco")
                headers = listOf("ID", "Fecha", "Tipo Documento", "Nº Factura", "NIT/CI", "Razón Social", "Descripción", "Categoría", "Cantidad", "Precio Unitario", "Descuento", "Excento", "Total", "Estado Pago", "Tipo Pago", "Número Cheque", "Banco")
                sheet = if (kind == Kind.GASTOS) "Gastos" else "Régimen"
                color = if (kind == Kind.GASTOS) "DC3545" else "00B294"
            }
        }
        val sql = StringBuilder("SELECT ${cols.joinToString(", ")} FROM ${kind.table} WHERE empresa_id = ?")
        val params = ArrayList<Any?>()
        params.add(empresaId)
        if (fi.isNotBlank()) {
            sql.append(" AND fecha >= ?"); params.add(fi)
        }
        if (ff.isNotBlank()) {
            sql.append(" AND fecha <= ?"); params.add(ff)
        }
        sql.append(" ORDER BY id DESC")
        val data = db.rows(sql.toString(), params).map { r -> cols.map { c -> r[c] } }
        return TableData(sheet, headers, data, color)
    }

    fun plantilla(kind: Kind): TableData {
        return when (kind) {
            Kind.VENTAS -> TableData(
                "Ventas",
                listOf("fecha", "tipo_documento", "razon_social", "numero_documento", "numero_factura", "producto", "categoria", "cantidad", "precio_unitario", "descuento", "tipo_pago", "numero_cheque", "banco"),
                listOf(
                    listOf("2024-01-15", "Factura", "CLIENTE ABC S.R.L.", "123456789", "001-0001234", "Producto A", "General", 5.0, 30.0, 0.0, "Efectivo", "", ""),
                    listOf("2024-01-16", "Recibo", "COMERCIAL XYZ", "987654321", "002-0005678", "Servicio B", "Servicios", 1.0, 250.0, 0.0, "Transferencia", "", "")
                ), "4472C4"
            )
            Kind.COMPRAS -> TableData(
                "Compras",
                listOf("fecha", "tipo_documento", "razon_social", "numero_documento", "numero_factura", "producto", "categoria", "cantidad", "precio_unitario", "descuento", "excento", "tipo_pago", "numero_cheque", "banco", "estado_pago"),
                listOf(
                    listOf("2024-01-15", "Factura", "PROVEEDOR ABC S.R.L.", "123456789", "001-0001234", "Insumos", "General", 10.0, 20.0, 0.0, 0.0, "Efectivo", "", "", "Pagada"),
                    listOf("2024-01-16", "Factura", "COMERCIAL XYZ", "987654321", "002-0005678", "Materiales", "General", 2.0, 150.0, 0.0, 0.0, "Transferencia", "", "", "Por Pagar")
                ), "FF9800"
            )
            else -> TableData(
                if (kind == Kind.GASTOS) "Gastos" else "Régimen",
                listOf("fecha", "tipo_documento", "numero_factura", "numero_documento", "razon_social", "descripcion", "categoria", "cantidad", "precio_unitario", "descuento", "excento", "tipo_pago", "numero_cheque", "banco"),
                listOf(
                    listOf("2024-01-15", "Factura", "001-0001234", "123456789", "PROVEEDOR ABC S.R.L.", "Compra de suministros", "Suministros", 5.0, 30.0, 0.0, 0.0, "Efectivo", "", ""),
                    listOf("2024-01-16", "Recibo", "002-0005678", "987654321", "COMERCIAL XYZ", "Pago de servicios", "Servicios", 1.0, 250.0, 0.0, 0.0, "Transferencia", "", "")
                ), if (kind == Kind.GASTOS) "DC3545" else "00B294"
            )
        }
    }

    // ------------------------------------------------------------------ IMPORTAR
    private val COLUMN_MAP = mapOf(
        "fecha" to "fecha", "tipo_doc" to "tipo_documento", "tipo_documento" to "tipo_documento",
        "razón_social" to "razon_social", "razon_social" to "razon_social",
        "nro_documento" to "numero_documento", "numero_documento" to "numero_documento", "nit_ci" to "numero_documento",
        "nro_factura" to "numero_factura", "numero_factura" to "numero_factura", "nº_factura" to "numero_factura",
        "producto" to "producto", "detalle" to "descripcion", "descripcion" to "descripcion", "descripción" to "descripcion",
        "categoría" to "categoria", "categoria" to "categoria", "cantidad" to "cantidad",
        "precio_unit" to "precio_unitario", "precio_unitario" to "precio_unitario",
        "descuento" to "descuento", "excento" to "excento", "total" to "total",
        "tipo_pago" to "tipo_pago", "numero_cheque" to "numero_cheque", "nro_cheque" to "numero_cheque",
        "banco" to "banco", "estado_pago" to "estado_pago"
    )

    fun normalizarFilas(raw: List<Map<String, String>>): List<Map<String, String>> {
        return raw.map { r ->
            val m = LinkedHashMap<String, String>()
            for ((k, v) in r) {
                val key = k.trim().lowercase().replace(" ", "_").replace(".", "")
                m[COLUMN_MAP[key] ?: key] = v
            }
            m
        }
    }

    fun importar(kind: Kind, empresaId: Long, raw: List<Map<String, String>>): ImportResult {
        val filas = normalizarFilas(raw).map {
            if (kind.usesProducto && !it.containsKey("producto") && it.containsKey("descripcion")) it + ("producto" to it.getValue("descripcion")) else it
        }
        if (filas.isEmpty()) return ImportResult(0, 0, emptyList(), "El archivo no contiene filas de datos")
        val requeridas = when (kind) {
            Kind.VENTAS, Kind.COMPRAS -> listOf("fecha", "razon_social", "producto", "cantidad", "precio_unitario")
            else -> listOf("fecha", "tipo_documento", "numero_documento", "razon_social", "descripcion", "categoria", "cantidad", "precio_unitario")
        }
        val presentes = filas[0].keys
        val faltan = requeridas.filter { !presentes.contains(it) }
        if (faltan.isNotEmpty()) {
            return ImportResult(0, 0, emptyList(), "Faltan columnas requeridas: ${faltan.joinToString(", ")}. Columnas encontradas: ${presentes.joinToString(", ")}")
        }
        val db = DbManager.db()
        var ok = 0
        var extra = 0
        val errores = ArrayList<String>()
        db.beginTransaction()
        try {
            for ((idx, r) in filas.withIndex()) {
                try {
                    val fecha = Fmt.normalizeDate(r["fecha"])
                    val cantidad = Fmt.parseDouble(r["cantidad"]) ?: throw IllegalArgumentException("cantidad inválida")
                    val precio = Fmt.parseDouble(r["precio_unitario"]) ?: throw IllegalArgumentException("precio inválido")
                    val desc = Fmt.dbl(r["descuento"])
                    val exc = Fmt.dbl(r["excento"])
                    val tipoDoc = r["tipo_documento"].orEmpty().ifBlank { "Factura" }
                    val razon = r["razon_social"].orEmpty().trim()
                    if (razon.isEmpty()) throw IllegalArgumentException("razón social vacía")
                    val nDoc = r["numero_documento"].orEmpty().trim().removeSuffix(".0")
                    val nFac = r["numero_factura"].orEmpty().trim()
                    val tipoPago = r["tipo_pago"].orEmpty().ifBlank { "Efectivo" }
                    val cheque = r["numero_cheque"].orEmpty()
                    val banco = r["banco"].orEmpty()
                    val cat = r["categoria"].orEmpty()
                    when (kind) {
                        Kind.VENTAS -> {
                            val total = cantidad * precio - desc
                            db.insertRow(
                                "ventas",
                                mapOf(
                                    "empresa_id" to empresaId, "fecha" to fecha, "tipo_documento" to tipoDoc, "razon_social" to razon,
                                    "numero_documento" to nDoc, "numero_factura" to nFac, "producto" to r["producto"].orEmpty(),
                                    "categoria" to cat, "cantidad" to cantidad, "precio_unitario" to precio, "descuento" to desc,
                                    "total" to total, "tipo_pago" to tipoPago, "numero_cheque" to cheque, "banco" to banco
                                )
                            )
                        }
                        Kind.COMPRAS -> {
                            val total = cantidad * precio - desc - exc
                            var estado = r["estado_pago"].orEmpty().trim()
                            if (estado != "Pagada" && estado != "Por Pagar") estado = "Pagada"
                            val id = db.insertRow(
                                "compras",
                                mapOf(
                                    "empresa_id" to empresaId, "fecha" to fecha, "tipo_documento" to tipoDoc, "razon_social" to razon,
                                    "numero_documento" to nDoc, "numero_factura" to nFac, "producto" to r["producto"].orEmpty(),
                                    "categoria" to cat, "cantidad" to cantidad, "precio_unitario" to precio, "descuento" to desc,
                                    "excento" to exc, "total" to total, "tipo_pago" to tipoPago, "numero_cheque" to cheque,
                                    "banco" to banco, "estado_pago" to estado
                                )
                            )
                            if (estado == "Por Pagar") {
                                val cid = db.insertRow(
                                    "cuentas_pagar",
                                    mapOf(
                                        "empresa_id" to empresaId, "fecha_emision" to fecha, "fecha_vencimiento" to fecha,
                                        "tipo_documento" to tipoDoc, "numero_documento" to (if (nFac.isNotEmpty()) nFac else nDoc),
                                        "razon_social" to razon, "nit_ci" to nDoc,
                                        "concepto" to ("Compra: " + r["producto"].orEmpty() + (if (cat.isNotEmpty()) " - $cat" else "")),
                                        "monto_total" to total, "monto_pagado" to 0.0, "saldo" to total, "estado" to "Pendiente",
                                        "observaciones" to "Importado desde Excel - Compra #$id", "compra_id" to id
                                    )
                                )
                                db.updateRow("compras", mapOf("cuenta_pagar_id" to cid), "id = ?", listOf(id))
                                extra++
                            }
                        }
                        else -> {
                            val total = cantidad * precio - desc - exc
                            db.insertRow(
                                kind.table,
                                mapOf(
                                    "empresa_id" to empresaId, "fecha" to fecha, "tipo_documento" to tipoDoc,
                                    "numero_documento" to nDoc, "numero_factura" to nFac, "razon_social" to razon,
                                    "descripcion" to r["descripcion"].orEmpty(), "categoria" to cat, "cantidad" to cantidad,
                                    "precio_unitario" to precio, "descuento" to desc, "excento" to exc, "total" to total,
                                    "tipo_pago" to tipoPago, "numero_cheque" to cheque, "banco" to banco
                                )
                            )
                        }
                    }
                    ok++
                } catch (e: Exception) {
                    errores.add("Fila ${idx + 2}: ${e.message}")
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return ImportResult(ok, extra, errores, null)
    }
}
