package com.btmessenger.app.security

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Optional cryptography utilities for encrypting messages
 * Can be used in future versions for secure messaging
 */
object Crypto {
    
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    
    /**
     * Generate a new AES key
     */
    fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(ALGORITHM)
        keyGenerator.init(256)
        return keyGenerator.generateKey()
    }
    
    /**
     * Convert key to string for sharing
     */
    fun keyToString(key: SecretKey): String {
        return Base64.encodeToString(key.encoded, Base64.NO_WRAP)
    }
    
    /**
     * Convert string back to key
     */
    fun stringToKey(keyString: String): SecretKey {
        val decodedKey = Base64.decode(keyString, Base64.NO_WRAP)
        return SecretKeySpec(decodedKey, 0, decodedKey.size, ALGORITHM)
    }
    
    /**
     * Encrypt a message
     */
    fun encrypt(message: String, key: SecretKey): Pair<String, String> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(message.toByteArray(Charsets.UTF_8))
        
        val encryptedMessage = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        val ivString = Base64.encodeToString(iv, Base64.NO_WRAP)
        
        return Pair(encryptedMessage, ivString)
    }
    
    /**
     * Decrypt a message
     */
    fun decrypt(encryptedMessage: String, ivString: String, key: SecretKey): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = Base64.decode(ivString, Base64.NO_WRAP)
        val ivSpec = IvParameterSpec(iv)
        
        cipher.init(Cipher.DECRYPT_MODE, key, ivSpec)
        
        val encryptedBytes = Base64.decode(encryptedMessage, Base64.NO_WRAP)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        
        return String(decryptedBytes, Charsets.UTF_8)
    }
    
    /**
     * Generate SHA-256 hash of a string
     */
    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
    
    /**
     * Generate a simple peer ID based on device info
     */
    fun generatePeerId(deviceName: String, address: String): String {
        return sha256("$deviceName:$address").take(16)
    }
}
