package com.appscz.cbfinance.data

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.generators.SCrypt
import org.bouncycastle.crypto.params.KeyParameter
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Hashes de contraseña 100 % compatibles con Werkzeug (el sistema web):
 *  - pbkdf2:sha256:ITER$salt$hex
 *  - scrypt:N:r:p$salt$hex
 *  - sha256$salt$hex (formato antiguo)
 * Así, una base de datos finanzas.db del sistema web puede restaurarse en la app y los usuarios entran con su misma clave.
 */
object Security {
    private const val ITERATIONS = 150000
    private const val SALT_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    fun hash(password: String): String {
        val rnd = SecureRandom()
        val salt = StringBuilder()
        for (i in 0 until 16) salt.append(SALT_CHARS[rnd.nextInt(SALT_CHARS.length)])
        val dk = pbkdf2(password, salt.toString(), ITERATIONS)
        return "pbkdf2:sha256:$ITERATIONS\$$salt\$${toHex(dk)}"
    }

    fun verify(stored: String?, password: String): Boolean {
        if (stored.isNullOrEmpty()) return false
        return try {
            val parts = stored.split("$")
            if (parts.size != 3) return false
            val method = parts[0]
            val salt = parts[1]
            val expected = parts[2]
            val computed: String = when {
                method.startsWith("pbkdf2:") -> {
                    val p = method.split(":")
                    val algo = if (p.size > 1) p[1] else "sha256"
                    val iter = if (p.size > 2) p[2].toInt() else 260000
                    if (algo != "sha256") return false
                    toHex(pbkdf2(password, salt, iter))
                }
                method.startsWith("scrypt:") -> {
                    val p = method.split(":")
                    val n = if (p.size > 1) p[1].toInt() else 32768
                    val r = if (p.size > 2) p[2].toInt() else 8
                    val pp = if (p.size > 3) p[3].toInt() else 1
                    toHex(SCrypt.generate(password.toByteArray(Charsets.UTF_8), salt.toByteArray(Charsets.UTF_8), n, r, pp, 64))
                }
                method == "sha256" -> {
                    val mac = Mac.getInstance("HmacSHA256")
                    mac.init(SecretKeySpec(salt.toByteArray(Charsets.UTF_8), "HmacSHA256"))
                    toHex(mac.doFinal(password.toByteArray(Charsets.UTF_8)))
                }
                method == "plain" -> password
                else -> return false
            }
            MessageDigest.isEqual(computed.toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            false
        }
    }

    private fun pbkdf2(password: String, salt: String, iterations: Int): ByteArray {
        val gen = PKCS5S2ParametersGenerator(SHA256Digest())
        gen.init(password.toByteArray(Charsets.UTF_8), salt.toByteArray(Charsets.UTF_8), iterations)
        val key = gen.generateDerivedMacParameters(256) as KeyParameter
        return key.key
    }

    private fun toHex(b: ByteArray): String {
        val sb = StringBuilder(b.size * 2)
        for (x in b) {
            val v = x.toInt() and 0xff
            if (v < 16) sb.append('0')
            sb.append(Integer.toHexString(v))
        }
        return sb.toString()
    }
}
