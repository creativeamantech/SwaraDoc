package com.swarapulse.data.db.entity

import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.swarapulse.domain.model.AppointmentStatus
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Serializable
@Entity(
    tableName = "appointments",
    foreignKeys = [ForeignKey(entity = Patient::class,
        parentColumns = ["id"], childColumns = ["patientId"],
        onDelete = ForeignKey.CASCADE)]
)
data class Appointment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val dateTime: Instant,
    val purpose: String? = null,
    val notes: String? = null,
    val status: AppointmentStatus = AppointmentStatus.SCHEDULED,  // SCHEDULED, COMPLETED, CANCELLED
    val createdAt: Instant = Clock.System.now()
)
