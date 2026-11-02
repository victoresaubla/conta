@file:OptIn(ExperimentalMaterial3Api::class)

package com.appscz.cbfinance.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appscz.cbfinance.data.*
import com.appscz.cbfinance.ui.*
import com.appscz.cbfinance.util.DownloadsSaver
import com.appscz.cbfinance.util.Files
import com.appscz.cbfinance.util.Fmt
import com.appscz.cbfinance.util.PdfReports
import com.appscz.cbfinance.util.io
import kotlinx.coroutines.launch

class KpiItem(val title: String, val value: String, val sub: String, val color: Color, val icon: ImageVector)

@Composable
fun KpiGrid(items: List<KpiItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        var i = 0
        while (i < items.size) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                val a = items[i]
                KpiCard(a.title, a.value, a.sub, a.color, a.icon, Modifier.weight(1f))
                if (i + 1 < items.size) {
                    val b = items[i + 1]
                    KpiCard(b.title, b.value, b.sub, b.color, b.icon, Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
            i += 2
        }
    }
}

private fun kpisFrom(k: Metricas): List<KpiItem> = listOf(
    KpiItem("Ingresos Totales", Fmt.bs(k.totalVentas), "${k.numVentas} ventas", CB.Green, Icons.Filled.TrendingUp),
    KpiItem("Compras", Fmt.bs(k.totalCompras), "${k.numCompras} compras", CB.Amber, Icons.Filled.ShoppingCart),
    KpiItem("Gastos Operativos", Fmt.bs(k.totalGastos), "${k.numGastos} gastos", CB.Red, Icons.Filled.Payments),
    KpiItem("Régimen", Fmt.bs(k.totalRegimen), "${k.numRegimen} registros", CB.Teal, Icons.Filled.AccountBalance),
    KpiItem("Utilidad Neta", Fmt.bs(k.utilidadNeta), "Margen " + Fmt.pct(k.margen), CB.Blue, Icons.Filled.Savings),
    KpiItem("Utilidad Bruta", Fmt.bs(k.utilidadBruta), "Ingresos - Compras", CB.Green, Icons.Filled.Paid),
    KpiItem("Por Cobrar", Fmt.bs(k.totalPorCobrar), "${k.numPorCobrar} cuentas", CB.Blue, Icons.Filled.RequestQuote),
    KpiItem("Por Pagar", Fmt.bs(k.totalPorPagar), "${k.numPorPagar} cuentas", CB.Red, Icons.Filled.ReceiptLong)
)

