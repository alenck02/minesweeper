package com.minesweeper.minesweeper

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [MineSweeperEntity::class], version = 2)
abstract class AppDatabase : RoomDatabase() {

    abstract fun mineSweeperDao(): MineSweeperDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()

                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create the new table
                database.execSQL("""
            CREATE TABLE new_mine_sweeper_table (
                id INTEGER PRIMARY KEY NOT NULL,
                difficulty INTEGER NOT NULL DEFAULT 1,
                elapsedTime INTEGER NOT NULL,
                MineCount INTEGER NOT NULL,
                nonMineButtonClickedCount INTEGER NOT NULL,
                nonMineButtonCount INTEGER NOT NULL,
                minePositions TEXT NOT NULL,
                cellOpened TEXT NOT NULL,
                cellFlagged TEXT NOT NULL,
                cellMineCountNearby TEXT NOT NULL
            )
        """.trimIndent())
                // Copy the data
                database.execSQL("""
            INSERT INTO new_mine_sweeper_table (id, difficulty, elapsedTime, MineCount, nonMineButtonClickedCount, nonMineButtonCount, minePositions, cellOpened, cellFlagged, cellMineCountNearby)
            SELECT id, 1, elapsedTime, MineCount, nonMineButtonClickedCount, nonMineButtonCount, minePositions, cellOpened, cellFlagged, cellMineCountNearby FROM mine_sweeper_table
        """.trimIndent())
                // Remove the old table
                database.execSQL("DROP TABLE mine_sweeper_table")
                // Change the table name to the correct one
                database.execSQL("ALTER TABLE new_mine_sweeper_table RENAME TO mine_sweeper_table")
            }
        }
    }
}