package com.sueleyman.suelostodo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TaskList::class, ListGroup::class, Task::class, Attachment::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): TaskDao

    companion object {
        /** v2: list groups (folders) */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lists ADD COLUMN groupId TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `list_groups` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `parentId` TEXT, " +
                        "`sortOrder` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, " +
                        "`expanded` INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_list_groups_parentId` ON `list_groups` (`parentId`)")
            }
        }

        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "suelostodo.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
