package com.lactose.textme.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lactose.textme.data.local.entity.LocalMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun getMessagesForConversationFlow(conversationId: String): Flow<List<LocalMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMessage(message: LocalMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMessages(messages: List<LocalMessageEntity>)

    @Query("SELECT id FROM messages WHERE conversationId = :conversationId")
    fun getExistingMessageIds(conversationId: String): List<String>

    @Query("UPDATE messages SET isRead = 1 WHERE conversationId = :conversationId AND isRead = 0")
    fun markMessagesAsRead(conversationId: String): Int

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND isRead = 0")
    fun getUnreadCount(conversationId: String): Int

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    fun deleteMessagesForConversation(conversationId: String): Int

    @Query("DELETE FROM messages")
    fun clearAll(): Int
}
