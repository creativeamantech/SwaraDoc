package com.swarapulse.data.db.converter

import androidx.room.TypeConverter
import com.swarapulse.domain.model.*
import kotlinx.datetime.Instant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TypeConverters {

    @TypeConverter
    fun fromInstant(instant: Instant?): Long? {
        return instant?.toEpochMilliseconds()
    }

    @TypeConverter
    fun toInstant(millis: Long?): Instant? {
        return millis?.let { Instant.fromEpochMilliseconds(it) }
    }

    @TypeConverter
    fun fromStringList(list: List<String>?): String {
        return Json.encodeToString(list ?: emptyList())
    }

    @TypeConverter
    fun toStringList(data: String): List<String> {
        return try {
            Json.decodeFromString(data)
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromMediaEntryList(list: List<MediaEntry>?): String {
        return Json.encodeToString(list ?: emptyList())
    }

    @TypeConverter
    fun toMediaEntryList(data: String): List<MediaEntry> {
        return try {
            Json.decodeFromString(data)
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromCustomFieldList(list: List<CustomField>?): String {
        return Json.encodeToString(list ?: emptyList())
    }

    @TypeConverter
    fun toCustomFieldList(data: String): List<CustomField> {
        return try {
            Json.decodeFromString(data)
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromGender(gender: Gender): String = gender.name

    @TypeConverter
    fun toGender(name: String): Gender = Gender.valueOf(name)

    @TypeConverter
    fun fromMandala(mandala: Mandala): String = mandala.name

    @TypeConverter
    fun toMandala(name: String): Mandala = Mandala.valueOf(name)

    @TypeConverter
    fun fromNadi(nadi: Nadi): String = nadi.name

    @TypeConverter
    fun toNadi(name: String): Nadi = Nadi.valueOf(name)

    @TypeConverter
    fun fromElement(element: Element): String = element.name

    @TypeConverter
    fun toElement(name: String): Element = Element.valueOf(name)

    @TypeConverter
    fun fromPatientSide(patientSide: PatientSide): String = patientSide.name

    @TypeConverter
    fun toPatientSide(name: String): PatientSide = PatientSide.valueOf(name)

    @TypeConverter
    fun fromPaksha(paksha: Paksha): String = paksha.name

    @TypeConverter
    fun toPaksha(name: String): Paksha = Paksha.valueOf(name)

    @TypeConverter
    fun fromAppointmentStatus(status: AppointmentStatus): String = status.name

    @TypeConverter
    fun toAppointmentStatus(name: String): AppointmentStatus = AppointmentStatus.valueOf(name)
}
