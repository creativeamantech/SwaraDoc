package com.swarapulse.data.db.dao

import androidx.room.*
import com.swarapulse.data.db.entity.Visit
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitDao {
    @Query("SELECT * FROM visits WHERE patientId = :patientId ORDER BY dateTime DESC")
    fun getVisitsForPatient(patientId: Long): Flow<List<Visit>>

    @Query("SELECT * FROM visits WHERE id = :id")
    fun getVisitById(id: Long): Flow<Visit?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisit(visit: Visit): Long

    @Update
    suspend fun updateVisit(visit: Visit)

    @Delete
    suspend fun deleteVisit(visit: Visit)

    @Query("SELECT * FROM visits ORDER BY dateTime DESC LIMIT 5")
    fun getRecentVisits(): Flow<List<Visit>>

    @Query("SELECT COUNT(*) FROM visits")
    fun getVisitCount(): Flow<Int>
}
