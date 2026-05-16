package com.swarapulse.data.repository

import com.swarapulse.data.db.dao.AppointmentDao
import com.swarapulse.data.db.entity.Appointment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AppointmentRepository @Inject constructor(
    private val appointmentDao: AppointmentDao
) {
    fun getAllAppointments(): Flow<List<Appointment>> = appointmentDao.getAllAppointments()

    fun getAppointmentsForPatient(patientId: Long): Flow<List<Appointment>> = appointmentDao.getAppointmentsForPatient(patientId)

    fun getAppointmentById(id: Long): Flow<Appointment?> = appointmentDao.getAppointmentById(id)

    suspend fun insertAppointment(appointment: Appointment): Long = withContext(Dispatchers.IO) {
        appointmentDao.insertAppointment(appointment)
    }

    suspend fun updateAppointment(appointment: Appointment) = withContext(Dispatchers.IO) {
        appointmentDao.updateAppointment(appointment)
    }

    suspend fun deleteAppointment(appointment: Appointment) = withContext(Dispatchers.IO) {
        appointmentDao.deleteAppointment(appointment)
    }
}
