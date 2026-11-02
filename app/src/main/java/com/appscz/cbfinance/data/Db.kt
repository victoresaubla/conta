package com.appscz.cbfinance.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File

typealias Rec = Map<String, Any?>

fun Rec.str(k: String): String = this[k]?.toString() ?: ""

fun Rec.dbl(k: String): Double {
    val v = this[k]
    return when (v) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull() ?: 0.0
        else -> 0.0
    }
}

fun Rec.lng(k: String): Long {
    val v = this[k]
    return when (v) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull() ?: 0L
        else -> 0L
    }
}

fun Rec.int(k: String): Int = lng(k).toInt()

/** Ejecuta un SELECT y devuelve las filas como mapas (equivalente a dict(row) en Python). */
fun SQLiteDatabase.rows(sql: String, args: List<Any?> = emptyList()): List<Rec> {
    val out = ArrayList<Rec>()
    val a = Array(args.size) { i -> args[i]?.toString() ?: "" }
    val c: Cursor = rawQuery(sql, a)
    try {
        while (c.moveToNext()) {
            val m = LinkedHashMap<String, Any?>()
            for (i in 0 until c.columnCount) {
                val name = c.getColumnName(i)
                m[name] = when (c.getType(i)) {
                    Cursor.FIELD_TYPE_NULL -> null
                    Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                    Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                    Cursor.FIELD_TYPE_BLOB -> null
                    else -> c.getString(i)
                }
            }
            out.add(m)
        }
    } finally {
        c.close()
    }
    return out
}

fun SQLiteDatabase.row(sql: String, args: List<Any?> = emptyList()): Rec? = rows(sql, args).firstOrNull()

fun SQLiteDatabase.scalarDouble(sql: String, args: List<Any?> = emptyList()): Double {
    val r = rows(sql, args).firstOrNull() ?: return 0.0
    val first = r.values.firstOrNull()
    return when (first) {
        is Number -> first.toDouble()
        is String -> first.toDoubleOrNull() ?: 0.0
        else -> 0.0
    }
}

fun SQLiteDatabase.scalarLong(sql: String, args: List<Any?> = emptyList()): Long = scalarDouble(sql, args).toLong()

private fun toContentValues(values: Map<String, Any?>): ContentValues {
    val cv = ContentValues()
    for ((k, v) in values) {
        when (v) {
            null -> cv.putNull(k)
            is String -> cv.put(k, v)
            is Double -> cv.put(k, v)
            is Float -> cv.put(k, v.toDouble())
            is Long -> cv.put(k, v)
            is Int -> cv.put(k, v.toLong())
            is Boolean -> cv.put(k, if (v) 1L else 0L)
            else -> cv.put(k, v.toString())
        }
    }
    return cv
}

fun SQLiteDatabase.insertRow(table: String, values: Map<String, Any?>): Long {
    val id = insert(table, null, toContentValues(values))
    if (id < 0) throw IllegalStateException("No se pudo insertar en $table")
    return id
}

fun SQLiteDatabase.updateRow(table: String, values: Map<String, Any?>, where: String, whereArgs: List<Any?>): Int {
    val a = Array(whereArgs.size) { i -> whereArgs[i]?.toString() ?: "" }
    return update(table, toContentValues(values), where, a)
}

fun SQLiteDatabase.exec(sql: String, args: List<Any?> = emptyList()) {
    if (args.isEmpty()) execSQL(sql) else execSQL(sql, args.toTypedArray())
}

inline fun <T> SQLiteDatabase.tx(block: () -> T): T {
    beginTransaction()
    try {
        val r = block()
        setTransactionSuccessful()
        return r
    } finally {
        endTransaction()
    }
}

/** Gestor único de la base de datos local (archivo finanzas.db dentro del almacenamiento del teléfono). */
object DbManager {
    const val DB_NAME = "finanzas.db"
    private lateinit var appContext: Context
    private var database: SQLiteDatabase? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val context: Context get() = appContext

    fun dbFile(): File = appContext.getDatabasePath(DB_NAME)

    @Synchronized
    fun db(): SQLiteDatabase {
        val current = database
        if (current != null && current.isOpen) return current
        val f = dbFile()
        f.parentFile?.mkdirs()
        val d = SQLiteDatabase.openOrCreateDatabase(f, null)
        Schema.ensure(d)
        database = d
        return d
    }

