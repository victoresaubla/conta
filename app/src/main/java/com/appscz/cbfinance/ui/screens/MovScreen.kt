@file:OptIn(ExperimentalMaterial3Api::class)

package com.appscz.cbfinance.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appscz.cbfinance.data.*
import com.appscz.cbfinance.ui.*
import com.appscz.cbfinance.util.DownloadsSaver
import com.appscz.cbfinance.util.Files
import com.appscz.cbfinance.util.Fmt
import com.appscz.cbfinance.util.Xlsx
import com.appscz.cbfinance.util.io
import kotlinx.coroutines.launch
import java.io.File
import java.math.BigDecimal

const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

fun numStr(v: Double): String = BigDecimal.valueOf(v).stripTrailingZeros().toPlainString()

fun kindColor(k: Kind): Color = when (k) {
    Kind.VENTAS -> CB.Green
    Kind.COMPRAS -> CB.Amber
    Kind.GASTOS -> CB.Red
    Kind.REGIMEN -> CB.Teal
}

/** Genera el Excel y lo guarda directamente en Descargas/CB_Consultores. Devuelve la ruta. */
suspend fun downloadExcel(ctx: android.content.Context, t: TableData, prefix: String): String {
    return io {
        val file = File(Files.exportsDir(ctx), prefix + "_" + Files.stamp() + ".xlsx")
        file.outputStream().use { Xlsx.write(it, t) }
        DownloadsSaver.deliver(ctx, file, XLSX_MIME)
    }
}

