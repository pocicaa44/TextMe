package com.lactose.textme.core.crypto

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoEngine {

    private const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12
    private val CONVERSATION_KEY_SALT = "textme_conversation_e2ee_key_v1:".toByteArray(Charsets.UTF_8)

    fun deriveConversationKey(conversationId: String): SecretKey {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(CONVERSATION_KEY_SALT)
        val hash = md.digest(conversationId.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(hash, "AES")
    }

    fun generateX25519KeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("XDH")
        return generator.generateKeyPair()
    }

    fun deriveSharedSecret(privateKey: PrivateKey, publicKey: PublicKey): SecretKey {
        val keyAgreement = KeyAgreement.getInstance("XDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(publicKey, true)
        val secretBytes = keyAgreement.generateSecret()
        // Using first 32 bytes for AES-256
        return SecretKeySpec(secretBytes, 0, 32, "AES")
    }

    fun encryptMessage(sharedKey: SecretKey, plaintext: String): EncryptedPayload {
        val iv = ByteArray(GCM_IV_LENGTH).apply {
            SecureRandom().nextBytes(this)
        }
        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, sharedKey, gcmSpec)
        val ciphertextBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        return EncryptedPayload(
            ciphertext = Base64.getEncoder().encodeToString(ciphertextBytes),
            nonce = Base64.getEncoder().encodeToString(iv)
        )
    }

    fun decryptMessage(sharedKey: SecretKey, ciphertextBase64: String, nonceBase64: String): String {
        val ciphertextBytes = Base64.getDecoder().decode(ciphertextBase64)
        val iv = Base64.getDecoder().decode(nonceBase64)

        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, sharedKey, gcmSpec)
        val decryptedBytes = cipher.doFinal(ciphertextBytes)

        return String(decryptedBytes, Charsets.UTF_8)
    }
}

data class EncryptedPayload(
    val ciphertext: String,
    val nonce: String
)