    @Synchronized
    fun close() {
        try {
            database?.close()
        } catch (_: Exception) {
        }
        database = null
    }

    /** Fuerza que todo esté escrito en el archivo principal antes de copiarlo. */
    @Synchronized
    fun flush() {
        try {
            val c = db().rawQuery("PRAGMA wal_checkpoint(FULL)", null)
            c.moveToFirst()
            c.close()
        } catch (_: Exception) {
        }
    }

    /** Reemplaza la base de datos actual por el archivo indicado (restauración). */
    @Synchronized
    fun replaceWith(src: File) {
        close()
        val f = dbFile()
        File(f.path + "-wal").delete()
        File(f.path + "-shm").delete()
        File(f.path + "-journal").delete()
        src.copyTo(f, overwrite = true)
        db() // reabre y completa tablas/columnas faltantes (por si viene de la versión web)
    }
}

/** Esquema idéntico al sistema web (database.py) + columnas agregadas por las migraciones del sistema. */
object Schema {
    private val CREATE_STATEMENTS = listOf(
        """CREATE TABLE IF NOT EXISTS usuarios (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT NOT NULL UNIQUE,
            password_hash TEXT NOT NULL,
            nombre_completo TEXT NOT NULL,
            email TEXT NOT NULL UNIQUE,
            rol TEXT NOT NULL CHECK(rol IN ('admin', 'contador')),
            activo INTEGER DEFAULT 1,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )""",
        """CREATE TABLE IF NOT EXISTS empresas (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            nombre TEXT NOT NULL,
            nit TEXT NOT NULL UNIQUE,
            direccion TEXT,
            telefono TEXT,
            email TEXT,
            logo_url TEXT,
            activo INTEGER DEFAULT 1,
            created_by INTEGER NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )""",
        """CREATE TABLE IF NOT EXISTS usuario_empresa (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            usuario_id INTEGER NOT NULL,
            empresa_id INTEGER NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(usuario_id, empresa_id)
        )""",
        """CREATE TABLE IF NOT EXISTS ventas (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            empresa_id INTEGER NOT NULL DEFAULT 1,
            fecha DATE NOT NULL,
            tipo_documento TEXT DEFAULT 'Factura',
            razon_social TEXT NOT NULL,
            numero_documento TEXT NOT NULL,
            numero_factura TEXT,
            producto TEXT NOT NULL,
            categoria TEXT,
            cantidad REAL NOT NULL,
            precio_unitario REAL NOT NULL,
            descuento REAL DEFAULT 0,
            total REAL NOT NULL,
            tipo_pago TEXT DEFAULT 'Efectivo',
            numero_cheque TEXT,
            banco TEXT,
            estado_cobro TEXT DEFAULT 'Cobrada',
            cuenta_cobrar_id INTEGER,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )""",
        """CREATE TABLE IF NOT EXISTS compras (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            empresa_id INTEGER NOT NULL DEFAULT 1,
            fecha DATE NOT NULL,
            tipo_documento TEXT DEFAULT 'Factura',
            razon_social TEXT NOT NULL,
            numero_documento TEXT NOT NULL,
            numero_factura TEXT,
            producto TEXT NOT NULL,
            categoria TEXT,
            cantidad REAL NOT NULL,
            precio_unitario REAL NOT NULL,
            descuento REAL DEFAULT 0,
            excento REAL DEFAULT 0,
            total REAL NOT NULL,
            tipo_pago TEXT DEFAULT 'Efectivo',
            numero_cheque TEXT,
            banco TEXT,
            estado_pago TEXT DEFAULT 'Pagada',
            cuenta_pagar_id INTEGER,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )""",
        """CREATE TABLE IF NOT EXISTS gastos (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            empresa_id INTEGER NOT NULL DEFAULT 1,
            fecha DATE NOT NULL,
            tipo_documento TEXT DEFAULT 'Factura',
            numero_documento TEXT NOT NULL,
            numero_factura TEXT,
            razon_social TEXT NOT NULL,
            descripcion TEXT NOT NULL,
            categoria TEXT NOT NULL,
            cantidad REAL DEFAULT 1.0,
            precio_unitario REAL DEFAULT 0.0,
            descuento REAL DEFAULT 0.0,
            excento REAL DEFAULT 0.0,
            total REAL NOT NULL,
            tipo_pago TEXT DEFAULT 'Efectivo',
            numero_cheque TEXT,
            banco TEXT,
            estado_pago TEXT DEFAULT 'Pagada',
            cuenta_pagar_id INTEGER,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )""",
        """CREATE TABLE IF NOT EXISTS planillas (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            empresa_id INTEGER NOT NULL,
            gasto_id INTEGER,
            fecha DATE NOT NULL,
            empleado TEXT NOT NULL,
            mes TEXT NOT NULL,
            monto_bruto REAL NOT NULL,
            descuento_afp_porcentaje REAL DEFAULT 12.71,
            descuento_afp_monto REAL NOT NULL,
            descuento_caja_nacional REAL DEFAULT 0,
            descuento_ministerio_trabajo REAL DEFAULT 0,
            horas_extras REAL DEFAULT 0,
            total_neto REAL NOT NULL,
            observaciones TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )""",
        """CREATE TABLE IF NOT EXISTS categorias_gastos (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            empresa_id INTEGER NOT NULL DEFAULT 1,
            nombre TEXT NOT NULL,
            descripcion TEXT,
            activo INTEGER DEFAULT 1,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(empresa_id, nombre)
        )""",
        """CREATE TABLE IF NOT EXISTS regimen (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            empresa_id INTEGER NOT NULL DEFAULT 1,
            fecha DATE NOT NULL,
            tipo_documento TEXT NOT NULL,
            numero_documento TEXT NOT NULL,
            numero_factura TEXT,
            razon_social TEXT NOT NULL,
            descripcion TEXT NOT NULL,
            categoria TEXT NOT NULL,
            cantidad REAL DEFAULT 1.0,
            precio_unitario REAL DEFAULT 0.0,
            descuento REAL DEFAULT 0.0,
            excento REAL DEFAULT 0.0,
            total REAL NOT NULL,
            tipo_pago TEXT DEFAULT 'Efectivo',
            numero_cheque TEXT,
            banco TEXT,
            estado_pago TEXT DEFAULT 'Pagada',
            cuenta_pagar_id INTEGER,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )""",
        """CREATE TABLE IF NOT EXISTS planillas_regimen (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            empresa_id INTEGER NOT NULL,
            regimen_id INTEGER,
            fecha DATE NOT NULL,
            empleado TEXT NOT NULL,
            mes TEXT NOT NULL,
            monto_bruto REAL NOT NULL,
            descuento_afp_porcentaje REAL DEFAULT 12.71,
            descuento_afp_monto REAL NOT NULL,
            descuento_caja_nacional REAL DEFAULT 0,
            descuento_ministerio_trabajo REAL DEFAULT 0,
            horas_extras REAL DEFAULT 0,
            total_neto REAL NOT NULL,
            observaciones TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )""",
        """CREATE TABLE IF NOT EXISTS categorias_regimen (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            empresa_id INTEGER NOT NULL DEFAULT 1,
            nombre TEXT NOT NULL,
            descripcion TEXT,
            activo INTEGER DEFAULT 1,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(empresa_id, nombre)
        )""",
        """CREATE TABLE IF NOT EXISTS categorias_ventas (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            empresa_id INTEGER NOT NULL DEFAULT 1,
            nombre TEXT NOT NULL,
            descripcion TEXT,
            activo INTEGER DEFAULT 1,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(empresa_id, nombre)
        )""",
        """CREATE TABLE IF NOT EXISTS categorias_compras (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            empresa_id INTEGER NOT NULL DEFAULT 1,
            nombre TEXT NOT NULL,
            descripcion TEXT,
            activo INTEGER DEFAULT 1,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(empresa_id, nombre)
        )""",
        """CREATE TABLE IF NOT EXISTS cuentas_cobrar (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            empresa_id INTEGER NOT NULL DEFAULT 1,
            fecha_emision DATE NOT NULL,
            fecha_vencimiento DATE NOT NULL,
            tipo_documento TEXT DEFAULT 'Factura',
            numero_documento TEXT,
            razon_social TEXT NOT NULL,
            nit_ci TEXT NOT NULL,
            concepto TEXT NOT NULL,
            monto_total REAL NOT NULL,
            monto_pagado REAL DEFAULT 0,
            saldo REAL NOT NULL,
            estado TEXT DEFAULT 'Pendiente',
            observaciones TEXT,
            venta_id INTEGER,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )""",
        """CREATE TABLE IF NOT EXISTS pagos_cuentas_cobrar (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            empresa_id INTEGER NOT NULL DEFAULT 1,
            cuenta_cobrar_id INTEGER NOT NULL,
            fecha_pago DATE NOT NULL,
            monto REAL NOT NULL,
            tipo_pago TEXT DEFAULT 'Efectivo',
            numero_cheque TEXT,
            banco TEXT,
            observaciones TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )""",
        """CREATE TABLE IF NOT EXISTS cuentas_pagar (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            empresa_id INTEGER NOT NULL DEFAULT 1,
            fecha_emision DATE NOT NULL,
            fecha_vencimiento DATE NOT NULL,
            tipo_documento TEXT DEFAULT 'Factura',
            numero_documento TEXT,
            razon_social TEXT NOT NULL,
            nit_ci TEXT NOT NULL,
            concepto TEXT NOT NULL,
            monto_total REAL NOT NULL,
            monto_pagado REAL DEFAULT 0,
            saldo REAL NOT NULL,
            estado TEXT DEFAULT 'Pendiente',
            observaciones TEXT,
            compra_id INTEGER,
            gasto_id INTEGER,
            regimen_id INTEGER,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )""",
        """CREATE TABLE IF NOT EXISTS pagos_cuentas_pagar (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            empresa_id INTEGER NOT NULL DEFAULT 1,
            cuenta_pagar_id INTEGER NOT NULL,
            fecha_pago DATE NOT NULL,
            monto REAL NOT NULL,
            tipo_pago TEXT DEFAULT 'Efectivo',
            numero_cheque TEXT,
            banco TEXT,
            observaciones TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )"""
    )

