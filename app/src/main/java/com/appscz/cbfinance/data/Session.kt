package com.appscz.cbfinance.data

import android.content.Context

data class User(
    val id: Long,
    val username: String,
    val nombre: String,
    val email: String,
    val rol: String
) {
    val isAdmin: Boolean get() = rol == "admin"
    val rolLabel: String get() = if (rol == "admin") "Administrador" else "Contador"
}

data class OpResult(
    val ok: Boolean,
    val message: String,
    val warning: Boolean = false,
    val duplicate: Boolean = false
)

/** Sesión del usuario (equivalente a Flask-Login + session['empresa_id']). Se recuerda entre aperturas de la app. */
object Session {
    private const val PREFS = "cb_session"
    var user: User? = null
    var empresaId: Long? = null

    private fun prefs() = DbManager.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun userFromRow(r: Rec): User = User(
        id = r.lng("id"),
        username = r.str("username"),
        nombre = r.str("nombre_completo"),
        email = r.str("email"),
        rol = r.str("rol")
    )

    fun start(u: User, empresa: Long?) {
        user = u
        empresaId = empresa
        save()
    }

    fun setEmpresa(id: Long) {
        empresaId = id
        save()
    }

    private fun save() {
        val e = prefs().edit()
        val u = user
        if (u == null) {
            e.clear()
        } else {
            e.putLong("user_id", u.id)
            e.putLong("empresa_id", empresaId ?: -1L)
        }
        e.apply()
    }

    fun logout() {
        user = null
        empresaId = null
        prefs().edit().clear().apply()
    }

    /** Intenta recuperar la sesión guardada. Devuelve true si el usuario sigue activo. */
    fun restore(): Boolean {
        val p = prefs()
        val uid = p.getLong("user_id", -1L)
        if (uid < 0) return false
        val db = DbManager.db()
        val r = db.row("SELECT * FROM usuarios WHERE id = ? AND activo = 1", listOf(uid)) ?: run {
            logout()
            return false
        }
        val u = userFromRow(r)
        user = u
        val eid = p.getLong("empresa_id", -1L)
        empresaId = if (eid >= 0 && AuthRepo.tienePermiso(u, eid)) eid else null
        return true
    }
}
