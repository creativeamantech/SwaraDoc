package com.swarapulse.data.repository

import com.swarapulse.data.db.dao.DraftVisitDao
import com.swarapulse.data.db.entity.DraftVisit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DraftVisitRepository @Inject constructor(
    private val draftVisitDao: DraftVisitDao
) {
    fun getDraftVisit(): Flow<DraftVisit?> = draftVisitDao.getDraftVisit()

    suspend fun saveDraft(draft: DraftVisit) = withContext(Dispatchers.IO) {
        draftVisitDao.saveDraft(draft)
    }

    suspend fun clearDrafts() = withContext(Dispatchers.IO) {
        draftVisitDao.clearDrafts()
    }
}
