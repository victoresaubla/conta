@file:OptIn(ExperimentalMaterial3Api::class)

package com.appscz.cbfinance.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.appscz.cbfinance.util.Files
import com.appscz.cbfinance.util.Fmt
import com.appscz.cbfinance.util.Xlsx
import com.appscz.cbfinance.util.io
import kotlinx.coroutines.launch

@Composable
fun CuentasScreen(tipo: CuentaTipo, empresaId: Long) {
    val ctx = LocalContext.current
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    val cobrar = tipo == CuentaTipo.COBRAR
    val color = if (cobrar) CB.Green else CB.Red

    var fiIn by remember { mutableStateOf("") }
    var ffIn by remember { mutableStateOf("") }
    var parteIn by remember { mutableStateOf("") }
    var estadoIn by remember { mutableStateOf("") }
    var fi by remember { mutableStateOf("") }
    var ff by remember { mutableStateOf("") }
    var parte by remember { mutableStateOf("") }
    var estado by remember { mutableStateOf("") }
    var tick by remember { mutableStateOf(0) }
    var res by remember { mutableStateOf<CuentasResult?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    var formOpen by remember { mutableStateOf(false) }
    var pagoDe by remember { mutableStateOf<Rec?>(null) }
    var historialDe by remember { mutableStateOf<Rec?>(null) }
    var deleteId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(fi, ff, parte, estado, tick) {
        res = io { CuentasRepo.listar(tipo, empresaId, fi, ff, parte, estado) }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val name = Files.displayName(ctx, uri)
                    val r = io {
                        val stream = ctx.contentResolver.openInputStream(uri) ?: throw IllegalStateException("No se pudo abrir el archivo")
                        val rows = stream.use { Xlsx.read(it, name) }
                        CuentasRepo.importar(tipo, empresaId, rows)
                    }
                    val fatal = r.fatal
                    if (fatal != null) notify(fatal, false)
                    else {
                        var msg = "Se importaron ${r.inserted} cuentas correctamente"
                        if (r.errors.isNotEmpty()) msg += ". ${r.errors.size} filas con errores: " + r.errors.take(3).joinToString(" | ")
                        notify(msg, r.errors.isEmpty())
                        tick++
                    }
                } catch (e: Exception) {
                    notify("Error al importar archivo: ${e.message}", false)
                }
            }
        }
    }

    BackHandler(enabled = formOpen) { formOpen = false }
    if (formOpen) {
        CuentaForm(tipo, empresaId, onDone = { formOpen = false; tick++ }, onCancel = { formOpen = false })
        return
    }

    val data = res
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (data != null) {
                item {
                    KpiGrid(
                        listOf(
                            KpiItem(tipo.totalLabel, Fmt.bs(data.totalPendiente), "Saldo pendiente", CB.Blue, Icons.Filled.AccountBalanceWallet),
                            KpiItem(tipo.doneLabel, Fmt.bs(data.totalHecho), "Cuentas saldadas", CB.Green, Icons.Filled.CheckCircle),
                            KpiItem("Vencido", Fmt.bs(data.totalVencido), "Requiere atención", CB.Red, Icons.Filled.Warning),
                            KpiItem("Por Vencer", Fmt.bs(data.totalPorVencer), "Dentro de plazo", CB.Amber, Icons.Filled.Schedule)
                        )
                    )
                }
            }
            item {
                SectionCard(
                    "Filtros", headerColor = CB.Secondary,
                    actions = {
                        IconButton(onClick = { showFilters = !showFilters }) {
                            Icon(if (showFilters) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = Color.White)
                        }
                    }
                ) {
                    if (showFilters || fi.isNotBlank() || parte.isNotBlank() || estado.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DateField("Emisión desde", fiIn, { fiIn = it }, Modifier.weight(1f))
                                DateField("Emisión hasta", ffIn, { ffIn = it }, Modifier.weight(1f))
                            }
                            DropdownField(tipo.party, data?.partes ?: emptyList(), parteIn, { parteIn = it }, allowEmptyLabel = "— Todos —")
                            DropdownField("Estado", listOf("Pendiente", "Parcial", "Pagada"), estadoIn, { estadoIn = it }, allowEmptyLabel = "— Todos —")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(onClick = { fi = fiIn; ff = ffIn; parte = parteIn; estado = estadoIn }, modifier = Modifier.weight(1f)) { Text("Filtrar") }
                                OutlinedButton(onClick = {
                                    fiIn = ""; ffIn = ""; parteIn = ""; estadoIn = ""; fi = ""; ff = ""; parte = ""; estado = ""
                                }, modifier = Modifier.weight(1f)) { Text("Limpiar") }
                            }
                        }
                    } else {
                        Text("Toca la flecha para filtrar", fontSize = 12.sp, color = CB.Gray)
                    }
                }
            }
            item {
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {
                            scope.launch {
                                try {
                                    val t = io { CuentasRepo.exportarDatos(tipo, empresaId, fi, ff) }
                                    shareExcel(ctx, t, tipo.table)
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
                                    shareExcel(ctx, CuentasRepo.plantilla(tipo), "plantilla_" + tipo.table)
                                } catch (e: Exception) {
                                    notify("No se pudo generar la plantilla: ${e.message}", false)
                                }
                            }
                        },
                        label = { Text("Plantilla") },
                        leadingIcon = { Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
            if (data == null) {
                item {
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            } else {
                if (data.rows.isEmpty()) item { EmptyState("No hay cuentas registradas") }
                items(data.rows, key = { it.lng("id") }) { c ->
                    CuentaCard(
                        tipo, c, color,
                        onPago = { pagoDe = c },
                        onHistorial = { historialDe = c },
                        onDelete = { deleteId = c.lng("id") }
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { formOpen = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(if (cobrar) "Nueva cuenta por cobrar" else "Nueva cuenta por pagar") },
            containerColor = CB.Accent, contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }

    val del = deleteId
    if (del != null) {
        ConfirmDialog(
            title = "Eliminar cuenta", text = "¿Eliminar la cuenta #$del junto con su historial de pagos?",
            confirmText = "Eliminar", danger = true,
            onConfirm = {
                deleteId = null
                scope.launch {
                    val r = io { CuentasRepo.eliminar(tipo, empresaId, del) }
                    notify(r.message, r.ok)
                    tick++
                }
            },
            onDismiss = { deleteId = null }
        )
    }
    val pd = pagoDe
    if (pd != null) {
        PagoDialog(tipo, empresaId, pd, onClose = { pagoDe = null }, onDone = { pagoDe = null; tick++ })
    }
    val hd = historialDe
    if (hd != null) {
        HistorialDialog(tipo, empresaId, hd, onClose = { historialDe = null })
    }
}

@Composable
private fun CuentaCard(tipo: CuentaTipo, c: Rec, color: Color, onPago: () -> Unit, onHistorial: () -> Unit, onDelete: () -> Unit) {
    val estado = c.str("estado")
    val vencida = estado != "Pagada" && c.str("fecha_vencimiento").isNotBlank() && c.str("fecha_vencimiento") < Fmt.today()
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(if (vencida) CB.Red else color)
            )
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#${c.lng("id")} · ${c.str("tipo_documento")} ${c.str("numero_documento")}", fontSize = 12.sp, color = CB.Gray, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    StatusChip(estado)
                }
                Spacer(Modifier.height(4.dp))
                Text(c.str("razon_social"), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = CB.Primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("NIT/CI: ${c.str("nit_ci")}", fontSize = 12.sp, color = CB.Gray)
                Text(c.str("concepto"), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                LabeledValue("Emisión", Fmt.fecha(c.str("fecha_emision")))
                LabeledValue("Vencimiento", Fmt.fecha(c.str("fecha_vencimiento")) + if (vencida) "  (VENCIDA)" else "", color = if (vencida) CB.Red else Color.Unspecified, bold = vencida)
                LabeledValue("Monto total", Fmt.bs(c.dbl("monto_total")))
                LabeledValue(if (tipo == CuentaTipo.COBRAR) "Cobrado" else "Pagado", Fmt.bs(c.dbl("monto_pagado")), color = CB.Success)
                LabeledValue("Saldo", Fmt.bs(c.dbl("saldo")), bold = true, color = if (c.dbl("saldo") > 0) CB.Danger else CB.Success)
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    if (estado != "Pagada") {
                        FilledTonalButton(onClick = onPago) {
                            Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (tipo == CuentaTipo.COBRAR) "Cobrar" else "Pagar")
                        }
                    }
                    IconButton(onClick = onHistorial) { Icon(Icons.Filled.History, contentDescription = "Historial", tint = CB.Accent) }
                    IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = CB.Danger) }
                }
            }
        }
    }
}

