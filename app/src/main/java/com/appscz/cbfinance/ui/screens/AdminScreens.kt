@file:OptIn(ExperimentalMaterial3Api::class)

package com.appscz.cbfinance.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appscz.cbfinance.data.*
import com.appscz.cbfinance.ui.*
import com.appscz.cbfinance.util.Backup
import com.appscz.cbfinance.util.Files
import com.appscz.cbfinance.util.io
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =================================================================== USUARIOS
@Composable
fun UsuariosScreen() {
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    val me = Session.user
    var users by remember { mutableStateOf<List<Rec>>(emptyList()) }
    var empresas by remember { mutableStateOf<List<Rec>>(emptyList()) }
    var tick by remember { mutableStateOf(0) }
    var dialogFor by remember { mutableStateOf<Rec?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var passFor by remember { mutableStateOf<Rec?>(null) }
    var deleteFor by remember { mutableStateOf<Rec?>(null) }

    LaunchedEffect(tick) {
        users = io { UsuarioRepo.listar() }
        empresas = io { UsuarioRepo.empresasActivas() }
    }
    if (me == null || !me.isAdmin) {
        EmptyState("Solo el administrador puede gestionar usuarios")
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(users, key = { it.lng("id") }) { u ->
                val activo = u.lng("activo") == 1L
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(u.str("nombre_completo"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = CB.Primary)
                                Text("@${u.str("username")} · ${u.str("email")}", fontSize = 12.sp, color = CB.Gray)
                            }
                            Text(
                                if (u.str("rol") == "admin") "Admin" else "Contador",
                                color = if (u.str("rol") == "admin") CB.Danger else CB.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background((if (u.str("rol") == "admin") CB.Danger else CB.Accent).copy(alpha = 0.12f), RoundedCornerShape(50))
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Empresas: " + u.str("empresas").ifBlank { "Sin empresas" }, fontSize = 12.sp)
                        Text(if (activo) "Activo" else "Inactivo", fontSize = 12.sp, color = if (activo) CB.Success else CB.Gray, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            IconButton(onClick = { dialogFor = u }) { Icon(Icons.Filled.Edit, contentDescription = "Editar", tint = CB.Warning) }
                            IconButton(onClick = { passFor = u }) { Icon(Icons.Filled.Key, contentDescription = "Contraseña", tint = CB.Accent) }
                            if (u.lng("id") != me.id && u.lng("id") != 1L) {
                                IconButton(onClick = { deleteFor = u }) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = CB.Danger) }
                            }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { showNew = true },
            icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
            text = { Text("Nuevo usuario") },
            containerColor = CB.Accent, contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }

    if (showNew) {
        UsuarioDialog(null, empresas, onClose = { showNew = false }, onSaved = { showNew = false; tick++ })
    }
    val df = dialogFor
    if (df != null) {
        UsuarioDialog(df, empresas, onClose = { dialogFor = null }, onSaved = { dialogFor = null; tick++ })
    }
    val pf = passFor
    if (pf != null) {
        var np by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { passFor = null },
            title = { Text("Cambiar contraseña") },
            text = {
                Column {
                    Text("Usuario: ${pf.str("username")}", fontSize = 13.sp, color = CB.Gray)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(np, { np = it }, label = { Text("Nueva contraseña") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val r = io { UsuarioRepo.cambiarPassword(pf.lng("id"), np) }
                        notify(r.message, r.ok)
                        if (r.ok) passFor = null
                    }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { passFor = null }) { Text("Cancelar") } }
        )
    }
    val dl = deleteFor
    if (dl != null) {
        ConfirmDialog(
            title = "Eliminar usuario", text = "¿Eliminar al usuario ${dl.str("username")}? Esta acción no se puede deshacer.",
            confirmText = "Eliminar", danger = true,
            onConfirm = {
                deleteFor = null
                scope.launch {
                    val r = io { UsuarioRepo.eliminar(dl.lng("id"), me.id) }
                    notify(r.message, r.ok)
                    tick++
                }
            },
            onDismiss = { deleteFor = null }
        )
    }
}

@Composable
private fun UsuarioDialog(u: Rec?, empresas: List<Rec>, onClose: () -> Unit, onSaved: () -> Unit) {
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    val edit = u != null
    var username by remember { mutableStateOf(u?.str("username") ?: "") }
    var nombre by remember { mutableStateOf(u?.str("nombre_completo") ?: "") }
    var email by remember { mutableStateOf(u?.str("email") ?: "") }
    var pass by remember { mutableStateOf("") }
    var rol by remember { mutableStateOf(u?.str("rol") ?: "contador") }
    var activo by remember { mutableStateOf(u == null || u.lng("activo") == 1L) }
    val ids = remember {
        val s = mutableStateListOf<Long>()
        val txt = u?.str("empresas_ids") ?: ""
        for (p in txt.split(",")) {
            val v = p.trim().toLongOrNull()
            if (v != null) s.add(v)
        }
        s
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (edit) "Editar usuario" else "Nuevo usuario", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppField(username, { username = it }, "Usuario *", readOnly = edit)
                AppField(nombre, { nombre = it }, "Nombre completo *")
                AppField(email, { email = it }, "Email *")
                if (!edit) AppField(pass, { pass = it }, "Contraseña *")
                DropdownField("Rol", listOf("admin", "contador"), rol, { rol = it })
                if (edit) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Usuario activo", modifier = Modifier.weight(1f))
                        Switch(checked = activo, onCheckedChange = { activo = it })
                    }
                }
                Text("Empresas asignadas", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                for (e in empresas) {
                    val id = e.lng("id")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = ids.contains(id), onCheckedChange = { c -> if (c) { if (!ids.contains(id)) ids.add(id) } else ids.remove(id) })
                        Text(e.str("nombre"), fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    val r = io {
                        if (u != null) UsuarioRepo.editar(u.lng("id"), nombre, email, rol, activo, ids.toList())
                        else UsuarioRepo.agregar(username, nombre, email, pass, rol, ids.toList())
                    }
                    notify(r.message, r.ok)
                    if (r.ok) onSaved()
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } }
    )
}

