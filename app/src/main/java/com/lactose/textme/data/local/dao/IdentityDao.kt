package com.lactose.textme.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lactose.textme.data.local.entity.LocalIdentityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IdentityDao {

    @Query("SELECT * FROM identities WHERE status = 'active' ORDER BY createdAt DESC LIMIT 1")
    fun getActiveIdentityFlow(): Flow<LocalIdentityEntity?>

    @Query("SELECT * FROM identities WHERE status = 'active' ORDER BY createdAt DESC LIMIT 1")
    fun getActiveIdentity(): LocalIdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertIdentity(identity: LocalIdentityEntity): Long

    @Query("UPDATE identities SET status = :status WHERE id = :id")
    fun updateStatus(id: String, status: String): Int

    @Query("UPDATE identities SET autoRotationEnabled = :enabled WHERE id = :id")
    fun updateAutoRotation(id: String, enabled: Boolean): Int

    @Query("UPDATE identities SET autoRotationEnabled = :enabled, expiresAt = :expiresAt WHERE id = :id")
    fun updateAutoRotationWithExpiration(id: String, enabled: Boolean, expiresAt: Long): Int

    @Query("DELETE FROM identities")
    fun clearAll(): Int
}
