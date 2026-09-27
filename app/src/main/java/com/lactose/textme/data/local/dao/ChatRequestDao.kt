package com.lactose.textme.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lactose.textme.data.local.entity.LocalChatRequestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatRequestDao {

    @Query("SELECT * FROM chat_requests WHERE status = 'pending' ORDER BY createdAt DESC")
    fun getPendingRequestsFlow(): Flow<List<LocalChatRequestEntity>>

    @Query("SELECT * FROM chat_requests WHERE receiverIdentityId = :myIdentityId AND status = 'pending' ORDER BY createdAt DESC")
    fun getPendingIncomingRequestsFlow(myIdentityId: String): Flow<List<LocalChatRequestEntity>>

    @Query("SELECT * FROM chat_requests WHERE senderIdentityId = :myIdentityId AND status = 'pending' ORDER BY createdAt DESC")
    fun getPendingOutgoingRequestsFlow(myIdentityId: String): Flow<List<LocalChatRequestEntity>>

    @Query("SELECT * FROM chat_requests WHERE id = :id LIMIT 1")
    fun getRequestById(id: String): LocalChatRequestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRequest(request: LocalChatRequestEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRequests(requests: List<LocalChatRequestEntity>)

    @Query("UPDATE chat_requests SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    fun updateStatus(id: String, status: String, updatedAt: Long): Int

    @Query("UPDATE chat_requests SET status = :status, updatedAt = :updatedAt WHERE receiverIdentityId = :myIdentityId AND status = 'pending' AND id NOT IN (:activeRemoteIds)")
    fun markMissingRequestsCancelled(myIdentityId: String, activeRemoteIds: List<String>, status: String, updatedAt: Long): Int

    @Query("UPDATE chat_requests SET status = :status, updatedAt = :updatedAt WHERE receiverIdentityId = :myIdentityId AND status = 'pending'")
    fun markAllPendingIncomingRequestsCancelled(myIdentityId: String, status: String, updatedAt: Long): Int

    @Query("UPDATE chat_requests SET status = :status, updatedAt = :updatedAt WHERE (senderIdentityId = :identityId OR receiverIdentityId = :identityId) AND status = 'pending'")
    fun markAllPendingRequestsCancelledForIdentity(identityId: String, status: String, updatedAt: Long): Int

    @Query("DELETE FROM chat_requests WHERE id = :id")
    fun deleteRequest(id: String): Int

    @Query("DELETE FROM chat_requests")
    fun clearAll(): Int
}
