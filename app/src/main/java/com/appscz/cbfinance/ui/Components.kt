@file:OptIn(ExperimentalMaterial3Api::class)

package com.appscz.cbfinance.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appscz.cbfinance.data.CatTotal
import com.appscz.cbfinance.data.Serie
import com.appscz.cbfinance.util.Fmt
import java.time.LocalDate

/** Muestra mensajes (snackbar) desde cualquier pantalla. */
val LocalNotify = compositionLocalOf<(String, Boolean) -> Unit> { { _, _ -> } }

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    headerColor: Color = CB.Primary,
    actions: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (headerColor == CB.Primary) CB.HeaderBrush else androidx.compose.ui.graphics.Brush.linearGradient(listOf(headerColor, headerColor)))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            actions()
        }
        Column(modifier = Modifier.padding(14.dp)) { content() }
    }
}

@Composable
fun KpiCard(title: String, value: String, sub: String, color: Color, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(color)
            )
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.width(8.dp))
                    Text(title, color = CB.Gray, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                }
                Spacer(Modifier.height(8.dp))
                Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub.isNotEmpty()) Text(sub, color = Color(0xFF8A8886), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun StatusChip(text: String) {
    val t = text.ifBlank { "-" }
    val (bg, fg) = when {
        t == "Pagada" || t == "Cobrada" -> Pair(Color(0xFFD5F5E3), Color(0xFF1E8449))
        t.contains("Parcial") -> Pair(Color(0xFFD6EAF8), Color(0xFF1B4F72))
        t == "Por Pagar" || t == "Por Cobrar" || t == "Pendiente" -> Pair(Color(0xFFFDEBD0), Color(0xFF9C640C))
        else -> Pair(Color(0xFFEAECEE), Color(0xFF566573))
    }
    Text(
        t,
        color = fg,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

@Composable
fun AppField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    placeholder: String = "",
    trailing: @Composable (() -> Unit)? = null
) {
    val ph: (@Composable () -> Unit)? = if (placeholder.isNotEmpty()) {
        { Text(placeholder) }
    } else null
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        readOnly = readOnly,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        placeholder = ph,
        trailingIcon = trailing
    )
}

@Composable
fun NumField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    AppField(
        value = value,
        onChange = { v -> if (v.isEmpty() || v.matches(Regex("^-?\\d*[.,]?\\d*$"))) onChange(v) },
        label = label,
        modifier = modifier,
        keyboard = KeyboardType.Decimal
    )
}

@Composable
fun DropdownField(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, allowEmptyLabel: String? = null) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) }
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { open = true }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (allowEmptyLabel != null) {
                DropdownMenuItem(text = { Text(allowEmptyLabel) }, onClick = { onSelect(""); open = false })
            }
            for (o in options) {
                DropdownMenuItem(text = { Text(o) }, onClick = { onSelect(o); open = false })
            }
        }
    }
}

@Composable
fun DateField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, monthOnly: Boolean = false) {
    val ctx = LocalContext.current
    fun openPicker() {
        val base = try {
            if (value.length >= 10) LocalDate.parse(value.take(10))
            else if (value.length == 7) LocalDate.parse("$value-01")
            else LocalDate.now()
        } catch (e: Exception) {
            LocalDate.now()
        }
        val dlg = DatePickerDialog(
            ctx,
            { _, y, m, d ->
                val mm = String.format("%02d", m + 1)
                val dd = String.format("%02d", d)
                onChange(if (monthOnly) "$y-$mm" else "$y-$mm-$dd")
            },
            base.year, base.monthValue - 1, base.dayOfMonth
        )
        dlg.show()
    }
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = if (monthOnly) value else Fmt.fecha(value),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) }
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { openPicker() }
        )
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmText: String = "Confirmar", danger: Boolean = false, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = if (danger) CB.Danger else CB.Accent)
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun EmptyState(text: String) {
    Box(modifier = Modifier
        .fillMaxWidth()
        .padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = CB.Gray, fontSize = 14.sp)
    }
}

@Composable
fun LabeledValue(label: String, value: String, bold: Boolean = false, color: Color = Color.Unspecified) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 2.dp)) {
        Text(label, color = CB.Gray, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = color)
    }
}

// ---------------------------------------------------------------- GRÁFICOS
@Composable
fun TrendChart(serie: Serie, modifier: Modifier = Modifier) {
    if (serie.periodos.isEmpty()) {
        EmptyState("Sin datos para graficar")
        return
    }
    val n = serie.periodos.size
    val maxV = maxOf(
        1.0,
        serie.ventas.maxOrNull() ?: 0.0, serie.compras.maxOrNull() ?: 0.0,
        serie.gastos.maxOrNull() ?: 0.0, serie.regimen.maxOrNull() ?: 0.0
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)) {
            val w = size.width
            val h = size.height
            val groupW = w / n
            val barW = (groupW - 8f) / 4f
            drawLine(CB.Line, Offset(0f, h - 2f), Offset(w, h - 2f), strokeWidth = 2f)
            val cols = listOf(CB.Green, CB.Amber, CB.Red, CB.Teal)
            val data = listOf(serie.ventas, serie.compras, serie.gastos, serie.regimen)
            for (g in 0 until n) {
                for (k in 0 until 4) {
                    val v = data[k][g]
                    val bh = ((v / maxV) * (h - 8f)).toFloat()
                    val x0 = g * groupW + 4f + k * barW
                    drawRect(cols[k], topLeft = Offset(x0, h - 2f - bh), size = Size(maxOf(barW - 1f, 1f), bh))
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val step = if (n > 6) 2 else 1
            for (i in 0 until n) {
                if (i % step == 0 || n <= 6) Text(Fmt.mesLabel(serie.periodos[i]), fontSize = 9.sp, color = CB.Gray)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Legend("Ingresos", CB.Green)
            Legend("Egresos", CB.Amber)
            Legend("Gastos", CB.Red)
            Legend("Régimen", CB.Teal)
        }
    }
}

@Composable
fun Legend(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier
            .size(10.dp)
            .background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 10.sp, color = CB.Gray)
    }
}

val PIE_COLORS = listOf(
    Color(0xFF3498DB), Color(0xFF27AE60), Color(0xFFF39C12), Color(0xFFE74C3C), Color(0xFF9B59B6),
    Color(0xFF1ABC9C), Color(0xFFE67E22), Color(0xFF34495E), Color(0xFF16A085), Color(0xFFC0392B)
)

@Composable
fun CategoryPie(data: List<CatTotal>, modifier: Modifier = Modifier) {
    val total = data.sumOf { it.total }
    if (data.isEmpty() || total <= 0.0) {
        EmptyState("Sin datos")
        return
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(modifier = Modifier.size(120.dp)) {
                var start = -90f
                val stroke = 28f
                for ((i, c) in data.withIndex()) {
                    val sweep = (c.total / total * 360.0).toFloat()
                    drawArc(
                        color = PIE_COLORS[i % PIE_COLORS.size],
                        startAngle = start, sweepAngle = sweep, useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke)
                    )
                    start += sweep
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                for ((i, c) in data.take(6).withIndex()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier
                            .size(9.dp)
                            .background(PIE_COLORS[i % PIE_COLORS.size], CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(c.categoria, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(Fmt.pct(c.total / total * 100.0), fontSize = 11.sp, color = CB.Gray)
                    }
                }
            }
        }
    }
}
