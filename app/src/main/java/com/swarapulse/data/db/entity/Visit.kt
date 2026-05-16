package com.swarapulse.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.swarapulse.domain.model.CustomField
import com.swarapulse.domain.model.Element
import com.swarapulse.domain.model.Mandala
import com.swarapulse.domain.model.MediaEntry
import com.swarapulse.domain.model.Nadi
import com.swarapulse.domain.model.Paksha
import com.swarapulse.domain.model.PatientSide
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Entity(
    tableName = "visits",
    foreignKeys = [ForeignKey(entity = Patient::class,
        parentColumns = ["id"], childColumns = ["patientId"],
        onDelete = ForeignKey.CASCADE)]
)
data class Visit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val dateTime: Instant,

    // Yogic Evaluation
    val patientMandala: Mandala,        // IDA, PINGALA, OTHER
    val patientNadi: Nadi,              // IDA, PINGALA, SUSHUMNA, SHIFT_IDA_PING, SHIFT_PING_IDA, OTHER
    val patientElement: Element,        // AIR, FIRE, SPACE, EARTH, WATER, OTHER
    val patientSide: PatientSide,       // RIGHT, LEFT, FRONT

    // Doctor Assessment
    val doctorNadi: String,
    val doctorElementBefore: String,
    val doctorElementAfter: String? = null,

    // Lunar / Temporal
    val paksha: Paksha,                 // SHUKLA, KRISHNA
    val tithi: String,                  // "1"-"15" or "Other"
    val tithiElement: Element,

    // Clinical
    val chiefComplaint: String = "",    // HTML/MD
    val prescription: String = "",      // HTML/MD
    val diseaseCategories: List<String> = emptyList(),

    // Media: stored as JSON list of MediaEntry
    val mediaEntries: List<MediaEntry> = emptyList(),

    // Custom Fields: JSON list
    val customFields: List<CustomField> = emptyList(),

    val followupDate: Instant? = null,
    val editedAt: Instant? = null,
    val createdAt: Instant = Clock.System.now()
)
