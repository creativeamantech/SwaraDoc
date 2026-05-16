package com.swarapulse.data.repository

import com.swarapulse.data.db.dao.PatientDao
import com.swarapulse.data.db.entity.Patient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PatientRepository @Inject constructor(
    private val patientDao: PatientDao
) {
    fun getAllPatients(): Flow<List<Patient>> = patientDao.getAllPatients()

    fun getPatientById(id: Long): Flow<Patient?> = patientDao.getPatientById(id)

    suspend fun insertPatient(patient: Patient): Long = withContext(Dispatchers.IO) {
        patientDao.insertPatient(patient)
    }

    suspend fun updatePatient(patient: Patient) = withContext(Dispatchers.IO) {
        patientDao.updatePatient(patient)
    }

    suspend fun deletePatient(patient: Patient) = withContext(Dispatchers.IO) {
        patientDao.deletePatient(patient)
    }

    fun searchPatients(query: String): Flow<List<Patient>> = patientDao.searchPatients(query)
}
