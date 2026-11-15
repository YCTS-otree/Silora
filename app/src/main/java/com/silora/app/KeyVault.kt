package com.silora.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeyVault(context: Context) {
    private val prefs=context.getSharedPreferences("credentials",Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if(store.containsAlias("silora-api"))return store.getKey("silora-api",null) as SecretKey
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("silora-api",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun configured()=prefs.contains("ciphertext")
    fun save(value: String) {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
        check(prefs.edit().putString("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP))
            .putString("ciphertext",Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)),Base64.NO_WRAP)).commit()) { "密钥加密保存失败" }
    }
    fun read(): String {
        check(configured()) { "请先配置 DeepSeek API Key" }
        try {
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(prefs.getString("iv",null),Base64.NO_WRAP)))
            return String(cipher.doFinal(Base64.decode(prefs.getString("ciphertext",null),Base64.NO_WRAP)),Charsets.UTF_8)
        } catch(e:Exception) { throw IllegalStateException("密钥无法解密，请重新配置") }
    }
    fun delete() { check(prefs.edit().clear().commit()) { "删除密钥失败" } }
}
