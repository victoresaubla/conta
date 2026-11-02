package com.appscz.cbfinance.data

class LoginResult(val user: User?, val error: String?, val empresas: List<Rec>)

object AuthRepo {
    fun login(username: String, password: String): LoginResult {
        val db = DbManager.db()
        val r = db.row("SELECT * FROM usuarios WHERE username = ? AND activo = 1", listOf(username.trim()))
        if (r == null || !Security.verify(r.str("password_hash"), password)) {
            return LoginResult(null, "Usuario o contraseña incorrectos", emptyList())
        }
        val u = Session.userFromRow(r)
        val empresas = empresasUsuario(u)
        if (empresas.isEmpty()) {
            return LoginResult(null, "Tu cuenta no tiene empresas asignadas. Contacta al administrador.", emptyList())
        }
        return LoginResult(u, null, empresas)
    }

    /** Admin: todas las empresas activas. Contador: solo las asignadas. */
    fun empresasUsuario(u: User): List<Rec> {
        val db = DbManager.db()
        return if (u.isAdmin) {
            db.rows("SELECT * FROM empresas WHERE activo = 1 ORDER BY nombre")
        } else {
            db.rows(
                """SELECT e.* FROM empresas e
                   INNER JOIN usuario_empresa ue ON e.id = ue.empresa_id
                   WHERE ue.usuario_id = ? AND e.activo = 1
                   ORDER BY e.nombre""",
                listOf(u.id)
            )
        }
    }

    fun tienePermiso(u: User, empresaId: Long): Boolean {
        val db = DbManager.db()
        val activa = db.scalarLong("SELECT COUNT(*) FROM empresas WHERE id = ? AND activo = 1", listOf(empresaId))
        if (activa == 0L) return false
        if (u.isAdmin) return true
        return db.scalarLong(
            "SELECT COUNT(*) FROM usuario_empresa WHERE usuario_id = ? AND empresa_id = ?",
            listOf(u.id, empresaId)
        ) > 0L
    }

    fun empresaActual(): Rec? {
        val id = Session.empresaId ?: return null
        return DbManager.db().row("SELECT * FROM empresas WHERE id = ?", listOf(id))
    }

    fun cambiarPassword(userId: Long, actual: String, nueva: String, confirmar: String): OpResult {
        if (actual.isEmpty() || nueva.isEmpty() || confirmar.isEmpty()) return OpResult(false, "Todos los campos son requeridos")
        if (nueva != confirmar) return OpResult(false, "Las contraseñas nuevas no coinciden")
        val db = DbManager.db()
        val r = db.row("SELECT password_hash FROM usuarios WHERE id = ?", listOf(userId))
            ?: return OpResult(false, "Usuario no encontrado")
        if (!Security.verify(r.str("password_hash"), actual)) return OpResult(false, "La contraseña actual es incorrecta")
        db.updateRow("usuarios", mapOf("password_hash" to Security.hash(nueva)), "id = ?", listOf(userId))
        return OpResult(true, "Contraseña actualizada correctamente")
    }
}

object EmpresaRepo {
    fun listar(): List<Rec> = DbManager.db().rows(
        """SELECT e.*, u.nombre_completo as creador, COUNT(DISTINCT ue.usuario_id) as num_usuarios
           FROM empresas e
           LEFT JOIN usuarios u ON e.created_by = u.id
           LEFT JOIN usuario_empresa ue ON e.id = ue.empresa_id
           GROUP BY e.id
           ORDER BY e.nombre"""
    )

    fun agregar(creadorId: Long, nombre: String, nit: String, direccion: String, telefono: String, email: String): OpResult {
        if (nombre.isBlank() || nit.isBlank()) return OpResult(false, "Nombre y NIT son obligatorios")
        val db = DbManager.db()
        return try {
            db.tx {
                val id = db.insertRow(
                    "empresas",
                    mapOf(
                        "nombre" to nombre.trim(), "nit" to nit.trim(), "direccion" to direccion,
                        "telefono" to telefono, "email" to email, "activo" to 1, "created_by" to creadorId
                    )
                )
                db.insertRow("usuario_empresa", mapOf("usuario_id" to creadorId, "empresa_id" to id))
            }
            OpResult(true, "Empresa ${nombre.trim()} creada correctamente")
        } catch (e: Exception) {
            OpResult(false, "Error al crear empresa: ${e.message}")
        }
    }

    fun editar(id: Long, nombre: String, nit: String, direccion: String, telefono: String, email: String, activo: Boolean): OpResult {
        if (nombre.isBlank() || nit.isBlank()) return OpResult(false, "Nombre y NIT son obligatorios")
        return try {
            DbManager.db().updateRow(
                "empresas",
                mapOf(
                    "nombre" to nombre.trim(), "nit" to nit.trim(), "direccion" to direccion,
                    "telefono" to telefono, "email" to email, "activo" to (if (activo) 1 else 0)
                ),
                "id = ?", listOf(id)
            )
            OpResult(true, "Empresa actualizada correctamente")
        } catch (e: Exception) {
            OpResult(false, "Error al actualizar empresa: ${e.message}")
        }
    }

