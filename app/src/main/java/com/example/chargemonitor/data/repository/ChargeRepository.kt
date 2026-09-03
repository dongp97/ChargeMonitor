package com.example.chargemonitor.data.repository

import com.example.chargemonitor.data.db.SampleDao
import com.example.chargemonitor.data.db.SessionDao
import com.example.chargemonitor.data.entity.Sample
import com.example.chargemonitor.data.entity.Session
import kotlinx.coroutines.flow.Flow

class ChargeRepository(
    private val sessionDao: SessionDao,
    private val sampleDao: SampleDao
) {

    // ==================== Session ====================

    suspend fun insertSession(session: Session): Long = sessionDao.insert(session)

    suspend fun updateSession(session: Session) = sessionDao.update(session)

    fun getAllSessionsFlow(): Flow<List<Session>> = sessionDao.getAllFlow()

    suspend fun getAllSessions(): List<Session> = sessionDao.getAll()

    suspend fun getSessionById(id: Long): Session? = sessionDao.getById(id)

    fun getSessionByIdFlow(id: Long): Flow<Session?> = sessionDao.getByIdFlow(id)

    suspend fun getActiveSession(): Session? = sessionDao.getActiveSession()

    suspend fun deleteSession(id: Long) = sessionDao.delete(id)

    // ==================== Sample ====================

    suspend fun insertSample(sample: Sample): Long = sampleDao.insert(sample)

    suspend fun insertSamples(samples: List<Sample>) = sampleDao.insertAll(samples)

    fun getSamplesBySessionFlow(sessionId: Long): Flow<List<Sample>> =
        sampleDao.getBySessionFlow(sessionId)

    suspend fun getSamplesBySession(sessionId: Long): List<Sample> =
        sampleDao.getBySession(sessionId)

    suspend fun getLatestSample(sessionId: Long): Sample? = sampleDao.getLatest(sessionId)

    suspend fun getSampleCount(sessionId: Long): Int = sampleDao.getCount(sessionId)

    suspend fun deleteSamplesOlderThan(threshold: Long) = sampleDao.deleteOlderThan(threshold)

    suspend fun deleteSamplesBySession(sessionId: Long) = sampleDao.deleteBySession(sessionId)
}
