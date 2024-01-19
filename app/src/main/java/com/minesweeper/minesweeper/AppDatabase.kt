package com.minesweeper.minesweeper

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [MineSweeperEntity::class], version = 3)
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()

                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
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
                database.execSQL("""
            INSERT INTO new_mine_sweeper_table (id, difficulty, elapsedTime, MineCount, nonMineButtonClickedCount,
            nonMineButtonCount, minePositions, cellOpened, cellFlagged, cellMineCountNearby)
            SELECT id, 1, elapsedTime, MineCount, nonMineButtonClickedCount, nonMineButtonCount, minePositions, cellOpened,
            cellFlagged, cellMineCountNearby FROM mine_sweeper_table
        """.trimIndent())
                database.execSQL("DROP TABLE mine_sweeper_table")
                database.execSQL("ALTER TABLE new_mine_sweeper_table RENAME TO mine_sweeper_table")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
            CREATE TABLE mine_sweeper_table2 (
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
                database.execSQL("""
            INSERT INTO mine_sweeper_table2 (id, elapsedTime, MineCount, nonMineButtonClickedCount,
            nonMineButtonCount, minePositions, cellOpened, cellFlagged, cellMineCountNearby)
            SELECT id, elapsedTime, MineCount, nonMineButtonClickedCount, nonMineButtonCount, minePositions, cellOpened,
            cellFlagged, cellMineCountNearby FROM mine_sweeper_table
        """.trimIndent())
                database.execSQL("DROP TABLE mine_sweeper_table")
                database.execSQL("ALTER TABLE mine_sweeper_table2 RENAME TO mine_sweeper_table")
            }
        }
    }
}