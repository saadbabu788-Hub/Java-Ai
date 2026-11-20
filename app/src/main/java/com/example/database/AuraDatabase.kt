package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChatMessageEntity::class,
        AutomationTaskEntity::class,
        ActionLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AuraDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun automationTaskDao(): AutomationTaskDao
    abstract fun actionLogDao(): ActionLogDao

    companion object {
        @Volatile
        private var INSTANCE: AuraDatabase? = null

        fun getInstance(context: Context): AuraDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AuraDatabase::class.java,
                    "aurapilot_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
