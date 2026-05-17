package com.swarapulse.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Entity(tableName = "draft_visits")
data class DraftVisit(
    @PrimaryKey val id: Long = 1, // Single draft record
    val patientId: Long? = null,
    val draftDataJson: String,
    val lastSavedAt: Instant = Clock.System.now()
)