@Composable
private fun CuentaForm(tipo: CuentaTipo, empresaId: Long, onDone: () -> Unit, onCancel: () -> Unit) {
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    var razon by remember { mutableStateOf("") }
    var nit by remember { mutableStateOf("") }
    var tipoDoc by remember { mutableStateOf("Factura") }
    var concepto by remember { mutableStateOf("") }
    var monto by remember { mutableStateOf("") }
    var emision by remember { mutableStateOf(Fmt.today()) }
    var venc by remember { mutableStateOf(Fmt.today()) }
    var obs by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val color = if (tipo == CuentaTipo.COBRAR) CB.Green else CB.Red

    fun save() {
        if (saving) return
        saving = true
        scope.launch {
            val r = io {
                CuentasRepo.agregar(tipo, empresaId, CuentaInput(razon, nit, tipoDoc, concepto, Fmt.dbl(monto), emision, venc, obs))
            }
            saving = false
            notify(r.message, r.ok)
            if (r.ok) onDone()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .background(color)
            .padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White) }
            Text(if (tipo == CuentaTipo.COBRAR) "Nueva cuenta por cobrar" else "Nueva cuenta por pagar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { save() }, enabled = !saving) { Text("GUARDAR", color = Color.White, fontWeight = FontWeight.Bold) }
        }
        Column(modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppField(razon, { razon = it }, tipo.party + " / Razón social *")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppField(nit, { nit = it }, "NIT/CI", Modifier.weight(1f))
                DropdownField("Documento", listOf("Factura", "Recibo", "Otro"), tipoDoc, { tipoDoc = it }, Modifier.weight(1f))
            }
            AppField(concepto, { concepto = it }, "Concepto *", singleLine = false)
            NumField(monto, { monto = it }, "Monto total (Bs.) *")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField("Emisión *", emision, { emision = it }, Modifier.weight(1f))
                DateField("Vencimiento *", venc, { venc = it }, Modifier.weight(1f))
            }
            AppField(obs, { obs = it }, "Observaciones", singleLine = false)
            Button(onClick = { save() }, enabled = !saving, modifier = Modifier
                .fillMaxWidth()
                .height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = color)) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Guardar cuenta", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PagoDialog(tipo: CuentaTipo, empresaId: Long, c: Rec, onClose: () -> Unit, onDone: () -> Unit) {
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    var monto by remember { mutableStateOf(numStr(maxOf(c.dbl("saldo"), 0.0))) }
    var fecha by remember { mutableStateOf(Fmt.today()) }
    var tipoPago by remember { mutableStateOf("Efectivo") }
    var cheque by remember { mutableStateOf("") }
    var banco by remember { mutableStateOf("") }
    var obs by remember { mutableStateOf("") }
    val cobrar = tipo == CuentaTipo.COBRAR
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (cobrar) "Registrar cobro" else "Registrar pago", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${c.str("razon_social")} · Saldo: ${Fmt.bs(c.dbl("saldo"))}", fontSize = 13.sp, color = CB.Gray)
                NumField(monto, { monto = it }, "Monto (Bs.)")
                DateField("Fecha", fecha, { fecha = it })
                DropdownField("Tipo de pago", listOf("Efectivo", "Transferencia", "Cheque", "QR"), tipoPago, { tipoPago = it })
                if (tipoPago == "Cheque") AppField(cheque, { cheque = it }, "Nº de cheque")
                if (tipoPago == "Cheque" || tipoPago == "Transferencia") AppField(banco, { banco = it }, "Banco")
                AppField(obs, { obs = it }, "Observaciones", singleLine = false)
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    val r = io { CuentasRepo.registrarPago(tipo, empresaId, c.lng("id"), Fmt.dbl(monto), fecha, tipoPago, cheque, banco, obs) }
                    notify(r.message, r.ok)
                    if (r.ok) onDone()
                }
            }) { Text("Registrar") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } }
    )
}

