package com.bblvn.composebaseproject.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.bblvn.composebaseproject.data.database.dao.CreationDao
import com.bblvn.composebaseproject.data.database.entity.CreationEntity

@Database(
    entities = [CreationEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun creationDao(): CreationDao

    companion object {
        private const val NAME = "ai_photo.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                NAME,
            )
                // History is regenerable, disposable data — a destructive migration is a better
                // trade than shipping migration code for a single table.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                .also { instance = it }
        }
    }
}
