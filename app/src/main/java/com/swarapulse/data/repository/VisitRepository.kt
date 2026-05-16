package com.swarapulse.data.repository

import com.swarapulse.data.db.dao.VisitDao
import com.swarapulse.data.db.entity.Visit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class VisitRepository @Inject constructor(
    private val visitDao: VisitDao
) {
    fun getVisitsForPatient(patientId: Long): Flow<List<Visit>> = visitDao.getVisitsForPatient(patientId)

    fun getVisitById(id: Long): Flow<Visit?> = visitDao.getVisitById(id)

    suspend fun insertVisit(visit: Visit): Long = withContext(Dispatchers.IO) {
        visitDao.insertVisit(visit)
    }

    suspend fun updateVisit(visit: Visit) = withContext(Dispatchers.IO) {
        visitDao.updateVisit(visit)
    }

    suspend fun deleteVisit(visit: Visit) = withContext(Dispatchers.IO) {
        visitDao.deleteVisit(visit)
    }

    fun getRecentVisits(): Flow<List<Visit>> = visitDao.getRecentVisits()

    fun getVisitCount(): Flow<Int> = visitDao.getVisitCount()
}
