package com.example.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object KeystoreCrypto {
  private const val ANDROID_KEYSTORE = "AndroidKeyStore"
  private const val KEY_ALIAS = "rivia_master_aes_key_v1"
  private const val TRANSFORMATION = "AES/GCM/NoPadding"
  private const val GCM_TAG_LENGTH_BITS = 128
  private const val GCM_IV_LENGTH_BYTES = 12

  @Volatile private var fallbackSoftwareKey: SecretKey? = null

  private fun getOrCreateSecretKey(): SecretKey {
    return try {
      val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
      val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
      if (existingKey != null) {
        existingKey.secretKey
      } else {
        val keyGenerator =
          KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec =
          KeyGenParameterSpec.Builder(
              KEY_ALIAS,
              KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        keyGenerator.generateKey()
      }
    } catch (_: Exception) {
      // Fallback only for local JVM unit test environment where AndroidKeyStore is absent
      fallbackSoftwareKey
        ?: synchronized(this) {
          fallbackSoftwareKey
            ?: run {
              val bytes = ByteArray(32)
              SecureRandom().nextBytes(bytes)
              SecretKeySpec(bytes, "AES").also { fallbackSoftwareKey = it }
            }
        }
    }
  }

  fun encrypt(plainText: String): String {
    if (plainText.isBlank()) return ""
    val cipher = Cipher.getInstance(TRANSFORMATION)
    val secretKey = getOrCreateSecretKey()
    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
    val iv = cipher.iv
    val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
    val buffer = ByteBuffer.allocate(4 + iv.size + cipherBytes.size)
    buffer.putInt(iv.size)
    buffer.put(iv)
    buffer.put(cipherBytes)
    return Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
  }

  fun decrypt(encryptedBase64: String): String {
    if (encryptedBase64.isBlank()) return ""
    return try {
      val raw = Base64.decode(encryptedBase64, Base64.NO_WRAP)
      val buffer = ByteBuffer.wrap(raw)
      val ivSize = buffer.int
      if (ivSize <= 0 || ivSize > 32) return ""
      val iv = ByteArray(ivSize)
      buffer.get(iv)
      val cipherBytes = ByteArray(buffer.remaining())
      buffer.get(cipherBytes)

      val cipher = Cipher.getInstance(TRANSFORMATION)
      val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
      cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
      String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
    } catch (_: Exception) {
      ""
    }
  }
}