@Composable
fun MovScreen(kind: Kind, empresaId: Long) {
    val ctx = LocalContext.current
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    val color = kindColor(kind)

    var fiIn by remember { mutableStateOf("") }
    var ffIn by remember { mutableStateOf("") }
    var razonIn by remember { mutableStateOf("") }
    var fi by remember { mutableStateOf("") }
    var ff by remember { mutableStateOf("") }
    var razon by remember { mutableStateOf("") }
    var page by remember { mutableStateOf(1) }
    var perPage by remember { mutableStateOf(25) }
    var tick by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<PageResult?>(null) }
    var razones by remember { mutableStateOf<List<String>>(emptyList()) }
    var formOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Rec?>(null) }
    var deleteId by remember { mutableStateOf<Long?>(null) }
    var showCats by remember { mutableStateOf(false) }
    var showMass by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var detalle by remember { mutableStateOf<Rec?>(null) }

    LaunchedEffect(fi, ff, razon, page, perPage, tick) {
        result = io { MovRepo.listar(kind, empresaId, fi, ff, razon, page, perPage) }
    }
    LaunchedEffect(tick) {
        razones = io { MovRepo.razonesSociales(kind, empresaId) }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val name = Files.displayName(ctx, uri)
                    val res = io {
                        val stream = ctx.contentResolver.openInputStream(uri) ?: throw IllegalStateException("No se pudo abrir el archivo")
                        val rows = stream.use { Xlsx.read(it, name) }
                        MovRepo.importar(kind, empresaId, rows)
                    }
                    val fatal = res.fatal
                    if (fatal != null) {
                        notify(fatal, false)
                    } else {
                        var msg = "Se importaron ${res.inserted} registros correctamente"
                        if (res.extra > 0) msg += ". Se crearon ${res.extra} cuentas por pagar"
                        if (res.errors.isNotEmpty()) msg += ". ${res.errors.size} filas con errores: " + res.errors.take(3).joinToString(" | ")
                        notify(msg, res.errors.isEmpty())
                        page = 1
                        tick++
                    }
                } catch (e: Exception) {
                    notify("Error al importar archivo: ${e.message}", false)
                }
            }
        }
    }

    BackHandler(enabled = formOpen) {
        formOpen = false
        editing = null
    }

    if (formOpen) {
        MovForm(
            kind = kind, empresaId = empresaId, editing = editing,
            onDone = { formOpen = false; editing = null; page = 1; tick++ },
            onCancel = { formOpen = false; editing = null }
        )
        return
    }

    val res = result
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                SectionCard(
                    "Filtros", headerColor = CB.Secondary,
                    actions = {
                        IconButton(onClick = { showFilters = !showFilters }) {
                            Icon(if (showFilters) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = Color.White)
                        }
                    }
                ) {
                    if (showFilters || fi.isNotBlank() || razon.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DateField("Desde", fiIn, { fiIn = it }, Modifier.weight(1f))
                                DateField("Hasta", ffIn, { ffIn = it }, Modifier.weight(1f))
                            }
                            DropdownField("Razón social", razones, razonIn, { razonIn = it }, allowEmptyLabel = "— Todas —")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(onClick = { fi = fiIn; ff = ffIn; razon = razonIn; page = 1 }, modifier = Modifier.weight(1f)) { Text("Filtrar") }
                                OutlinedButton(onClick = {
                                    fiIn = ""; ffIn = ""; razonIn = ""; fi = ""; ff = ""; razon = ""; page = 1
                                }, modifier = Modifier.weight(1f)) { Text("Limpiar") }
                            }
                        }
                    } else {
                        Text("Toca la flecha para filtrar por fechas o razón social", fontSize = 12.sp, color = CB.Gray)
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {
                            scope.launch {
                                try {
                                    val t = io { MovRepo.exportarDatos(kind, empresaId, fi, ff) }
                                    notify("Excel guardado en " + downloadExcel(ctx, t, kind.table), true)
                                } catch (e: Exception) {
                                    notify("No se pudo exportar: ${e.message}", false)
                                }
                            }
                        },
                        label = { Text("Exportar Excel") },
                        leadingIcon = { Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    AssistChip(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        label = { Text("Importar Excel") },
                        leadingIcon = { Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    AssistChip(
                        onClick = {
                            scope.launch {
                                try {
                                    notify("Plantilla guardada en " + downloadExcel(ctx, MovRepo.plantilla(kind), "plantilla_" + kind.table), true)
                                } catch (e: Exception) {
                                    notify("No se pudo generar la plantilla: ${e.message}", false)
                                }
                            }
                        },
                        label = { Text("Plantilla") },
                        leadingIcon = { Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    AssistChip(
                        onClick = { showCats = true },
                        label = { Text("Categorías") },
                        leadingIcon = { Icon(Icons.Filled.Category, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    AssistChip(
                        onClick = { showMass = true },
                        label = { Text("Eliminar por rango") },
                        leadingIcon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
            if (res == null) {
                item {
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            } else {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(color.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${res.total} registro(s)", fontSize = 12.sp, color = CB.Gray)
                            Text("Total filtrado", fontSize = 11.sp, color = CB.Gray)
                        }
                        Text(Fmt.bs(res.sumTotal), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = color)
                    }
                }
                if (res.rows.isEmpty()) {
                    item { EmptyState("No hay registros para mostrar") }
                }
                items(res.rows, key = { it.lng("id") }) { r ->
                    RecordCard(
                        kind = kind, r = r, color = color,
                        onDelete = { deleteId = r.lng("id") },
                        onEdit = { editing = r; formOpen = true },
                        onDetail = { detalle = r }
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { if (page > 1) page-- }, enabled = page > 1) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Anterior") }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Página $page de ${maxOf(res.totalPages, 1)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (n in listOf(25, 50, 100, 200)) {
                                    FilterChip(selected = perPage == n, onClick = { perPage = n; page = 1 }, label = { Text("$n", fontSize = 11.sp) })
                                }
                            }
                        }
                        IconButton(onClick = { if (page < res.totalPages) page++ }, enabled = page < res.totalPages) { Icon(Icons.Filled.ChevronRight, contentDescription = "Siguiente") }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { editing = null; formOpen = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(kind.newLabel) },
            containerColor = CB.Accent,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }

    val del = deleteId
    if (del != null) {
        ConfirmDialog(
            title = "Eliminar registro", text = "¿Eliminar el registro #$del? Esta acción no se puede deshacer.",
            confirmText = "Eliminar", danger = true,
            onConfirm = {
                deleteId = null
                scope.launch {
                    val r = io { MovRepo.eliminar(kind, empresaId, del) }
                    notify(r.message, r.ok)
                    tick++
                }
            },
            onDismiss = { deleteId = null }
        )
    }
    if (showMass) {
        MassDeleteDialog(onDismiss = { showMass = false }, onConfirm = { a, b ->
            showMass = false
            scope.launch {
                val r = io { MovRepo.eliminarMasivo(kind, empresaId, a, b) }
                notify(r.message, r.ok)
                page = 1
                tick++
            }
        })
    }
    if (showCats) {
        CategoriasDialog(kind, empresaId, onClose = { showCats = false })
    }
    val det = detalle
    if (det != null) {
        PlanillaDialog(kind, empresaId, det.lng("id"), onClose = { detalle = null })
    }
}

@Composable
private fun RecordCard(kind: Kind, r: Rec, color: Color, onDelete: () -> Unit, onEdit: () -> Unit, onDetail: () -> Unit) {
    val esPlanilla = r.str("tipo_documento") == "Planilla"
    val estado = if (esPlanilla) "Planilla" else r.str(kind.estadoCol).ifBlank { if (kind == Kind.VENTAS) "Cobrada" else "Pagada" }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(2.dp, color),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#${r.lng("id")} · ${Fmt.fecha(r.str("fecha"))}", fontSize = 12.sp, color = CB.Gray, modifier = Modifier.weight(1f))
                    StatusChip(estado)
                }
                Spacer(Modifier.height(4.dp))
                Text(r.str("razon_social"), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = CB.Title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val doc = buildString {
                    append(r.str("tipo_documento"))
                    if (r.str("numero_factura").isNotBlank()) append(" Nº ").append(r.str("numero_factura"))
                    if (r.str("numero_documento").isNotBlank() && r.str("numero_documento") != "-") append(" · NIT/CI ").append(r.str("numero_documento"))
                }
                Text(doc, fontSize = 12.sp, color = CB.Gray)
                val det = r.str(kind.detailCol)
                if (det.isNotBlank() && det != "-") Text(det, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (r.str("categoria").isNotBlank()) {
                    Text(
                        r.str("categoria"), fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (!esPlanilla) Text("${Fmt.qty(r.dbl("cantidad"))} × ${Fmt.money(r.dbl("precio_unitario"))}", fontSize = 12.sp, color = CB.Gray)
                        Text(r.str("tipo_pago"), fontSize = 11.sp, color = CB.Gray)
                    }
                    Text(Fmt.bs(r.dbl("total")), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = color)
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    if (esPlanilla) IconButton(onClick = onDetail) { Icon(Icons.Filled.Visibility, contentDescription = "Ver planilla", tint = CB.Accent) }
                    if (kind.hasPlanilla) IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Editar", tint = CB.Warning) }
                    IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = CB.Danger) }
                }
            }
        }
    }
}

@Composable
private fun MassDeleteDialog(onDismiss: () -> Unit, onConfirm: (Long, Long) -> Unit) {
    val notify = LocalNotify.current
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eliminar por rango de IDs", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Se eliminarán todos los registros de esta empresa cuyo ID esté dentro del rango. No se puede deshacer.", fontSize = 13.sp)
                OutlinedTextField(a, { a = it.filter { c -> c.isDigit() } }, label = { Text("ID inicio") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                OutlinedTextField(b, { b = it.filter { c -> c.isDigit() } }, label = { Text("ID fin") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val x = a.toLongOrNull()
                    val y = b.toLongOrNull()
                    if (x == null || y == null) notify("Debes especificar el ID de inicio y fin", false) else onConfirm(x, y)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CB.Danger)
            ) { Text("Eliminar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun PlanillaDialog(kind: Kind, empresaId: Long, id: Long, onClose: () -> Unit) {
    var d by remember { mutableStateOf<Rec?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        d = io { MovRepo.detallePlanilla(kind, empresaId, id) }
        loaded = true
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Detalle de planilla", fontWeight = FontWeight.Bold) },
        text = {
            val p = d
            if (p == null) {
                if (loaded) Text("Planilla no encontrada") else CircularProgressIndicator()
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    LabeledValue("Empleado", p.str("empleado"), bold = true)
                    LabeledValue("Mes", p.str("mes"))
                    LabeledValue("Fecha", Fmt.fecha(p.str("fecha")))
                    LabeledValue("Categoría", p.str("categoria"))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    LabeledValue("Monto bruto", Fmt.bs(p.dbl("monto_bruto")))
                    LabeledValue("Descuento AFP (${Fmt.money(p.dbl("descuento_afp_porcentaje"))}%)", "- " + Fmt.bs(p.dbl("descuento_afp_monto")), color = CB.Danger)
                    LabeledValue("Caja Nacional", "- " + Fmt.bs(p.dbl("descuento_caja_nacional")), color = CB.Danger)
                    LabeledValue("Ministerio de Trabajo", "- " + Fmt.bs(p.dbl("descuento_ministerio_trabajo")), color = CB.Danger)
                    LabeledValue("Horas extras", "+ " + Fmt.bs(p.dbl("horas_extras")), color = CB.Success)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    LabeledValue("TOTAL NETO", Fmt.bs(p.dbl("total_neto")), bold = true, color = CB.Title)
                    if (p.str("observaciones").isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(p.str("observaciones"), fontSize = 12.sp, color = CB.Gray)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Cerrar") } }
    )
}

@Composable
fun CategoriasDialog(kind: Kind, empresaId: Long, onClose: () -> Unit) {
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    var cats by remember { mutableStateOf<List<Rec>>(emptyList()) }
    var tick by remember { mutableStateOf(0) }
    var nombre by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    LaunchedEffect(tick) { cats = io { MovRepo.categoriasTodas(kind, empresaId) } }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Categorías de ${kind.title}", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Nueva categoría") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(desc, { desc = it }, label = { Text("Descripción (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Button(onClick = {
                    scope.launch {
                        val r = io { MovRepo.agregarCategoria(kind, empresaId, nombre, desc) }
                        notify(r.message, r.ok && !r.warning)
                        if (r.ok) {
                            nombre = ""; desc = ""
                        }
                        tick++
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Agregar categoría") }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                if (cats.isEmpty()) Text("Aún no hay categorías", color = CB.Gray, fontSize = 13.sp)
                for (c in cats) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(c.str("nombre"), fontWeight = FontWeight.SemiBold)
                            if (c.str("descripcion").isNotBlank()) Text(c.str("descripcion"), fontSize = 11.sp, color = CB.Gray)
                        }
                        Text(if (c.lng("activo") == 1L) "Activa" else "Inactiva", fontSize = 11.sp, color = if (c.lng("activo") == 1L) CB.Success else CB.Gray)
                        IconButton(onClick = {
                            scope.launch {
                                val r = io { MovRepo.eliminarCategoria(kind, empresaId, c.lng("id")) }
                                notify(r.message, r.ok && !r.warning)
                                tick++
                            }
                        }) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = CB.Danger) }
                    }
                    HorizontalDivider(color = CB.Line)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Cerrar") } }
    )
}

// =================================================================== FORMULARIO
@Composable
fun MovForm(kind: Kind, empresaId: Long, editing: Rec?, onDone: () -> Unit, onCancel: () -> Unit) {
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    val isEdit = editing != null
    val docOptions = if (kind.hasPlanilla) listOf("Factura", "Recibo", "Planilla") else listOf("Factura", "Recibo", "Intercambio")
    val pagoOptions = listOf("Efectivo", "Transferencia", "Cheque", "QR")
    val color = kindColor(kind)

    fun s(k: String, d: String = ""): String {
        val v = editing?.str(k) ?: ""
        return if (v.isEmpty()) d else v
    }

    var fecha by remember { mutableStateOf(s("fecha", Fmt.today())) }
    var tipoDoc by remember { mutableStateOf(s("tipo_documento", "Factura")) }
    var razon by remember { mutableStateOf(s("razon_social")) }
    var nit by remember { mutableStateOf(s("numero_documento").let { if (it == "-") "" else it }) }
    var nfac by remember { mutableStateOf(s("numero_factura")) }
    var detalle by remember { mutableStateOf(s(kind.detailCol).let { if (it == "-") "" else it }) }
    var categoria by remember { mutableStateOf(s("categoria")) }
    var cant by remember { mutableStateOf(if (editing != null) numStr(editing.dbl("cantidad")) else "1") }
    var precio by remember { mutableStateOf(if (editing != null) numStr(editing.dbl("precio_unitario")) else "") }
    var desc by remember { mutableStateOf(if (editing != null) numStr(editing.dbl("descuento")) else "0") }
    var exc by remember { mutableStateOf(if (editing != null) numStr(editing.dbl("excento")) else "0") }
    var tipoPago by remember { mutableStateOf(s("tipo_pago", "Efectivo")) }
    var cheque by remember { mutableStateOf(s("numero_cheque")) }
    var banco by remember { mutableStateOf(s("banco")) }
    var credito by remember { mutableStateOf(false) }
    var venc by remember { mutableStateOf("") }
    var obs by remember { mutableStateOf("") }
    var empleado by remember { mutableStateOf("") }
    var mes by remember { mutableStateOf(Fmt.currentMonth()) }
    var bruto by remember { mutableStateOf("") }
    var afp by remember { mutableStateOf("19.92") }
    var caja by remember { mutableStateOf("0") }
    var minis by remember { mutableStateOf("0") }
    var horas by remember { mutableStateOf("0") }
    var cats by remember { mutableStateOf<List<String>>(emptyList()) }
    var catTick by remember { mutableStateOf(0) }
    var showNewCat by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var dupMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(catTick) {
        cats = io { MovRepo.categoriasActivas(kind, empresaId).map { it.str("nombre") } }
    }
    LaunchedEffect(editing) {
        if (editing != null && editing.str("tipo_documento") == "Planilla") {
            val d = io { MovRepo.detallePlanilla(kind, empresaId, editing.lng("id")) }
            if (d != null) {
                empleado = d.str("empleado"); mes = d.str("mes")
                bruto = numStr(d.dbl("monto_bruto")); afp = numStr(d.dbl("descuento_afp_porcentaje"))
                caja = numStr(d.dbl("descuento_caja_nacional")); minis = numStr(d.dbl("descuento_ministerio_trabajo"))
                horas = numStr(d.dbl("horas_extras"))
                detalle = d.str("observaciones")
            }
        }
    }

    val esPlanilla = tipoDoc == "Planilla"
    val total = Fmt.dbl(cant) * Fmt.dbl(precio) - Fmt.dbl(desc) - (if (kind.hasExcento) Fmt.dbl(exc) else 0.0)
    val afpMonto = Fmt.dbl(bruto) * (Fmt.dbl(afp) / 100.0)
    val neto = Fmt.dbl(bruto) - afpMonto - Fmt.dbl(caja) - Fmt.dbl(minis) + Fmt.dbl(horas)

    fun save(confirm: Boolean) {
        if (saving) return
        val input = MovInput(
            fecha = fecha, tipoDocumento = tipoDoc, razonSocial = razon, numeroDocumento = nit, numeroFactura = nfac,
            detalle = detalle, categoria = categoria, cantidad = Fmt.dbl(cant), precioUnitario = Fmt.dbl(precio),
            descuento = Fmt.dbl(desc), excento = Fmt.dbl(exc), tipoPago = tipoPago, numeroCheque = cheque, banco = banco,
            credito = credito, fechaVencimiento = venc, observaciones = obs, empleado = empleado, mes = mes,
            montoBruto = Fmt.dbl(bruto), afpPorcentaje = Fmt.dbl(afp, 19.92), cajaNacional = Fmt.dbl(caja),
            ministerioTrabajo = Fmt.dbl(minis), horasExtras = Fmt.dbl(horas)
        )
        saving = true
        val editId = editing?.lng("id") ?: 0L
        scope.launch {
            val r = io {
                if (isEdit) MovRepo.editar(kind, empresaId, editId, input)
                else MovRepo.agregar(kind, empresaId, input, confirm)
            }
            saving = false
            if (r.duplicate) {
                dupMsg = r.message
            } else {
                notify(r.message, r.ok)
                if (r.ok) onDone()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(color)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White) }
            Text(
                (if (isEdit) "Editar " + kind.singular else kind.newLabel) + (if (isEdit) " #${editing?.lng("id")}" else ""),
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { save(false) }, enabled = !saving) {
                Text(if (saving) "Guardando..." else "GUARDAR", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField("Fecha *", fecha, { fecha = it }, Modifier.weight(1f))
                if (isEdit) AppField(tipoDoc, {}, "Tipo documento", Modifier.weight(1f), readOnly = true)
                else DropdownField("Tipo documento *", docOptions, tipoDoc, { tipoDoc = it }, Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                DropdownField("Categoría *", cats, categoria, { categoria = it }, Modifier.weight(1f))
                IconButton(onClick = { showNewCat = true }) { Icon(Icons.Filled.AddCircle, contentDescription = "Nueva categoría", tint = CB.Accent) }
            }

            if (esPlanilla) {
                SectionCard("Datos de la planilla", headerColor = CB.Blue) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppField(empleado, { empleado = it }, "Empleado *")
                        DateField("Mes *", mes, { mes = it }, monthOnly = true)
                        NumField(bruto, { bruto = it }, "Monto bruto (Bs.) *")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumField(afp, { afp = it }, "AFP %", Modifier.weight(1f))
                            AppField(Fmt.money(afpMonto), {}, "AFP monto", Modifier.weight(1f), readOnly = true)
                        }
                        NumField(caja, { caja = it }, "Caja Nacional")
                        NumField(minis, { minis = it }, "Ministerio de Trabajo")
                        NumField(horas, { horas = it }, "Horas extras (monto)")
                        AppField(detalle, { detalle = it }, "Descripción / observaciones", singleLine = false)
                        TotalBox("TOTAL NETO", neto, CB.Blue)
                    }
                }
            } else {
                SectionCard("Datos del documento", headerColor = CB.Secondary) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppField(razon, { razon = it }, if (kind == Kind.VENTAS) "Cliente / Razón social *" else "Proveedor / Razón social *")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppField(nit, { nit = it }, if (kind.hasPlanilla) "NIT/CI *" else "NIT/CI", Modifier.weight(1f))
                            AppField(nfac, { nfac = it }, "Nº factura", Modifier.weight(1f))
                        }
                        AppField(detalle, { detalle = it }, if (kind.usesProducto) "Detalle / Producto *" else "Descripción", singleLine = false)
                    }
                }
                SectionCard("Importes", headerColor = color) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumField(cant, { cant = it }, "Cantidad", Modifier.weight(1f))
                            NumField(precio, { precio = it }, "Precio unit.", Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumField(desc, { desc = it }, "Descuento", Modifier.weight(1f))
                            if (kind.hasExcento) NumField(exc, { exc = it }, "Excento", Modifier.weight(1f))
                            else Spacer(Modifier.weight(1f))
                        }
                        TotalBox("TOTAL", total, color)
                    }
                }
                SectionCard("Forma de pago", headerColor = CB.Secondary) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DropdownField("Tipo de pago *", pagoOptions, tipoPago, { tipoPago = it })
                        if (tipoPago == "Cheque") AppField(cheque, { cheque = it }, "Nº de cheque")
                        if (tipoPago == "Cheque" || tipoPago == "Transferencia") AppField(banco, { banco = it }, "Banco")
                        if (!isEdit) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(if (kind == Kind.VENTAS) "Venta a crédito" else "A crédito", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (kind == Kind.VENTAS) "Se registra automáticamente en Cuentas por Cobrar" else "Se registra automáticamente en Cuentas por Pagar",
                                        fontSize = 11.sp, color = CB.Gray
                                    )
                                }
                                Switch(checked = credito, onCheckedChange = { credito = it })
                            }
                            if (credito) {
                                DateField("Fecha de vencimiento", venc, { venc = it })
                                AppField(obs, { obs = it }, "Observaciones", singleLine = false)
                            }
                        }
                    }
                }
            }
            Button(
                onClick = { save(false) }, enabled = !saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = color)
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (isEdit) "Guardar cambios" else "Registrar ${kind.singular.lowercase()}", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showNewCat) {
        var n by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewCat = false },
            title = { Text("Nueva categoría") },
            text = { OutlinedTextField(n, { n = it }, label = { Text("Nombre") }, singleLine = true) },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val r = io { MovRepo.agregarCategoria(kind, empresaId, n, "") }
                        notify(r.message, r.ok && !r.warning)
                        if (r.ok) categoria = n.trim()
                        showNewCat = false
                        catTick++
                    }
                }) { Text("Crear") }
            },
            dismissButton = { TextButton(onClick = { showNewCat = false }) { Text("Cancelar") } }
        )
    }
    val dm = dupMsg
    if (dm != null) {
        AlertDialog(
            onDismissRequest = { dupMsg = null },
            title = { Text("⚠️ Posible registro duplicado", fontWeight = FontWeight.Bold) },
            text = { Text(dm + "\n\n¿Deseas registrarlo de todos modos?") },
            confirmButton = { Button(onClick = { dupMsg = null; save(true) }) { Text("Registrar igual") } },
            dismissButton = { TextButton(onClick = { dupMsg = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun TotalBox(label: String, value: Double, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontWeight = FontWeight.Bold, color = CB.Gray, modifier = Modifier.weight(1f))
        Text(Fmt.bs(value), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = color)
    }
}
