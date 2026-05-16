package com.swarapulse.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.swarapulse.data.db.converter.TypeConverters as AppTypeConverters
import com.swarapulse.data.db.dao.AppointmentDao
import com.swarapulse.data.db.dao.PatientDao
import com.swarapulse.data.db.dao.VisitDao
import com.swarapulse.data.db.entity.Appointment
import com.swarapulse.data.db.entity.Patient
import com.swarapulse.data.db.entity.Visit

@Database(
    entities = [
        Patient::class,
        Visit::class,
        Appointment::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(AppTypeConverters::class)
abstract class SwaraPulseDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun visitDao(): VisitDao
    abstract fun appointmentDao(): AppointmentDao
}