    fun desactivar(id: Long): OpResult {
        if (id == 1L) return OpResult(false, "No se puede desactivar la empresa principal")
        return try {
            DbManager.db().updateRow("empresas", mapOf("activo" to 0), "id = ?", listOf(id))
            OpResult(true, "Empresa desactivada correctamente")
        } catch (e: Exception) {
            OpResult(false, "Error al desactivar empresa: ${e.message}")
        }
    }
}

object UsuarioRepo {
    fun listar(): List<Rec> = DbManager.db().rows(
        """SELECT u.id, u.username, u.nombre_completo, u.email, u.rol, u.activo,
                  GROUP_CONCAT(e.nombre, ', ') as empresas,
                  GROUP_CONCAT(e.id) as empresas_ids
           FROM usuarios u
           LEFT JOIN usuario_empresa ue ON u.id = ue.usuario_id
           LEFT JOIN empresas e ON ue.empresa_id = e.id
           GROUP BY u.id
           ORDER BY u.username"""
    )

    fun empresasActivas(): List<Rec> = DbManager.db().rows("SELECT * FROM empresas WHERE activo = 1 ORDER BY nombre")

    fun agregar(username: String, nombre: String, email: String, password: String, rol: String, empresas: List<Long>): OpResult {
        if (username.isBlank() || nombre.isBlank() || email.isBlank() || password.isEmpty()) {
            return OpResult(false, "Completa todos los campos")
        }
        val db = DbManager.db()
        if (db.scalarLong("SELECT COUNT(*) FROM usuarios WHERE username = ?", listOf(username.trim())) > 0L) {
            return OpResult(false, "El nombre de usuario ya existe")
        }
        return try {
            val hash = Security.hash(password)
            db.tx {
                val uid = db.insertRow(
                    "usuarios",
                    mapOf(
                        "username" to username.trim(), "password_hash" to hash, "nombre_completo" to nombre.trim(),
                        "email" to email.trim(), "rol" to rol, "activo" to 1
                    )
                )
                for (e in empresas) db.insertRow("usuario_empresa", mapOf("usuario_id" to uid, "empresa_id" to e))
            }
            OpResult(true, "Usuario ${username.trim()} creado correctamente")
        } catch (e: Exception) {
            OpResult(false, "Error al crear usuario: ${e.message}")
        }
    }

    fun editar(id: Long, nombre: String, email: String, rol: String, activo: Boolean, empresas: List<Long>): OpResult {
        val db = DbManager.db()
        return try {
            db.tx {
                db.updateRow(
                    "usuarios",
                    mapOf("nombre_completo" to nombre.trim(), "email" to email.trim(), "rol" to rol, "activo" to (if (activo) 1 else 0)),
                    "id = ?", listOf(id)
                )
                db.exec("DELETE FROM usuario_empresa WHERE usuario_id = ?", listOf(id))
                for (e in empresas) db.insertRow("usuario_empresa", mapOf("usuario_id" to id, "empresa_id" to e))
            }
            OpResult(true, "Usuario actualizado correctamente")
        } catch (e: Exception) {
            OpResult(false, "Error al actualizar usuario: ${e.message}")
        }
    }

    fun asignarEmpresas(id: Long, empresas: List<Long>): OpResult {
        val db = DbManager.db()
        return try {
            db.tx {
                db.exec("DELETE FROM usuario_empresa WHERE usuario_id = ?", listOf(id))
                for (e in empresas) db.insertRow("usuario_empresa", mapOf("usuario_id" to id, "empresa_id" to e))
            }
            OpResult(true, "Empresas asignadas correctamente")
        } catch (e: Exception) {
            OpResult(false, "Error al asignar empresas: ${e.message}")
        }
    }

    fun cambiarPassword(id: Long, nueva: String): OpResult {
        if (nueva.isEmpty()) return OpResult(false, "Debes proporcionar una contraseña")
        DbManager.db().updateRow("usuarios", mapOf("password_hash" to Security.hash(nueva)), "id = ?", listOf(id))
        return OpResult(true, "Contraseña actualizada correctamente")
    }

    fun eliminar(id: Long, actualId: Long): OpResult {
        if (id == actualId) return OpResult(false, "No puedes eliminar tu propia cuenta")
        if (id == 1L) return OpResult(false, "No se puede eliminar el usuario administrador principal")
        val db = DbManager.db()
        db.tx {
            db.exec("DELETE FROM usuario_empresa WHERE usuario_id = ?", listOf(id))
            db.exec("DELETE FROM usuarios WHERE id = ?", listOf(id))
        }
        return OpResult(true, "Usuario eliminado correctamente")
    }

    fun desactivar(id: Long, actualId: Long): OpResult {
        if (id == actualId) return OpResult(false, "No puedes desactivar tu propia cuenta")
        DbManager.db().updateRow("usuarios", mapOf("activo" to 0), "id = ?", listOf(id))
        return OpResult(true, "Usuario desactivado correctamente")
    }
}
