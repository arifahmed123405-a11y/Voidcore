package dev.voidcore.providerrouter

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecretStore(context: Context) {
 private val prefs = context.getSharedPreferences("voidcore_secrets", Context.MODE_PRIVATE)
 private val alias = "voidcore-provider-secrets-v1"
 private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

 private fun key(): SecretKey {
  (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
  val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
  generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
   .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
   .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
   .build())
  return generator.generateKey()
 }

 fun put(name: String, value: String) {
  if (value.isBlank()) { prefs.edit().remove(name).apply(); return }
  val cipher = Cipher.getInstance("AES/GCM/NoPadding")
  cipher.init(Cipher.ENCRYPT_MODE, key())
  val payload = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
  prefs.edit().putString(name, Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
 }

 fun get(name: String): String? {
  val encoded = prefs.getString(name, null) ?: return null
  return runCatching {
   val payload = Base64.decode(encoded, Base64.NO_WRAP)
   val iv = payload.copyOfRange(0, 12)
   val encrypted = payload.copyOfRange(12, payload.size)
   val cipher = Cipher.getInstance("AES/GCM/NoPadding")
   cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
   String(cipher.doFinal(encrypted), Charsets.UTF_8)
  }.getOrNull()
 }
}
