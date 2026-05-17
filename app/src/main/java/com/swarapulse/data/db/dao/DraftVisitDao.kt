package com.swarapulse.data.db.dao

import androidx.room.*
import com.swarapulse.data.db.entity.DraftVisit
import kotlinx.coroutines.flow.Flow

@Dao
interface DraftVisitDao {
    @Query("SELECT * FROM draft_visits WHERE id = 1")
    fun getDraftVisit(): Flow<DraftVisit?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDraft(draft: DraftVisit)

    @Query("DELETE FROM draft_visits")
    suspend fun clearDrafts()
}
