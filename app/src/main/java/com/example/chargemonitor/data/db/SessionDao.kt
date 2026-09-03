package com.example.chargemonitor.data.db

import androidx.room.*
import com.example.chargemonitor.data.entity.Session
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: Session): Long

    @Update
    suspend fun update(session: Session)

    @Query("SELECT * FROM session ORDER BY start_time DESC")
    fun getAllFlow(): Flow<List<Session>>

    @Query("SELECT * FROM session ORDER BY start_time DESC")
    suspend fun getAll(): List<Session>

    @Query("SELECT * FROM session WHERE id = :id")
    suspend fun getById(id: Long): Session?

    @Query("SELECT * FROM session WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<Session?>

    @Query("SELECT * FROM session WHERE end_time IS NULL ORDER BY start_time DESC LIMIT 1")
    suspend fun getActiveSession(): Session?

    @Query("DELETE FROM session WHERE id = :id")
    suspend fun delete(id: Long)
}
