package com.lactose.textme.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lactose.textme.core.common.Constants
import com.lactose.textme.data.local.dao.ChatRequestDao
import com.lactose.textme.data.local.dao.ConversationDao
import com.lactose.textme.data.local.dao.IdentityDao
import com.lactose.textme.data.local.dao.MessageDao
import com.lactose.textme.data.local.entity.LocalChatRequestEntity
import com.lactose.textme.data.local.entity.LocalConversationEntity
import com.lactose.textme.data.local.entity.LocalIdentityEntity
import com.lactose.textme.data.local.entity.LocalMessageEntity

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE messages ADD COLUMN isRead INTEGER NOT NULL DEFAULT 1")
    }
}

@Database(
    entities = [
        LocalIdentityEntity::class,
        LocalConversationEntity::class,
        LocalMessageEntity::class,
        LocalChatRequestEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun identityDao(): IdentityDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun chatRequestDao(): ChatRequestDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    Constants.DATABASE_NAME
                )
                .addMigrations(MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
