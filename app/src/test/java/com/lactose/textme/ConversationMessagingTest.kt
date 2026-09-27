package com.lactose.textme

import com.lactose.textme.core.crypto.CryptoEngine
import com.lactose.textme.domain.model.Identity
import com.lactose.textme.domain.model.IdentityStatus
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ConversationMessagingTest {

    @Test
    fun deriveConversationKey_produces_identical_key_for_same_conversation_id() {
        val conversationId = UUID.randomUUID().toString()

        val keyUserA = CryptoEngine.deriveConversationKey(conversationId)
        val keyUserB = CryptoEngine.deriveConversationKey(conversationId)

        assertArrayEquals("Both participants must derive the exact same symmetric key", keyUserA.encoded, keyUserB.encoded)
        assertEquals("AES", keyUserA.algorithm)
        assertEquals(32, keyUserA.encoded.size) // 256-bit key
    }

    @Test
    fun deriveConversationKey_produces_distinct_keys_for_different_conversations() {
        val convId1 = UUID.randomUUID().toString()
        val convId2 = UUID.randomUUID().toString()

        val key1 = CryptoEngine.deriveConversationKey(convId1)
        val key2 = CryptoEngine.deriveConversationKey(convId2)

        assertFalse("Keys for different conversations must differ", key1.encoded.contentEquals(key2.encoded))
    }

    @Test
    fun encrypt_and_decrypt_recovers_original_plaintext() {
        val conversationId = UUID.randomUUID().toString()
        val originalText = "Hello from User A! Let's chat securely."

        val keyA = CryptoEngine.deriveConversationKey(conversationId)
        val payload = CryptoEngine.encryptMessage(keyA, originalText)

        assertNotNull(payload.ciphertext)
        assertNotNull(payload.nonce)
        assertNotEquals(originalText, payload.ciphertext)

        val keyB = CryptoEngine.deriveConversationKey(conversationId)
        val decryptedText = CryptoEngine.decryptMessage(keyB, payload.ciphertext, payload.nonce)

        assertEquals(originalText, decryptedText)
    }

    @Test
    fun decrypt_with_wrong_key_fails() {
        val conv1 = UUID.randomUUID().toString()
        val conv2 = UUID.randomUUID().toString()

        val key1 = CryptoEngine.deriveConversationKey(conv1)
        val key2 = CryptoEngine.deriveConversationKey(conv2)

        val payload = CryptoEngine.encryptMessage(key1, "Secret message")

        try {
            CryptoEngine.decryptMessage(key2, payload.ciphertext, payload.nonce)
            fail("Decryption with wrong conversation key should throw an exception")
        } catch (_: Exception) {
            // Expected AEADBadTagException / general security exception
        }
    }

    @Test
    fun auto_rotation_is_disabled_by_default() {
        val identity = Identity(
            id = "id_test",
            userId = "user_test",
            publicId = "K7F2A91X",
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 86400000L,
            status = IdentityStatus.ACTIVE
        )

        assertFalse("Identity must have autoRotationEnabled disabled by default", identity.autoRotationEnabled)
    }

    @Test
    fun conversation_displayTimestamp_uses_latest_message_timestamp_when_present() {
        val conv = com.lactose.textme.domain.model.Conversation(
            id = "conv_1",
            participantAId = "pA",
            participantAPublicId = "AAAA1111",
            participantBId = "pB",
            participantBPublicId = "BBBB2222",
            createdAt = 1000L,
            expiresAt = 9000L,
            status = com.lactose.textme.domain.model.ConversationStatus.ACTIVE,
            lastMessageTimestamp = 5000L
        )

        assertEquals("When messages exist, display timestamp must be latest message timestamp", 5000L, conv.displayTimestamp)
    }

    @Test
    fun conversation_displayTimestamp_falls_back_to_createdAt_when_no_messages() {
        val conv = com.lactose.textme.domain.model.Conversation(
            id = "conv_1",
            participantAId = "pA",
            participantAPublicId = "AAAA1111",
            participantBId = "pB",
            participantBPublicId = "BBBB2222",
            createdAt = 1000L,
            expiresAt = 9000L,
            status = com.lactose.textme.domain.model.ConversationStatus.ACTIVE,
            lastMessageTimestamp = null
        )

        assertEquals("When no messages exist, display timestamp must fall back to conversation createdAt", 1000L, conv.displayTimestamp)
    }
}
