package com.minesweeper.minesweeper

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mine_sweeper_table")
data class MineSweeperEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int,

    @ColumnInfo(name = "difficulty")
    val difficulty: Int,

    @ColumnInfo(name = "elapsedTime")
    val elapsedTime: Long,

    @ColumnInfo(name = "MineCount")
    val mineCount: Int,

    @ColumnInfo(name = "nonMineButtonClickedCount")
    val nonMineButtonClickedCount: Int,

    @ColumnInfo(name = "nonMineButtonCount")
    val nonMineButtonCount: Int,

    @ColumnInfo(name = "minePositions")
    val minePositions: String,

    @ColumnInfo(name = "cellOpened")
    val cellOpened: String,

    @ColumnInfo(name = "cellFlagged")
    val cellFlagged: String,

    @ColumnInfo(name = "cellMineCountNearby")
    val cellMineCountNearby: String
)