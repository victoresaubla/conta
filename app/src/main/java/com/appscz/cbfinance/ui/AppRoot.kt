@file:OptIn(ExperimentalMaterial3Api::class)

package com.appscz.cbfinance.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appscz.cbfinance.data.AuthRepo
import com.appscz.cbfinance.data.CuentaTipo
import com.appscz.cbfinance.data.DbManager
import com.appscz.cbfinance.data.Kind
import com.appscz.cbfinance.data.Rec
import com.appscz.cbfinance.data.Session
import com.appscz.cbfinance.data.lng
import com.appscz.cbfinance.data.str
import com.appscz.cbfinance.ui.screens.BackupScreen
import com.appscz.cbfinance.ui.screens.CuentasScreen
import com.appscz.cbfinance.ui.screens.DashboardScreen
import com.appscz.cbfinance.ui.screens.EmpresasScreen
import com.appscz.cbfinance.ui.screens.LoginScreen
import com.appscz.cbfinance.ui.screens.MovScreen
import com.appscz.cbfinance.ui.screens.PerfilScreen
import com.appscz.cbfinance.ui.screens.ReportesScreen
import com.appscz.cbfinance.ui.screens.SelectEmpresaScreen
import com.appscz.cbfinance.ui.screens.UsuariosScreen
import com.appscz.cbfinance.util.Backup
import com.appscz.cbfinance.util.io
import kotlinx.coroutines.launch

