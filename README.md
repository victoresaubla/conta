# C&B Consultores – App Android (Kotlin + Jetpack Compose)

App nativa que replica el sistema web de gestión financiera multi-empresa:

- Login con usuarios y roles (Administrador / Contador), selección y cambio de empresa
- Dashboard con KPIs, gráficos de tendencia, ventas/compras recientes, planillas, servicios y alquileres (con filtro por fechas y PDF)
- Ingresos (ventas), Egresos (compras), Gastos y Régimen: alta, planillas (AFP, Caja Nacional, Min. Trabajo, horas extras), detección de duplicados, eliminación individual y por rango de IDs, categorías, importar/exportar Excel y plantillas
- Ventas/compras/gastos/régimen **a crédito** → generan automáticamente Cuentas por Cobrar / Pagar
- Cuentas por Cobrar y por Pagar: pagos parciales, historial, sincronización del estado con el registro origen, importar/exportar Excel
- Reportes financieros con filtros (fechas, razón social, tipo de documento), estado de resultados, categorías, flujo por tipo de documento, planillas y exportación a PDF
- Administración de usuarios y empresas (solo admin), perfil y cambio de contraseña
- **Base de datos local SQLite en el teléfono** (`finanzas.db`, mismo esquema que el sistema web), sin internet
- **Backup y restauración**: guardar el respaldo (.db) en el dispositivo / Drive / USB o compartirlo, y restaurarlo en otro celular. También acepta el `finanzas.db` del sistema web (las contraseñas existentes siguen funcionando).
- Copia automática diaria interna (últimos 7 días)

Usuario inicial (si la base está vacía): **admin / admin123** → cámbiala en *Mi Perfil*.

## Compilar en Codemagic desde GitHub

1. Sube esta carpeta completa a un repositorio de GitHub (la raíz del repo debe contener `codemagic.yaml`, `build.gradle.kts`, `settings.gradle.kts` y la carpeta `app/`).
2. En https://codemagic.io → **Add application** → elige GitHub → selecciona tu repositorio → **Android App** → *Configure with codemagic.yaml*.
3. Inicia el workflow **android-apk**. Al terminar, descarga `app-debug.apk` desde *Artifacts* e instálalo en el teléfono.
4. Para publicar en Play Store usa el workflow **android-release**: sube tu keystore en *Team settings → Code signing identities → Android keystores* con la referencia `cb_keystore`.

El workflow genera el Gradle Wrapper automáticamente si no existe. Si prefieres incluirlo en el repositorio, ejecuta una vez `gradle wrapper --gradle-version 8.9` y sube los archivos.

## Llevar los datos a otro celular
1. En el celular original: menú → **Respaldo y Restauración** → *Guardar respaldo en el dispositivo* (o *Compartir*).
2. Envía el archivo `.db` al nuevo celular (Drive, WhatsApp, USB…).
3. En el nuevo celular: instala la app → inicia con `admin / admin123` → **Respaldo y Restauración** → *Seleccionar archivo de respaldo*.

## Estructura
```
app/src/main/java/com/appscz/cbfinance/
  data/   Db.kt (SQLite + esquema), Security.kt (hash compatible con Werkzeug), repositorios por módulo
  util/   Xlsx.kt (Excel), PdfExport.kt (PDF), Backup.kt (respaldo/restauración), Fmt.kt
  ui/     Tema (colores del sistema web), componentes y pantallas (Compose)
```
