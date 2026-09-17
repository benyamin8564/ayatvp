package com.ayat64.airvpn

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("secure_store", Context.MODE_PRIVATE)
    private val alias = "ai_iran_vpn_aes"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        kg.init(android.security.keystore.KeyGenParameterSpec.Builder(
            alias,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return kg.generateKey()
    }

    fun get(name: String): String? {
        val stored = prefs.getString(name, null) ?: return null
        val all = Base64.decode(stored, Base64.NO_WRAP)
        if (all.size < 13) return null
        val iv = all.copyOfRange(0, 12)
        val ciphertext = all.copyOfRange(12, all.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    fun put(name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val all = cipher.iv + ciphertext
        prefs.edit().putString(name, Base64.encodeToString(all, Base64.NO_WRAP)).apply()
    }
}
