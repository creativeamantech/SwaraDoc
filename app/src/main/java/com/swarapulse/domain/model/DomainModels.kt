package com.swarapulse.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

enum class Nadi { IDA, PINGALA, SUSHUMNA, SHIFT_IDA_PINGALA, SHIFT_PINGALA_IDA, OTHER }
enum class Element { AIR, FIRE, SPACE, EARTH, WATER, OTHER }
enum class Mandala { IDA, PINGALA, OTHER }
enum class Paksha { SHUKLA, KRISHNA }
enum class PatientSide { RIGHT, LEFT, FRONT }
enum class Gender { MALE, FEMALE, OTHER }
enum class AppointmentStatus { SCHEDULED, COMPLETED, CANCELLED }
enum class FieldType { TEXT, NUMBER, DATE }

@Serializable
data class MediaEntry(val filePath: String, val dateMillis: Long)

@Serializable
data class CustomField(val id: String, val label: String, val type: FieldType, val value: String)