    /** Columnas que pueden faltar en bases de datos antiguas (migraciones). */
    private val EXTRA_COLUMNS = listOf(
        Triple("ventas", "empresa_id", "INTEGER DEFAULT 1"),
        Triple("ventas", "categoria", "TEXT"),
        Triple("ventas", "numero_factura", "TEXT"),
        Triple("ventas", "estado_cobro", "TEXT DEFAULT 'Cobrada'"),
        Triple("ventas", "cuenta_cobrar_id", "INTEGER"),
        Triple("compras", "empresa_id", "INTEGER DEFAULT 1"),
        Triple("compras", "categoria", "TEXT"),
        Triple("compras", "numero_factura", "TEXT"),
        Triple("compras", "estado_pago", "TEXT DEFAULT 'Pagada'"),
        Triple("compras", "cuenta_pagar_id", "INTEGER"),
        Triple("gastos", "empresa_id", "INTEGER DEFAULT 1"),
        Triple("gastos", "numero_factura", "TEXT"),
        Triple("gastos", "estado_pago", "TEXT DEFAULT 'Pagada'"),
        Triple("gastos", "cuenta_pagar_id", "INTEGER"),
        Triple("regimen", "numero_factura", "TEXT"),
        Triple("regimen", "estado_pago", "TEXT DEFAULT 'Pagada'"),
        Triple("regimen", "cuenta_pagar_id", "INTEGER"),
        Triple("cuentas_cobrar", "empresa_id", "INTEGER DEFAULT 1"),
        Triple("cuentas_cobrar", "venta_id", "INTEGER"),
        Triple("cuentas_pagar", "empresa_id", "INTEGER DEFAULT 1"),
        Triple("cuentas_pagar", "compra_id", "INTEGER"),
        Triple("cuentas_pagar", "gasto_id", "INTEGER"),
        Triple("cuentas_pagar", "regimen_id", "INTEGER"),
        Triple("pagos_cuentas_cobrar", "empresa_id", "INTEGER DEFAULT 1"),
        Triple("pagos_cuentas_pagar", "empresa_id", "INTEGER DEFAULT 1")
    )

