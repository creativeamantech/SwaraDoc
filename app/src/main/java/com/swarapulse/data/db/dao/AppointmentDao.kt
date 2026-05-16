package com.swarapulse.data.db.dao

import androidx.room.*
import com.swarapulse.data.db.entity.Appointment
import kotlinx.coroutines.flow.Flow

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments ORDER BY dateTime ASC")
    fun getAllAppointments(): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE patientId = :patientId ORDER BY dateTime ASC")
    fun getAppointmentsForPatient(patientId: Long): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE id = :id")
    fun getAppointmentById(id: Long): Flow<Appointment?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppointment(appointment: Appointment): Long

    @Update
    suspend fun updateAppointment(appointment: Appointment)

    @Delete
    suspend fun deleteAppointment(appointment: Appointment)
}