@Composable
private fun HistorialDialog(tipo: CuentaTipo, empresaId: Long, c: Rec, onClose: () -> Unit) {
    var pagos by remember { mutableStateOf<List<Rec>?>(null) }
    LaunchedEffect(c) { pagos = io { CuentasRepo.historial(tipo, empresaId, c.lng("id")) } }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Historial de pagos", fontWeight = FontWeight.Bold) },
        text = {
            val list = pagos
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("${c.str("razon_social")} — Total ${Fmt.bs(c.dbl("monto_total"))}", fontSize = 13.sp, color = CB.Gray)
                Spacer(Modifier.height(8.dp))
                if (list == null) CircularProgressIndicator()
                else if (list.isEmpty()) Text("Aún no hay pagos registrados", fontSize = 13.sp)
                else {
                    for (p in list) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row {
                                Text(Fmt.fecha(p.str("fecha_pago")), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(Fmt.bs(p.dbl("monto")), fontWeight = FontWeight.Bold, color = CB.Success)
                            }
                            Text(buildString {
                                append(p.str("tipo_pago"))
                                if (p.str("numero_cheque").isNotBlank()) append(" · Cheque ").append(p.str("numero_cheque"))
                                if (p.str("banco").isNotBlank()) append(" · ").append(p.str("banco"))
                            }, fontSize = 12.sp, color = CB.Gray)
                            if (p.str("observaciones").isNotBlank()) Text(p.str("observaciones"), fontSize = 12.sp)
                        }
                        HorizontalDivider(color = CB.Line)
                    }
                    Spacer(Modifier.height(6.dp))
                    LabeledValue("Total pagado", Fmt.bs(list.sumOf { it.dbl("monto") }), bold = true)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Cerrar") } }
    )
}