    private val INDEXES = listOf(
        Triple("idx_gastos_empresa", "gastos", "empresa_id"),
        Triple("idx_gastos_fecha", "gastos", "fecha"),
        Triple("idx_gastos_categoria", "gastos", "categoria"),
        Triple("idx_planillas_empresa", "planillas", "empresa_id"),
        Triple("idx_planillas_fecha", "planillas", "fecha"),
        Triple("idx_planillas_mes", "planillas", "mes"),
        Triple("idx_planillas_gasto", "planillas", "gasto_id"),
        Triple("idx_compras_empresa", "compras", "empresa_id"),
        Triple("idx_compras_fecha", "compras", "fecha"),
        Triple("idx_ventas_empresa", "ventas", "empresa_id"),
        Triple("idx_ventas_fecha", "ventas", "fecha"),
        Triple("idx_regimen_empresa", "regimen", "empresa_id"),
        Triple("idx_regimen_fecha", "regimen", "fecha"),
        Triple("idx_cuentas_cobrar_empresa", "cuentas_cobrar", "empresa_id"),
        Triple("idx_cuentas_pagar_empresa", "cuentas_pagar", "empresa_id"),
        Triple("idx_ventas_emp_fecha", "ventas", "empresa_id, fecha"),
        Triple("idx_compras_emp_fecha", "compras", "empresa_id, fecha"),
        Triple("idx_gastos_emp_fecha", "gastos", "empresa_id, fecha"),
        Triple("idx_regimen_emp_fecha", "regimen", "empresa_id, fecha"),
        Triple("idx_planillas_emp_fecha", "planillas", "empresa_id, fecha"),
        Triple("idx_cc_emp_fecha", "cuentas_cobrar", "empresa_id, fecha_emision"),
        Triple("idx_cp_emp_fecha", "cuentas_pagar", "empresa_id, fecha_emision"),
        Triple("idx_pagos_cc", "pagos_cuentas_cobrar", "cuenta_cobrar_id"),
        Triple("idx_pagos_cp", "pagos_cuentas_pagar", "cuenta_pagar_id"),
        Triple("idx_ue_usuario", "usuario_empresa", "usuario_id")
    )

