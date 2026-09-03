package com.example.chargemonitor.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.chargemonitor.data.entity.Phase
import kotlinx.coroutines.flow.Flow

@Dao
interface PhaseDao {

    @Insert
    suspend fun insert(phase: Phase): Long

    @Query("SELECT * FROM phase ORDER BY start_time DESC")
    fun getAllFlow(): Flow<List<Phase>>

    @Query("SELECT * FROM phase ORDER BY start_time DESC")
    suspend fun getAll(): List<Phase>

    @Query("DELETE FROM phase WHERE start_time < :threshold")
    suspend fun deleteOlderThan(threshold: Long)
}
