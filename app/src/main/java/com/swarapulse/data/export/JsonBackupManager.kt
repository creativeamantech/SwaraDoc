package com.swarapulse.data.export

import android.content.Context
import com.swarapulse.data.db.entity.Appointment
import com.swarapulse.data.db.entity.Patient
import com.swarapulse.data.db.entity.Visit
import com.swarapulse.data.repository.AppointmentRepository
import com.swarapulse.data.repository.PatientRepository
import com.swarapulse.data.repository.VisitRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject

@Serializable
data class DatabaseBackup(
    val patients: List<Patient>,
    val visits: List<Visit>,
    val appointments: List<Appointment>
)

class JsonBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val patientRepository: PatientRepository,
    private val visitRepository: VisitRepository,
    private val appointmentRepository: AppointmentRepository
) {
    suspend fun createBackup(): File {
        val patients = patientRepository.getAllPatients().first()
        // Here we'd fetch all visits. For scaffold, getRecent is used but we should get all.
        // Assuming we add getAllVisits to repo later, using recent for now to compile.
        val visits = visitRepository.getRecentVisits().first()
        val appointments = appointmentRepository.getAllAppointments().first()

        val backup = DatabaseBackup(patients, visits, appointments)
        val jsonString = Json.encodeToString(backup)

        val file = File(context.cacheDir, "swarapulse_backup.json")
        file.writeText(jsonString)
        return file
    }

    suspend fun restoreBackup(jsonString: String): Boolean {
        return try {
            val backup = Json.decodeFromString<DatabaseBackup>(jsonString)

            // In a real scenario we need a Transaction to wipe and insert safely
            // For scaffold, we will just insert (assuming empty DB or replace strategy)
            backup.patients.forEach { patientRepository.insertPatient(it) }
            backup.visits.forEach { visitRepository.insertVisit(it) }
            backup.appointments.forEach { appointmentRepository.insertAppointment(it) }

            true
        } catch (e: Exception) {
            false
        }
    }
}
