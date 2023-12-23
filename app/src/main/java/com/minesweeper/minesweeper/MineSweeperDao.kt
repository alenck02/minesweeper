package com.minesweeper.minesweeper

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface MineSweeperDao {

    @Insert
    suspend fun insert(gameState : MineSweeperEntity): Long

    @Update
    suspend fun update(gameState: MineSweeperEntity)

    @Query("SELECT * FROM mine_sweeper_table WHERE id = :id")
    suspend fun get(id:Int): MineSweeperEntity?

    @Query("DELETE FROM mine_sweeper_table")
    suspend fun deleteAll()
}