    val REQUIRED_TABLES = listOf("usuarios", "empresas", "usuario_empresa", "ventas", "compras", "gastos")

    fun ensure(db: SQLiteDatabase) {
        for (sql in CREATE_STATEMENTS) {
            db.execSQL(sql)
        }
        for ((table, column, ddl) in EXTRA_COLUMNS) {
            try {
                val cols = db.rows("PRAGMA table_info($table)").map { it.str("name") }
                if (!cols.contains(column)) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN $column $ddl")
                }
            } catch (_: Exception) {
            }
        }
        for ((name, table, column) in INDEXES) {
            try {
                db.execSQL("CREATE INDEX IF NOT EXISTS $name ON $table($column)")
            } catch (_: Exception) {
            }
        }
        seedIfEmpty(db)
    }

    private fun seedIfEmpty(db: SQLiteDatabase) {
        val admins = db.scalarLong("SELECT COUNT(*) FROM usuarios WHERE rol = 'admin'")
        if (admins > 0L) return
        db.tx {
            val adminId = db.insertRow(
                "usuarios",
                mapOf(
                    "username" to "admin",
                    "password_hash" to Security.hash("admin123"),
                    "nombre_completo" to "Administrador",
                    "email" to "admin@finance.com",
                    "rol" to "admin",
                    "activo" to 1
                )
            )
            val empresaId = db.insertRow(
                "empresas",
                mapOf(
                    "nombre" to "Empresa Principal",
                    "nit" to "0000000-0",
                    "direccion" to "La Paz, Bolivia",
                    "activo" to 1,
                    "created_by" to adminId
                )
            )
            db.insertRow("usuario_empresa", mapOf("usuario_id" to adminId, "empresa_id" to empresaId))
            val defaults = listOf(
                "Alquiler" to "Gastos de arriendo de local u oficina",
                "Servicios" to "Servicios básicos: luz, agua, internet, teléfono",
                "Salarios" to "Sueldos y salarios del personal",
                "Marketing" to "Publicidad y marketing",
                "Mantenimiento" to "Mantenimiento y reparaciones",
                "Transporte" to "Gastos de transporte y logística",
                "Suministros" to "Suministros y materiales de oficina",
                "Impuestos" to "Impuestos y tributos",
                "Seguros" to "Pólizas de seguros",
                "Otros" to "Otros gastos varios"
            )
            for ((n, d) in defaults) {
                db.insertRow(
                    "categorias_gastos",
                    mapOf("empresa_id" to empresaId, "nombre" to n, "descripcion" to d, "activo" to 1)
                )
            }
        }
    }
}
