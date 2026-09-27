package com.lactose.textme.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lactose.textme.data.local.entity.LocalConversationEntity
import kotlinx.coroutines.flow.Flow

data class LocalConversationWithLastMessage(
    @Embedded val conversation: LocalConversationEntity,
    val lastMessageTimestamp: Long?,
    val unreadCount: Int = 0
)

@Dao
interface ConversationDao {

    @Query("""
        SELECT c.*, 
               (SELECT MAX(m.createdAt) FROM messages m WHERE m.conversationId = c.id) AS lastMessageTimestamp,
               (SELECT COUNT(*) FROM messages m WHERE m.conversationId = c.id AND m.isRead = 0) AS unreadCount
        FROM conversations c
        WHERE c.status != 'deleted'
        ORDER BY c.isPinned DESC, COALESCE((SELECT MAX(m.createdAt) FROM messages m WHERE m.conversationId = c.id), c.createdAt) DESC
    """)
    fun getActiveConversationsFlow(): Flow<List<LocalConversationWithLastMessage>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    fun getConversationById(id: String): LocalConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    fun getConversationByIdFlow(id: String): Flow<LocalConversationEntity?>

    @Query("UPDATE conversations SET isPinned = :isPinned WHERE id = :id")
    fun updatePinned(id: String, isPinned: Boolean): Int

    @Query("UPDATE conversations SET status = 'expired', terminatedReason = :reason, terminatedBy = :terminatedBy WHERE (participantAId = :identityId OR participantBId = :identityId) AND status = 'active'")
    fun markConversationsTerminatedByIdentity(identityId: String, reason: String, terminatedBy: String): Int

    @Query("UPDATE conversations SET status = 'expired', terminatedReason = :reason, terminatedBy = :terminatedBy WHERE id = :conversationId")
    fun markConversationTerminated(conversationId: String, reason: String, terminatedBy: String?): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertConversation(conversation: LocalConversationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertConversations(conversations: List<LocalConversationEntity>)

    @Query("SELECT * FROM conversations")
    fun getAllConversations(): List<LocalConversationEntity>

    @Query("SELECT id FROM conversations WHERE status = 'deleted'")
    fun getDeletedConversationIds(): List<String>

    @Query("UPDATE conversations SET status = 'deleted' WHERE id = :id")
    fun markConversationDeleted(id: String): Int

    @Query("DELETE FROM conversations WHERE id = :id")
    fun deleteConversation(id: String): Int

    @Query("DELETE FROM conversations")
    fun clearAll(): Int
}