@Composable
private fun DateFilterBar(fi: String, ff: String, onFi: (String) -> Unit, onFf: (String) -> Unit, onApply: () -> Unit, onClear: () -> Unit, onPdf: () -> Unit, busy: Boolean) {
    SectionCard("Filtrar por fechas", headerColor = CB.Secondary) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField("Desde", fi, onFi, Modifier.weight(1f))
                DateField("Hasta", ff, onFf, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onApply, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Filtrar", maxLines = 1) }
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Limpiar", maxLines = 1) }
                FilledTonalButton(onClick = onPdf, enabled = !busy, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp)); Text("PDF", maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(empresaId: Long, empresaNombre: String) {
    val ctx = LocalContext.current
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    var fiIn by remember { mutableStateOf("") }
    var ffIn by remember { mutableStateOf("") }
    var fi by remember { mutableStateOf("") }
    var ff by remember { mutableStateOf("") }
    var data by remember { mutableStateOf<Dashboard?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(fi, ff) {
        data = io { ReportesRepo.dashboard(empresaId, Filtro(fi, ff)) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DateFilterBar(
            fiIn, ffIn, { fiIn = it }, { ffIn = it },
            onApply = {
                if (fiIn.isBlank() != ffIn.isBlank()) notify("Selecciona fecha inicial y final", false)
                else {
                    fi = fiIn; ff = ffIn
                }
            },
            onClear = { fiIn = ""; ffIn = ""; fi = ""; ff = "" },
            onPdf = {
                val d = data
                if (d != null) {
                    busy = true
                    scope.launch {
                        try {
                            val f = io { PdfReports.dashboard(ctx, empresaNombre, d, Filtro(fi, ff)) }
                            val ruta = io { DownloadsSaver.deliver(ctx, f, "application/pdf") }
                            notify("PDF guardado en $ruta", true)
                        } catch (e: Exception) {
                            notify("No se pudo generar el PDF: ${e.message}", false)
                        }
                        busy = false
                    }
                }
            },
            busy = busy
        )
        val d = data
        if (d == null) {
            Box(modifier = Modifier
                .fillMaxWidth()
                .padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            if (fi.isNotBlank()) {
                Text("Mostrando: ${Fmt.fecha(fi)} al ${Fmt.fecha(ff)}", color = CB.Gray, fontSize = 12.sp)
            }
            KpiGrid(kpisFrom(d.metricas))

            SectionCard("Tendencia Mensual", headerColor = CB.Blue) { TrendChart(d.serie) }

            SectionCard("Ventas Recientes", headerColor = CB.Green) {
                if (d.ventasRecientes.isEmpty()) EmptyState("Sin ventas registradas")
                for (r in d.ventasRecientes) RecentRow(r)
            }
            SectionCard("Compras Recientes", headerColor = CB.Amber) {
                if (d.comprasRecientes.isEmpty()) EmptyState("Sin compras registradas")
                for (r in d.comprasRecientes) RecentRow(r)
            }
            if (d.planillas.isNotEmpty()) {
                SectionCard("Resumen de Planillas", headerColor = CB.Blue) {
                    for (p in d.planillas) PlanillaResumenRow(p)
                }
            }
            if (d.servicios.isNotEmpty()) {
                SectionCard("Servicios Básicos por Mes", headerColor = CB.Teal) { MesesDetalle(d.servicios) }
            }
            if (d.alquileres.isNotEmpty()) {
                SectionCard("Alquileres por Mes", headerColor = CB.Red) { MesesDetalle(d.alquileres) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RecentRow(r: Rec) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(r.str("producto"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
            Text("${Fmt.fecha(r.str("fecha"))} · ${r.str("tipo_documento")} · Cant. ${Fmt.qty(r.dbl("cantidad"))}", fontSize = 11.sp, color = CB.Gray)
        }
        Text(Fmt.bs(r.dbl("total")), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = CB.Title)
    }
    HorizontalDivider(color = CB.Line)
}

@Composable
fun PlanillaResumenRow(p: ResumenPlanilla) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp)) {
        Row {
            Text(p.mes, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${p.numEmpleados} empleado(s)", fontSize = 12.sp, color = CB.Gray)
        }
        LabeledValue("Total neto", Fmt.bs(p.totalNeto), bold = true)
        LabeledValue("AFP", Fmt.bs(p.totalAfp))
        LabeledValue("Otros descuentos", Fmt.bs(p.otrosDescuentos))
        LabeledValue("Horas extras", Fmt.bs(p.horasExtras))
    }
    HorizontalDivider(color = CB.Line)
}

@Composable
fun MesesDetalle(m: Map<String, List<Rec>>) {
    for ((per, items) in m) {
        Text(Fmt.mesLabel(per), fontWeight = FontWeight.Bold, color = CB.Title, modifier = Modifier.padding(top = 6.dp))
        for (r in items) {
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(r.str("descripcion"), fontSize = 13.sp, maxLines = 1)
                    Text(r.str("categoria"), fontSize = 11.sp, color = CB.Gray)
                }
                Text(Fmt.bs(r.dbl("total")), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        HorizontalDivider(color = CB.Line)
    }
}

// =================================================================== REPORTES
@Composable
fun ReportesScreen(empresaId: Long, empresaNombre: String) {
    val ctx = LocalContext.current
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    var fiIn by remember { mutableStateOf("") }
    var ffIn by remember { mutableStateOf("") }
    var razonIn by remember { mutableStateOf("") }
    var tipoIn by remember { mutableStateOf("") }
    var filtro by remember { mutableStateOf(Filtro()) }
    var razones by remember { mutableStateOf<List<String>>(emptyList()) }
    var tipos by remember { mutableStateOf<List<String>>(emptyList()) }
    var rep by remember { mutableStateOf<ReporteCompleto?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        razones = io { ReportesRepo.razonesSocialesTodas(empresaId) }
        tipos = io { ReportesRepo.tiposDocumentoTodos(empresaId) }
    }
    LaunchedEffect(filtro) {
        rep = null
        rep = io { ReportesRepo.reporte(empresaId, filtro) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard("Filtros de reporte", headerColor = CB.Secondary) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField("Desde", fiIn, { fiIn = it }, Modifier.weight(1f))
                    DateField("Hasta", ffIn, { ffIn = it }, Modifier.weight(1f))
                }
                DropdownField("Razón social", razones, razonIn, { razonIn = it }, allowEmptyLabel = "— Todas —")
                DropdownField("Tipo de documento", tipos, tipoIn, { tipoIn = it }, allowEmptyLabel = "— Todos —")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = {
                        if (fiIn.isBlank() != ffIn.isBlank()) notify("Selecciona fecha inicial y final", false)
                        else filtro = Filtro(fiIn, ffIn, razonIn, tipoIn)
                    }, modifier = Modifier.weight(1f)) { Text("Aplicar") }
                    OutlinedButton(onClick = {
                        fiIn = ""; ffIn = ""; razonIn = ""; tipoIn = ""; filtro = Filtro()
                    }, modifier = Modifier.weight(1f)) { Text("Limpiar") }
                    FilledTonalButton(onClick = {
                        val r = rep
                        if (r != null) {
                            busy = true
                            scope.launch {
                                try {
                                    val f = io { PdfReports.reporte(ctx, empresaNombre, r, filtro) }
                                    val ruta = io { DownloadsSaver.deliver(ctx, f, "application/pdf") }
                                    notify("PDF guardado en $ruta", true)
                                } catch (e: Exception) {
                                    notify("No se pudo generar el PDF: ${e.message}", false)
                                }
                                busy = false
                            }
                        }
                    }, enabled = !busy && rep != null, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp)); Text("PDF")
                    }
                }
            }
        }
        val r = rep
        if (r == null) {
            Box(modifier = Modifier
                .fillMaxWidth()
                .padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val k = r.metricas
            KpiGrid(kpisFrom(k))

            SectionCard("Estado de Resultados", headerColor = CB.Primary) {
                LabeledValue("Ingresos (ventas)", Fmt.bs(k.totalVentas), bold = true, color = CB.Green)
                LabeledValue("(-) Egresos (compras)", Fmt.bs(k.totalCompras))
                HorizontalDivider(color = CB.Line, modifier = Modifier.padding(vertical = 4.dp))
                LabeledValue("Utilidad bruta", Fmt.bs(k.utilidadBruta), bold = true)
                LabeledValue("(-) Gastos", Fmt.bs(k.totalGastos))
                LabeledValue("(-) Régimen", Fmt.bs(k.totalRegimen))
                HorizontalDivider(color = CB.Line, modifier = Modifier.padding(vertical = 4.dp))
                LabeledValue("Utilidad neta", Fmt.bs(k.utilidadNeta), bold = true, color = if (k.utilidadNeta >= 0) CB.Green else CB.Red)
                LabeledValue("Margen de utilidad", Fmt.pct(k.margen))
                LabeledValue("Costo total", Fmt.bs(k.costoTotal))
            }

            SectionCard("Análisis de Tendencias Financieras", headerColor = CB.Blue) { TrendChart(r.serie) }

            SectionCard("Flujo por Tipo de Documento", headerColor = CB.Secondary) {
                TipoDocBlock("Ingresos", r.tipoDoc.ventas, CB.Green)
                TipoDocBlock("Egresos", r.tipoDoc.compras, CB.Amber)
                TipoDocBlock("Gastos", r.tipoDoc.gastos, CB.Red)
                TipoDocBlock("Régimen", r.tipoDoc.regimen, CB.Teal)
            }

            CatBlock("Ventas por Categoría", r.ventasCat, CB.Green)
            CatBlock("Compras por Categoría", r.comprasCat, CB.Amber)
            CatBlock("Gastos por Categoría", r.gastosCat, CB.Red)
            CatBlock("Régimen por Categoría", r.regimenCat, CB.Teal)

            SectionCard("Cuentas por Cobrar Pendientes", headerColor = CB.Green) {
                if (r.cobrar.isEmpty()) EmptyState("Sin cuentas pendientes")
                for (c in r.cobrar) CuentaMini(c)
            }
            SectionCard("Cuentas por Pagar Pendientes", headerColor = CB.Red) {
                if (r.pagar.isEmpty()) EmptyState("Sin cuentas pendientes")
                for (c in r.pagar) CuentaMini(c)
            }
            if (r.resumenPlanillas.isNotEmpty()) {
                SectionCard("Resumen de Planillas por Mes", headerColor = CB.Blue) {
                    for (p in r.resumenPlanillas) PlanillaResumenRow(p)
                }
            }
            if (r.detallePlanillas.isNotEmpty()) {
                SectionCard("Detalle de Planillas", headerColor = CB.Secondary) {
                    for (p in r.detallePlanillas) {
                        Column(modifier = Modifier.padding(vertical = 5.dp)) {
                            Row {
                                Text(p.str("empleado"), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(p.str("mes"), fontSize = 12.sp, color = CB.Gray)
                            }
                            LabeledValue("Bruto", Fmt.bs(p.dbl("monto_bruto")))
                            LabeledValue("AFP (${Fmt.money(p.dbl("descuento_afp_porcentaje"))}%)", "- " + Fmt.bs(p.dbl("descuento_afp_monto")))
                            LabeledValue("Caja Nacional", "- " + Fmt.bs(p.dbl("descuento_caja_nacional")))
                            LabeledValue("Min. Trabajo", "- " + Fmt.bs(p.dbl("descuento_ministerio_trabajo")))
                            LabeledValue("Horas extras", "+ " + Fmt.bs(p.dbl("horas_extras")))
                            LabeledValue("Total neto", Fmt.bs(p.dbl("total_neto")), bold = true)
                        }
                        HorizontalDivider(color = CB.Line)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TipoDocBlock(titulo: String, data: List<TipoDocStat>, color: Color) {
    if (data.isEmpty()) return
    Text(titulo, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
    for (d in data) LabeledValue("${d.tipo} (${d.cantidad})", Fmt.bs(d.total))
    LabeledValue("Total", Fmt.bs(data.sumOf { it.total }), bold = true)
    HorizontalDivider(color = CB.Line, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun CatBlock(titulo: String, data: List<CatTotal>, color: Color) {
    SectionCard(titulo, headerColor = color) {
        if (data.isEmpty()) {
            EmptyState("Sin datos")
            return@SectionCard
        }
        CategoryPie(data)
        Spacer(Modifier.height(8.dp))
        for (c in data) LabeledValue(c.categoria + if (c.cantidad > 0) " (${c.cantidad})" else "", Fmt.bs(c.total))
        HorizontalDivider(color = CB.Line, modifier = Modifier.padding(vertical = 4.dp))
        LabeledValue("TOTAL", Fmt.bs(data.sumOf { it.total }), bold = true)
    }
}

@Composable
private fun CuentaMini(c: Rec) {
    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.str("razon_social"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
            StatusChip(c.str("estado"))
        }
        Text("${Fmt.fecha(c.str("fecha"))} · ${c.str("tipo_documento")} ${c.str("numero_documento")}", fontSize = 11.sp, color = CB.Gray)
        LabeledValue("Monto", Fmt.bs(c.dbl("monto_total")))
        LabeledValue("Saldo", Fmt.bs(c.dbl("saldo")), bold = true)
    }
    HorizontalDivider(color = CB.Line)
}
