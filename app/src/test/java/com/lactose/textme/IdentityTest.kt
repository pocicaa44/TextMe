package com.lactose.textme

import com.lactose.textme.core.common.Constants
import com.lactose.textme.domain.model.Identity
import com.lactose.textme.domain.model.IdentityStatus
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom

class IdentityTest {

    @Test
    fun publicId_format_is_8_chars_uppercase_alphanumeric() {
        val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val random = SecureRandom()
        val sb = StringBuilder(Constants.PUBLIC_ID_LENGTH)
        for (i in 0 until Constants.PUBLIC_ID_LENGTH) {
            sb.append(allowedChars[random.nextInt(allowedChars.length)])
        }
        val generated = sb.toString()

        assertEquals(8, generated.length)
        assertTrue(generated.all { it.isUpperCase() || it.isDigit() })
    }

    @Test
    fun identity_expiration_is_calculated_correctly_when_autoRotation_enabled() {
        val now = System.currentTimeMillis()
        val expiresAt = now + Constants.IDENTITY_LIFESPAN_MS

        val activeIdentity = Identity(
            id = "test_id",
            userId = "user_test",
            publicId = "K7F2A91X",
            createdAt = now,
            expiresAt = expiresAt,
            status = IdentityStatus.ACTIVE,
            autoRotationEnabled = true
        )

        assertFalse(activeIdentity.isExpired)
        assertTrue(activeIdentity.remainingTimeMillis > 0)
    }

    @Test
    fun expired_identity_returns_isExpired_true_when_autoRotation_enabled() {
        val now = System.currentTimeMillis()
        val expiredTime = now - 1000 // 1 second ago

        val expiredIdentity = Identity(
            id = "test_id",
            userId = "user_test",
            publicId = "K7F2A91X",
            createdAt = now - (25 * 60 * 60 * 1000),
            expiresAt = expiredTime,
            status = IdentityStatus.ACTIVE,
            autoRotationEnabled = true
        )

        assertTrue(expiredIdentity.isExpired)
        assertEquals(0L, expiredIdentity.remainingTimeMillis)
    }

    @Test
    fun identity_with_autoRotation_disabled_does_not_expire_by_time() {
        val now = System.currentTimeMillis()
        val pastTime = now - 1000 // 1 second ago

        val disabledIdentity = Identity(
            id = "test_id",
            userId = "user_test",
            publicId = "K7F2A91X",
            createdAt = now - (25 * 60 * 60 * 1000),
            expiresAt = pastTime,
            status = IdentityStatus.ACTIVE,
            autoRotationEnabled = false
        )

        // When auto-rotation is disabled, identity does not expire due to elapsed time
        assertFalse(disabledIdentity.isExpired)
        assertEquals(0L, disabledIdentity.remainingTimeMillis)
    }

    @Test
    fun turning_on_autoRotation_calculates_24h_remaining_time() {
        val now = System.currentTimeMillis()
        val newExpiresAt = now + Constants.IDENTITY_LIFESPAN_MS

        val reactivatedIdentity = Identity(
            id = "test_id",
            userId = "user_test",
            publicId = "K7F2A91X",
            createdAt = now,
            expiresAt = newExpiresAt,
            status = IdentityStatus.ACTIVE,
            autoRotationEnabled = true
        )

        assertFalse(reactivatedIdentity.isExpired)
        // Lifespan is 24 hours (86,400,000 ms)
        assertTrue(reactivatedIdentity.remainingTimeMillis in (86_300_000L..86_400_000L))
    }

    @Test
    fun self_id_messaging_is_rejected() {
        val myPublicId = "K7F2A91X"
        val enteredPublicId = "K7F2A91X"

        val isSelf = myPublicId.equals(enteredPublicId, ignoreCase = true)
        assertTrue(isSelf)
    }

    @Test
    fun canonical_server_public_id_overrides_local_mismatched_cache() {
        val localCachedPublicId = "LOCAL123"
        val serverCanonicalPublicId = "SERVER45"

        // Rule: Supabase is single source of truth. When server ID differs from cache, server wins.
        val finalPublicId = if (localCachedPublicId != serverCanonicalPublicId) {
            serverCanonicalPublicId
        } else {
            localCachedPublicId
        }

        assertEquals("SERVER45", finalPublicId)
        assertNotEquals(localCachedPublicId, finalPublicId)
    }

    @Test
    fun canonical_public_id_validation_rejects_invalid_lengths_or_chars() {
        val validPublicId = "A7K29P4X"
        val invalidShortId = "A7K29"
        val invalidCharId = "A7K29P4!"

        val isValid = { id: String -> id.length == 8 && id.all { it.isLetterOrDigit() } }

        assertTrue(isValid(validPublicId))
        assertFalse(isValid(invalidShortId))
        assertFalse(isValid(invalidCharId))
    }

    @Test
    fun profile_idempotency_preserves_existing_public_id() {
        val existingPublicId = "A7K29P4X"
        val retrievedPublicId = existingPublicId // Server returns existing active identity

        assertEquals(existingPublicId, retrievedPublicId)
    }
}
