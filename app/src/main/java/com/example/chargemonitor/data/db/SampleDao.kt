package com.example.chargemonitor.data.db

import androidx.room.*
import com.example.chargemonitor.data.entity.Sample
import kotlinx.coroutines.flow.Flow

@Dao
interface SampleDao {

    @Insert
    suspend fun insert(sample: Sample): Long

    @Insert
    suspend fun insertAll(samples: List<Sample>)

    @Query("SELECT * FROM sample WHERE session_id = :sessionId ORDER BY ts ASC")
    fun getBySessionFlow(sessionId: Long): Flow<List<Sample>>

    @Query("SELECT * FROM sample WHERE session_id = :sessionId ORDER BY ts ASC")
    suspend fun getBySession(sessionId: Long): List<Sample>

    @Query("SELECT * FROM sample WHERE session_id = :sessionId ORDER BY ts DESC LIMIT 1")
    suspend fun getLatest(sessionId: Long): Sample?

    @Query("SELECT COUNT(*) FROM sample WHERE session_id = :sessionId")
    suspend fun getCount(sessionId: Long): Int

    @Query("DELETE FROM sample WHERE ts < :threshold")
    suspend fun deleteOlderThan(threshold: Long)

    @Query("DELETE FROM sample WHERE session_id = :sessionId")
    suspend fun deleteBySession(sessionId: Long)
}