enum class Dest(val label: String, val icon: ImageVector, val adminOnly: Boolean = false) {
    Dashboard("Dashboard", Icons.Filled.Dashboard),
    Ingresos("Ingresos", Icons.Filled.TrendingUp),
    Egresos("Egresos", Icons.Filled.ShoppingCart),
    Gastos("Gastos", Icons.Filled.Payments),
    Regimen("Régimen", Icons.Filled.AccountBalance),
    Cobrar("Ctas. por Cobrar", Icons.Filled.RequestQuote),
    Pagar("Ctas. por Pagar", Icons.Filled.ReceiptLong),
    Reportes("Reportes", Icons.Filled.Assessment),
    Usuarios("Usuarios", Icons.Filled.People, true),
    Empresas("Empresas", Icons.Filled.Business, true),
    Perfil("Mi Perfil", Icons.Filled.Person),
    Respaldo("Respaldo y Restauración", Icons.Filled.Backup)
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notify: (String, Boolean) -> Unit = { msg, ok ->
        scope.launch { snack.showSnackbar((if (ok) "✅ " else "⚠️ ") + msg) }
    }
    var stage by remember { mutableStateOf("loading") }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        val restored = io {
            DbManager.db()
            val r = Session.restore()
            Backup.autoBackupIfNeeded(ctx)
            r
        }
        stage = if (!restored || Session.user == null) "login" else if (Session.empresaId == null) "empresa" else "main"
    }

    CompositionLocalProvider(LocalNotify provides notify) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (stage) {
                "loading" -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CB.HeaderBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("C&B CONSULTORES", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        CircularProgressIndicator(color = Color.White)
                    }
                }
                "login" -> LoginScreen(onLoggedIn = { needs -> stage = if (needs) "empresa" else "main" })
                "empresa" -> SelectEmpresaScreen(
                    onSelected = { stage = "main" },
                    onLogout = { Session.logout(); stage = "login" }
                )
                else -> MainShell(
                    onLogout = { Session.logout(); stage = "login" },
                    onRestored = { stage = "login" }
                )
            }
            SnackbarHost(hostState = snack, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
fun MainShell(onLogout: () -> Unit, onRestored: () -> Unit) {
    val user = Session.user ?: return
    val ctx = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val notify = LocalNotify.current
    var dest by remember { mutableStateOf(Dest.Dashboard) }
    var empresaKey by remember { mutableStateOf(0) }
    var empresa by remember { mutableStateOf<Rec?>(null) }
    var empresas by remember { mutableStateOf<List<Rec>>(emptyList()) }
    var showSwitch by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

    LaunchedEffect(empresaKey) {
        empresa = io { AuthRepo.empresaActual() }
        empresas = io { AuthRepo.empresasUsuario(user) }
    }

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(enabled = !drawerState.isOpen && dest != Dest.Dashboard) { dest = Dest.Dashboard }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CB.HeaderBrush)
                            .padding(18.dp)
                    ) {
                        Text("C&B CONSULTORES", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) { Text(user.nombre.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(user.nombre, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(user.rolLabel, color = Color(0xFFD0D7DE), fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                                .clickable { showSwitch = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.Business, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(empresa?.str("nombre") ?: "Empresa", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 8.dp)
                    ) {
                        Icon(if (ThemeState.dark) Icons.Filled.DarkMode else Icons.Filled.LightMode, contentDescription = null, tint = CB.Accent)
                        Spacer(Modifier.width(12.dp))
                        Text(if (ThemeState.dark) "Modo oscuro" else "Modo claro", modifier = Modifier.weight(1f))
                        Switch(checked = ThemeState.dark, onCheckedChange = { ThemeState.set(ctx, it) })
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(8.dp))
                    for (d in Dest.values()) {
                        if (d.adminOnly && !user.isAdmin) continue
                        NavigationDrawerItem(
                            label = { Text(d.label) },
                            selected = dest == d,
                            onClick = {
                                dest = d
                                scope.launch { drawerState.close() }
                            },
                            icon = { Icon(d.icon, contentDescription = null) },
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    NavigationDrawerItem(
                        label = { Text("Cerrar Sesión") },
                        selected = false,
                        onClick = { confirmLogout = true },
                        icon = { Icon(Icons.Filled.Logout, contentDescription = null) },
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(dest.label, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            val n = empresa?.str("nombre") ?: ""
                            if (n.isNotEmpty()) Text(n, fontSize = 11.sp, color = Color(0xFFD0D7DE))
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menú")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = CB.Primary,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { pad ->
            Box(modifier = Modifier
                .padding(pad)
                .fillMaxSize()) {
                val eid = Session.empresaId
                if (eid == null) {
                    EmptyState("Selecciona una empresa")
                } else {
                    key(empresaKey, dest) {
                        when (dest) {
                            Dest.Dashboard -> DashboardScreen(eid, empresa?.str("nombre") ?: "Empresa")
                            Dest.Ingresos -> MovScreen(Kind.VENTAS, eid)
                            Dest.Egresos -> MovScreen(Kind.COMPRAS, eid)
                            Dest.Gastos -> MovScreen(Kind.GASTOS, eid)
                            Dest.Regimen -> MovScreen(Kind.REGIMEN, eid)
                            Dest.Cobrar -> CuentasScreen(CuentaTipo.COBRAR, eid)
                            Dest.Pagar -> CuentasScreen(CuentaTipo.PAGAR, eid)
                            Dest.Reportes -> ReportesScreen(eid, empresa?.str("nombre") ?: "Empresa")
                            Dest.Usuarios -> UsuariosScreen()
                            Dest.Empresas -> EmpresasScreen(onChanged = { empresaKey++ })
                            Dest.Perfil -> PerfilScreen(onEmpresaChanged = { empresaKey++; dest = Dest.Dashboard })
                            Dest.Respaldo -> BackupScreen(onRestored = onRestored)
                        }
                    }
                }
            }
        }
    }

    if (showSwitch) {
        AlertDialog(
            onDismissRequest = { showSwitch = false },
            title = { Text("Cambiar de empresa", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    for (e in empresas) {
                        val sel = e.lng("id") == Session.empresaId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Session.setEmpresa(e.lng("id"))
                                    notify("Empresa cambiada a: ${e.str("nombre")}", true)
                                    showSwitch = false
                                    empresaKey++
                                    scope.launch { drawerState.close() }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(if (sel) Icons.Filled.CheckCircle else Icons.Filled.Business, contentDescription = null, tint = if (sel) CB.Success else CB.Gray)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(e.str("nombre"), fontWeight = FontWeight.SemiBold)
                                Text("NIT: ${e.str("nit")}", fontSize = 12.sp, color = CB.Gray)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSwitch = false }) { Text("Cerrar") } }
        )
    }
    if (confirmLogout) {
        ConfirmDialog(
            title = "Cerrar sesión", text = "¿Deseas cerrar tu sesión?", confirmText = "Cerrar sesión",
            onConfirm = { confirmLogout = false; onLogout() }, onDismiss = { confirmLogout = false }
        )
    }
}
