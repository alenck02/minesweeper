package com.minesweeper.minesweeper

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mine_sweeper_table")
data class MineSweeperEntity(
    @PrimaryKey(autoGenerate = true) // 각 게임 세션을 식별하는 유일한 키
    val id: Int,

    @ColumnInfo(name = "difficulty") // 게임 난이도
    val difficulty: Int,

    @ColumnInfo(name = "elapsedTime") // 진행된 시간(밀리초)
    val elapsedTime: Long,

    @ColumnInfo(name = "MineCount") // 지뢰 개수
    val mineCount: Int,

    @ColumnInfo(name = "nonMineButtonClickedCount") // 지뢰 아닌 버튼 클릭 수
    val nonMineButtonClickedCount: Int,

    @ColumnInfo(name = "nonMineButtonCount") // 지뢰 아닌 버튼 개수
    val nonMineButtonCount: Int,

    @ColumnInfo(name = "minePositions") // 지뢰 위치
    val minePositions: String,

    @ColumnInfo(name = "cellOpened") // 셀 열림 여부
    val cellOpened: String,

    @ColumnInfo(name = "cellFlagged") // 셀 깃발 여부
    val cellFlagged: String,

    @ColumnInfo(name = "cellMineCountNearby") // 주변 지뢰 개수
    val cellMineCountNearby: String
)