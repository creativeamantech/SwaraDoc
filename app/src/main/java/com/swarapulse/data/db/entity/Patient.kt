package com.swarapulse.data.db.entity

import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.PrimaryKey
import com.swarapulse.domain.model.Gender
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Serializable
@Entity(tableName = "patients")
data class Patient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val age: Int,
    val gender: Gender,               // enum: MALE, FEMALE, OTHER
    val mobile: String,
    val email: String? = null,
    val address: String? = null,
    val occupation: String? = null,
    val bloodGroup: String? = null,
    val category: List<String> = emptyList(),   // TypeConverter -> JSON
    val medicalHistory: String? = null,          // rich text as HTML/MD
    val emergencyContact: String? = null,
    val emergencyContactName: String? = null,
    val isActive: Boolean = true,
    val isProvisional: Boolean = false,
    val profileImagePath: String? = null,        // local file path
    val lastVisitDate: Instant? = null,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now()
)
