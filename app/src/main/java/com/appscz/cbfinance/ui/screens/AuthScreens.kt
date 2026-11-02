@file:OptIn(ExperimentalMaterial3Api::class)

package com.appscz.cbfinance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appscz.cbfinance.data.AuthRepo
import com.appscz.cbfinance.data.Rec
import com.appscz.cbfinance.data.Session
import com.appscz.cbfinance.data.str
import com.appscz.cbfinance.data.lng
import com.appscz.cbfinance.ui.CB
import com.appscz.cbfinance.ui.LocalNotify
import com.appscz.cbfinance.ui.SectionCard
import com.appscz.cbfinance.util.io
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoggedIn: (Boolean) -> Unit) {
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun doLogin() {
        if (busy) return
        if (user.isBlank() || pass.isEmpty()) {
            notify("Ingresa usuario y contraseña", false)
            return
        }
        busy = true
        scope.launch {
            val res = io { AuthRepo.login(user, pass) }
            busy = false
            val u = res.user
            if (u == null) {
                notify(res.error ?: "Error al iniciar sesión", false)
            } else {
                if (res.empresas.size == 1) {
                    Session.start(u, res.empresas[0].lng("id"))
                    notify("Bienvenido ${u.nombre}!", true)
                    onLoggedIn(false)
                } else {
                    Session.start(u, null)
                    notify("Bienvenido ${u.nombre}! Selecciona una empresa.", true)
                    onLoggedIn(true)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CB.HeaderBrush),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(CB.HeaderBrush),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = Color.White, modifier = Modifier.size(38.dp)) }
                    Spacer(Modifier.height(12.dp))
                    Text("C&B CONSULTORES", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = CB.Title)
                    Text("Gestión Financiera Multi-Empresa", fontSize = 13.sp, color = CB.Gray)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = user, onValueChange = { user = it },
                        label = { Text("Usuario") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pass, onValueChange = { pass = it },
                        label = { Text("Contraseña") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { show = !show }) {
                                Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null)
                            }
                        },
                        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { doLogin() },
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CB.LoginBlue)
                    ) {
                        if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("Iniciar Sesión", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(16.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        for (t in listOf("Control de Ventas", "Gestión de Compras", "Seguimiento de Gastos", "Cuentas por Cobrar/Pagar", "Reportes Financieros")) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = CB.Success, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(t, fontSize = 12.sp, color = CB.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SelectEmpresaScreen(onSelected: () -> Unit, onLogout: () -> Unit) {
    val notify = LocalNotify.current
    var empresas by remember { mutableStateOf<List<Rec>>(emptyList()) }
    LaunchedEffect(Unit) {
        val u = Session.user
        if (u != null) empresas = io { AuthRepo.empresasUsuario(u) }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CB.HeaderBrush),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Selecciona una empresa", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CB.Title)
                Text("Elige con cuál empresa deseas trabajar", fontSize = 13.sp, color = CB.Gray)
                Spacer(Modifier.height(12.dp))
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 420.dp)) {
                    for (e in empresas) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clickable {
                                    Session.setEmpresa(e.lng("id"))
                                    notify("Empresa: ${e.str("nombre")}", true)
                                    onSelected()
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            border = androidx.compose.foundation.BorderStroke(2.dp, CB.Line)
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Business, contentDescription = null, tint = CB.LoginBlue, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(e.str("nombre"), fontWeight = FontWeight.SemiBold, color = CB.Title)
                                    Text("NIT: ${e.str("nit")}", fontSize = 12.sp, color = CB.Gray)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Cerrar sesión") }
            }
        }
    }
}

@Composable
fun PerfilScreen(onEmpresaChanged: () -> Unit) {
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    val u = Session.user ?: return
    var empresas by remember { mutableStateOf<List<Rec>>(emptyList()) }
    var actual by remember { mutableStateOf("") }
    var nueva by remember { mutableStateOf("") }
    var conf by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { empresas = io { AuthRepo.empresasUsuario(u) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionCard("Información Personal") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(CB.HeaderBrush),
                    contentAlignment = Alignment.Center
                ) { Text(u.nombre.take(1).uppercase(), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(u.nombre, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = CB.Title)
                    Text(u.rolLabel, fontSize = 12.sp, color = CB.Accent, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(10.dp))
            com.appscz.cbfinance.ui.LabeledValue("Usuario", u.username)
            com.appscz.cbfinance.ui.LabeledValue("Email", u.email)
        }
        SectionCard("Cambiar Contraseña") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PassField("Contraseña actual", actual) { actual = it }
                PassField("Nueva contraseña", nueva) { nueva = it }
                PassField("Confirmar nueva contraseña", conf) { conf = it }
                Button(
                    onClick = {
                        scope.launch {
                            val r = io { AuthRepo.cambiarPassword(u.id, actual, nueva, conf) }
                            notify(r.message, r.ok)
                            if (r.ok) {
                                actual = ""; nueva = ""; conf = ""
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Actualizar Contraseña") }
            }
        }
        SectionCard("Mis Empresas") {
            for (e in empresas) {
                val sel = e.lng("id") == Session.empresaId
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(e.str("nombre"), fontWeight = FontWeight.SemiBold)
                        Text("NIT: ${e.str("nit")}", fontSize = 12.sp, color = CB.Gray)
                    }
                    if (sel) {
                        Text("Activa", color = CB.Success, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    } else {
                        Button(onClick = {
                            Session.setEmpresa(e.lng("id"))
                            notify("Empresa cambiada a: ${e.str("nombre")}", true)
                            onEmpresaChanged()
                        }) { Text("Seleccionar") }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PassField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
    )
}