// =================================================================== EMPRESAS
@Composable
fun EmpresasScreen(onChanged: () -> Unit) {
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    val me = Session.user
    var list by remember { mutableStateOf<List<Rec>>(emptyList()) }
    var tick by remember { mutableStateOf(0) }
    var showNew by remember { mutableStateOf(false) }
    var editFor by remember { mutableStateOf<Rec?>(null) }
    var deactFor by remember { mutableStateOf<Rec?>(null) }

    LaunchedEffect(tick) { list = io { EmpresaRepo.listar() } }
    if (me == null || !me.isAdmin) {
        EmptyState("Solo el administrador puede gestionar empresas")
        return
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(list, key = { it.lng("id") }) { e ->
                val activo = e.lng("activo") == 1L
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Business, contentDescription = null, tint = CB.Accent)
                            Spacer(Modifier.width(8.dp))
                            Text(e.str("nombre"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = CB.Primary, modifier = Modifier.weight(1f))
                            Text(if (activo) "Activa" else "Inactiva", fontSize = 12.sp, color = if (activo) CB.Success else CB.Gray, fontWeight = FontWeight.Bold)
                        }
                        Text("NIT: ${e.str("nit")}", fontSize = 12.sp, color = CB.Gray)
                        if (e.str("direccion").isNotBlank()) Text(e.str("direccion"), fontSize = 12.sp)
                        if (e.str("telefono").isNotBlank()) Text("Tel: ${e.str("telefono")}", fontSize = 12.sp)
                        if (e.str("email").isNotBlank()) Text(e.str("email"), fontSize = 12.sp)
                        Text("${e.lng("num_usuarios")} usuario(s) · Creada por ${e.str("creador")}", fontSize = 11.sp, color = CB.Gray)
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            IconButton(onClick = { editFor = e }) { Icon(Icons.Filled.Edit, contentDescription = "Editar", tint = CB.Warning) }
                            if (e.lng("id") != 1L && activo) {
                                IconButton(onClick = { deactFor = e }) { Icon(Icons.Filled.Delete, contentDescription = "Desactivar", tint = CB.Danger) }
                            }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { showNew = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Nueva empresa") },
            containerColor = CB.Accent, contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
    if (showNew) {
        EmpresaDialog(null, me.id, onClose = { showNew = false }, onSaved = { showNew = false; tick++; onChanged() })
    }
    val ef = editFor
    if (ef != null) {
        EmpresaDialog(ef, me.id, onClose = { editFor = null }, onSaved = { editFor = null; tick++; onChanged() })
    }
    val df = deactFor
    if (df != null) {
        ConfirmDialog(
            title = "Desactivar empresa", text = "¿Desactivar la empresa ${df.str("nombre")}? Sus datos se conservan.",
            confirmText = "Desactivar", danger = true,
            onConfirm = {
                deactFor = null
                scope.launch {
                    val r = io { EmpresaRepo.desactivar(df.lng("id")) }
                    notify(r.message, r.ok)
                    tick++
                    onChanged()
                }
            },
            onDismiss = { deactFor = null }
        )
    }
}

@Composable
private fun EmpresaDialog(e: Rec?, creadorId: Long, onClose: () -> Unit, onSaved: () -> Unit) {
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    var nombre by remember { mutableStateOf(e?.str("nombre") ?: "") }
    var nit by remember { mutableStateOf(e?.str("nit") ?: "") }
    var dir by remember { mutableStateOf(e?.str("direccion") ?: "") }
    var tel by remember { mutableStateOf(e?.str("telefono") ?: "") }
    var mail by remember { mutableStateOf(e?.str("email") ?: "") }
    var activo by remember { mutableStateOf(e == null || e.lng("activo") == 1L) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (e == null) "Nueva empresa" else "Editar empresa", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppField(nombre, { nombre = it }, "Nombre *")
                AppField(nit, { nit = it }, "NIT *")
                AppField(dir, { dir = it }, "Dirección")
                AppField(tel, { tel = it }, "Teléfono")
                AppField(mail, { mail = it }, "Email")
                if (e != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Empresa activa", modifier = Modifier.weight(1f))
                        Switch(checked = activo, onCheckedChange = { activo = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    val r = io {
                        if (e != null) EmpresaRepo.editar(e.lng("id"), nombre, nit, dir, tel, mail, activo)
                        else EmpresaRepo.agregar(creadorId, nombre, nit, dir, tel, mail)
                    }
                    notify(r.message, r.ok)
                    if (r.ok) onSaved()
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } }
    )
}

// =================================================================== RESPALDO / RESTAURACIÓN
@Composable
fun BackupScreen(onRestored: () -> Unit) {
    val ctx = LocalContext.current
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    val isAdmin = Session.user?.isAdmin == true
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var autos by remember { mutableStateOf<List<File>>(emptyList()) }
    var tick by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(tick) { autos = io { Backup.listAuto(ctx) } }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Backup.MIME_DB)) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                busy = true
                val r = io { Backup.saveTo(ctx, uri) }
                busy = false
                notify(r.message, r.ok)
            }
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) pendingUri = uri
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard("Base de datos local", headerColor = CB.Primary) {
            Text("Todos tus datos se guardan únicamente en este teléfono (archivo finanzas.db), sin necesidad de internet.", fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            val f = DbManager.dbFile()
            LabeledValue("Tamaño", String.format(Locale.US, "%.1f KB", f.length() / 1024.0))
            LabeledValue("Última modificación", SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(f.lastModified())))
        }
        SectionCard("Sacar respaldo (backup)", headerColor = CB.Green) {
            Text("Genera una copia completa de la base de datos. Guárdala en Descargas, Google Drive, USB o envíala por WhatsApp/correo.", fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { saveLauncher.launch(Backup.suggestedName()) }, enabled = !busy,
                modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = CB.Green)
            ) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp)); Text("Guardar respaldo en el dispositivo")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        try {
                            val file = io { Backup.snapshot(ctx) }
                            ctx.startActivity(Files.shareIntent(ctx, file, Backup.MIME_DB, "Compartir respaldo"))
                        } catch (e: Exception) {
                            notify("No se pudo crear el respaldo: ${e.message}", false)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp)); Text("Compartir respaldo")
            }
        }
        SectionCard("Restaurar base de datos", headerColor = CB.Danger) {
            Text(
                "Usa esta opción para recuperar tus datos o llevarlos a otro celular: instala la app, copia tu archivo de respaldo (.db) y selecciónalo aquí. También sirve el archivo finanzas.db del sistema web.",
                fontSize = 13.sp
            )
            Spacer(Modifier.height(6.dp))
            Text("⚠️ Se reemplazarán TODOS los datos actuales de este teléfono. Antes se guarda una copia automática por seguridad.", fontSize = 12.sp, color = CB.Danger)
            Spacer(Modifier.height(10.dp))
            if (isAdmin) {
                Button(
                    onClick = { openLauncher.launch(arrayOf("*/*")) }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = CB.Danger)
                ) {
                    Icon(Icons.Filled.Restore, contentDescription = null)
                    Spacer(Modifier.width(8.dp)); Text("Seleccionar archivo de respaldo")
                }
            } else {
                Text("Solo el administrador puede restaurar la base de datos.", fontSize = 13.sp, color = CB.Gray)
            }
        }
        SectionCard("Copias automáticas del teléfono", headerColor = CB.Secondary) {
            Text("La app guarda una copia diaria (últimos 7 días) dentro del teléfono.", fontSize = 12.sp, color = CB.Gray)
            if (autos.isEmpty()) Text("Aún no hay copias automáticas.", fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            for (f in autos) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(f.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(f.lastModified())) + " · " + String.format(Locale.US, "%.1f KB", f.length() / 1024.0),
                            fontSize = 11.sp, color = CB.Gray
                        )
                    }
                    if (isAdmin) TextButton(onClick = { pendingFile = f }) { Text("Restaurar") }
                }
                HorizontalDivider(color = CB.Line)
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    val pu = pendingUri
    val pf = pendingFile
    if (pu != null || pf != null) {
        ConfirmDialog(
            title = "¿Restaurar base de datos?",
            text = "Se reemplazarán todos los datos actuales por los del respaldo seleccionado. Después deberás iniciar sesión nuevamente.",
            confirmText = "Restaurar", danger = true,
            onConfirm = {
                pendingUri = null
                pendingFile = null
                scope.launch {
                    busy = true
                    val r = io {
                        if (pu != null) Backup.restoreFrom(ctx, pu) else Backup.restoreFromFile(ctx, pf!!)
                    }
                    busy = false
                    notify(r.message, r.ok)
                    if (r.ok) onRestored() else tick++
                }
            },
            onDismiss = { pendingUri = null; pendingFile = null }
        )
    }
}
