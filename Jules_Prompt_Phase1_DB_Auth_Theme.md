# Jules Prompt — SwaraPulse Phase 1
## Layers: Database · Auth · Theme & Design System

> Feed this entire file to Jules as a single task.  
> The project already has: Kotlin + Compose + Hilt + Room + kotlinx-datetime + DataStore + Navigation Compose dependencies configured, and empty placeholder `.kt` files for each package.

---

## CONTEXT

You are implementing **SwaraPulse**, a Swara Yoga patient management Android app.  
Architecture: **MVVM + Clean Architecture**, UI in **Jetpack Compose + Material 3**.  
Package root: `com.swarapulse`

Do NOT create any UI screens beyond what is listed here. Do NOT add placeholder TODOs inside function bodies — write complete, working Kotlin code for every file.

---

## TASK 1 — ENUMS & DOMAIN MODELS
### File: `data/db/entity/Enums.kt`

Create all enums used across the database:

```kotlin
package com.swarapulse.data.db.entity

enum class Gender { MALE, FEMALE, OTHER }

enum class Nadi {
    IDA, PINGALA, SUSHUMNA,
    SHIFT_IDA_TO_PINGALA, SHIFT_PINGALA_TO_IDA, OTHER;

    fun displayName(): String = when (this) {
        IDA -> "Ida"
        PINGALA -> "Pingala"
        SUSHUMNA -> "Sushumna"
        SHIFT_IDA_TO_PINGALA -> "Shifting Ida → Pingala"
        SHIFT_PINGALA_TO_IDA -> "Shifting Pingala → Ida"
        OTHER -> "Other"
    }
}

enum class Element {
    AIR, FIRE, SPACE, EARTH, WATER, OTHER;

    fun displayName(): String = when (this) {
        AIR -> "Air"; FIRE -> "Fire"; SPACE -> "Space"
        EARTH -> "Earth"; WATER -> "Water"; OTHER -> "Other"
    }

    fun emoji(): String = when (this) {
        AIR -> "💨"; FIRE -> "🔥"; SPACE -> "✨"
        EARTH -> "🌍"; WATER -> "💧"; OTHER -> "○"
    }
}

enum class Mandala { IDA, PINGALA, OTHER }

enum class Paksha {
    SHUKLA, KRISHNA;
    fun displayName() = if (this == SHUKLA) "Shukla (Waxing)" else "Krishna (Waning)"
}

enum class PatientSide { RIGHT, LEFT, FRONT }

enum class AppointmentStatus { SCHEDULED, COMPLETED, CANCELLED }

enum class FieldType { TEXT, NUMBER, DATE }
```

---

## TASK 2 — SUPPORTING DATA CLASSES
### File: `data/db/entity/SupportingModels.kt`

```kotlin
package com.swarapulse.data.db.entity

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class MediaEntry(
    val filePath: String,
    val dateTaken: Long  // epoch milliseconds
) {
    fun instant(): Instant = Instant.fromEpochMilliseconds(dateTaken)
}

@Serializable
data class CustomField(
    val id: String,
    val label: String,
    val type: FieldType,
    val value: String
)
```

---

## TASK 3 — ROOM ENTITIES
### File: `data/db/entity/Patient.kt`

```kotlin
package com.swarapulse.data.db.entity

import androidx.room.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Entity(tableName = "patients")
data class Patient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val age: Int,
    val gender: Gender,
    val mobile: String,
    val email: String? = null,
    val address: String? = null,
    val occupation: String? = null,
    val bloodGroup: String? = null,
    val category: List<String> = emptyList(),
    val medicalHistory: String? = null,
    val emergencyContact: String? = null,
    val emergencyContactName: String? = null,
    val isActive: Boolean = true,
    val isProvisional: Boolean = false,
    val profileImagePath: String? = null,
    val lastVisitDate: Instant? = null,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now()
)
```

### File: `data/db/entity/Visit.kt`

```kotlin
package com.swarapulse.data.db.entity

import androidx.room.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Entity(
    tableName = "visits",
    foreignKeys = [ForeignKey(
        entity = Patient::class,
        parentColumns = ["id"],
        childColumns = ["patientId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("patientId")]
)
data class Visit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val dateTime: Instant,

    val patientMandala: Mandala,
    val patientNadi: Nadi,
    val patientElement: Element,
    val patientSide: PatientSide,

    val doctorNadi: String,
    val doctorElementBefore: String,
    val doctorElementAfter: String? = null,

    val paksha: Paksha,
    val tithi: String,
    val tithiElement: Element,

    val chiefComplaint: String = "",
    val prescription: String = "",
    val diseaseCategories: List<String> = emptyList(),
    val mediaEntries: List<MediaEntry> = emptyList(),
    val customFields: List<CustomField> = emptyList(),

    val followupDate: Instant? = null,
    val editedAt: Instant? = null,
    val createdAt: Instant = Clock.System.now()
)
```

### File: `data/db/entity/Appointment.kt`

```kotlin
package com.swarapulse.data.db.entity

import androidx.room.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Entity(
    tableName = "appointments",
    foreignKeys = [ForeignKey(
        entity = Patient::class,
        parentColumns = ["id"],
        childColumns = ["patientId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("patientId")]
)
data class Appointment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val dateTime: Instant,
    val purpose: String? = null,
    val notes: String? = null,
    val status: AppointmentStatus = AppointmentStatus.SCHEDULED,
    val createdAt: Instant = Clock.System.now()
)
```

---

## TASK 4 — TYPE CONVERTERS
### File: `data/db/converter/Converters.kt`

Write a single `@ProvidedTypeConverter` class annotated with `@TypeConverters` covering:

- `Instant` ↔ `Long` (use `toLong()` / `fromEpochMilliseconds`)
- `List<String>` ↔ `String` (use `kotlinx.serialization.json.Json`)
- `List<MediaEntry>` ↔ `String`
- `List<CustomField>` ↔ `String`
- All enums (`Gender`, `Nadi`, `Element`, `Mandala`, `Paksha`, `PatientSide`, `AppointmentStatus`, `FieldType`) ↔ `String` using `name` / `valueOf`

Use `kotlinx.serialization.json.Json { ignoreUnknownKeys = true }` as the Json instance.  
Annotate each converter pair with `@TypeConverter`.

---

## TASK 5 — FTS ENTITIES
### File: `data/db/entity/PatientFts.kt`

```kotlin
package com.swarapulse.data.db.entity

import androidx.room.Entity
import androidx.room.Fts4

// FTS4 shadow table for patient full-text search.
// Keep in sync with Patient via triggers defined in the DB callback.
@Fts4(contentEntity = Patient::class)
@Entity(tableName = "patients_fts")
data class PatientFts(
    val name: String,
    val mobile: String,
    val email: String,
    val address: String,
    val occupation: String
)
```

### File: `data/db/entity/VisitFts.kt`

```kotlin
package com.swarapulse.data.db.entity

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = Visit::class)
@Entity(tableName = "visits_fts")
data class VisitFts(
    val chiefComplaint: String,
    val prescription: String,
    val doctorNadi: String,
    val doctorElementBefore: String
)
```

---

## TASK 6 — DAOs
### File: `data/db/dao/PatientDao.kt`

Write a `@Dao` interface with:

```kotlin
// Queries — all return Flow<> for reactive updates
@Query("SELECT * FROM patients ORDER BY updatedAt DESC")
fun getAllPatients(): Flow<List<Patient>>

@Query("SELECT * FROM patients WHERE isActive = 1 ORDER BY name ASC")
fun getActivePatients(): Flow<List<Patient>>

@Query("SELECT * FROM patients WHERE id = :id")
fun getPatientById(id: Long): Flow<Patient?>

// FTS search — returns matching patient IDs then join
@Query("""
    SELECT patients.* FROM patients
    INNER JOIN patients_fts ON patients.rowid = patients_fts.rowid
    WHERE patients_fts MATCH :query
    ORDER BY patients.updatedAt DESC
""")
fun searchPatients(query: String): Flow<List<Patient>>

@Query("SELECT COUNT(*) FROM patients WHERE isProvisional = 0")
fun getActivePatientCount(): Flow<Int>

@Query("SELECT * FROM patients WHERE id = :id")
suspend fun getPatientByIdOnce(id: Long): Patient?

// Mutations
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertPatient(patient: Patient): Long

@Update
suspend fun updatePatient(patient: Patient)

@Delete
suspend fun deletePatient(patient: Patient)

@Query("UPDATE patients SET lastVisitDate = :date, updatedAt = :now WHERE id = :id")
suspend fun updateLastVisitDate(id: Long, date: Long, now: Long)
```

### File: `data/db/dao/VisitDao.kt`

Write a `@Dao` interface with:

```kotlin
@Query("SELECT * FROM visits WHERE patientId = :patientId ORDER BY dateTime DESC")
fun getVisitsForPatient(patientId: Long): Flow<List<Visit>>

@Query("SELECT * FROM visits WHERE patientId = :patientId ORDER BY dateTime ASC LIMIT 1")
suspend fun getFirstVisit(patientId: Long): Visit?

@Query("SELECT COUNT(*) FROM visits")
fun getTotalVisitCount(): Flow<Int>

@Query("SELECT COUNT(*) FROM visits WHERE dateTime >= :monthStart")
fun getVisitsThisMonth(monthStart: Long): Flow<Int>

// Returns last 5 visits across all patients joined with patient name
@Query("""
    SELECT v.*, p.name as patientName FROM visits v
    INNER JOIN patients p ON v.patientId = p.id
    ORDER BY v.dateTime DESC LIMIT 5
""")
fun getRecentVisits(): Flow<List<VisitWithPatientName>>

@Query("SELECT * FROM visits WHERE followupDate BETWEEN :from AND :to ORDER BY followupDate ASC")
fun getFollowupsBetween(from: Long, to: Long): Flow<List<Visit>>

@Query("SELECT * FROM visits WHERE patientId = :patientId ORDER BY dateTime DESC")
suspend fun getVisitsForPatientOnce(patientId: Long): List<Visit>

@Query("SELECT * FROM visits ORDER BY dateTime DESC")
suspend fun getAllVisitsOnce(): List<Visit>

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertVisit(visit: Visit): Long

@Update
suspend fun updateVisit(visit: Visit)

@Delete
suspend fun deleteVisit(visit: Visit)
```

Also define this helper data class in the same file (outside the interface):
```kotlin
data class VisitWithPatientName(
    @Embedded val visit: Visit,
    val patientName: String
)
```

### File: `data/db/dao/AppointmentDao.kt`

Write a `@Dao` interface with:

```kotlin
@Query("SELECT * FROM appointments WHERE status = 'SCHEDULED' ORDER BY dateTime ASC")
fun getUpcomingAppointments(): Flow<List<Appointment>>

@Query("""
    SELECT * FROM appointments
    WHERE dateTime >= :dayStart AND dateTime < :dayEnd
    AND status = 'SCHEDULED'
    ORDER BY dateTime ASC
""")
fun getTodaysAppointments(dayStart: Long, dayEnd: Long): Flow<List<Appointment>>

@Query("SELECT * FROM appointments WHERE patientId = :patientId ORDER BY dateTime DESC")
fun getAppointmentsForPatient(patientId: Long): Flow<List<Appointment>>

@Query("SELECT * FROM appointments WHERE status IN ('COMPLETED','CANCELLED') ORDER BY dateTime DESC")
fun getPastAppointments(): Flow<List<Appointment>>

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertAppointment(appointment: Appointment): Long

@Update
suspend fun updateAppointment(appointment: Appointment)

@Delete
suspend fun deleteAppointment(appointment: Appointment)

@Query("UPDATE appointments SET status = :status WHERE id = :id")
suspend fun updateStatus(id: Long, status: String)
```

---

## TASK 7 — ROOM DATABASE
### File: `data/db/SwaraPulseDatabase.kt`

```kotlin
package com.swarapulse.data.db

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.swarapulse.data.db.converter.Converters
import com.swarapulse.data.db.dao.*
import com.swarapulse.data.db.entity.*

@Database(
    entities = [
        Patient::class,
        Visit::class,
        Appointment::class,
        PatientFts::class,
        VisitFts::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class SwaraPulseDatabase : RoomDatabase() {

    abstract fun patientDao(): PatientDao
    abstract fun visitDao(): VisitDao
    abstract fun appointmentDao(): AppointmentDao

    companion object {
        const val DATABASE_NAME = "swarapulse.db"

        // Callback that creates FTS sync triggers after DB is created
        val FTS_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Triggers to keep patients_fts in sync
                db.execSQL("""
                    CREATE TRIGGER patients_fts_insert AFTER INSERT ON patients BEGIN
                        INSERT INTO patients_fts(rowid, name, mobile, email, address, occupation)
                        VALUES (new.rowid, new.name, COALESCE(new.mobile,''), 
                                COALESCE(new.email,''), COALESCE(new.address,''),
                                COALESCE(new.occupation,''));
                    END
                """)
                db.execSQL("""
                    CREATE TRIGGER patients_fts_update AFTER UPDATE ON patients BEGIN
                        DELETE FROM patients_fts WHERE rowid = old.rowid;
                        INSERT INTO patients_fts(rowid, name, mobile, email, address, occupation)
                        VALUES (new.rowid, new.name, COALESCE(new.mobile,''),
                                COALESCE(new.email,''), COALESCE(new.address,''),
                                COALESCE(new.occupation,''));
                    END
                """)
                db.execSQL("""
                    CREATE TRIGGER patients_fts_delete AFTER DELETE ON patients BEGIN
                        DELETE FROM patients_fts WHERE rowid = old.rowid;
                    END
                """)
                // Triggers to keep visits_fts in sync
                db.execSQL("""
                    CREATE TRIGGER visits_fts_insert AFTER INSERT ON visits BEGIN
                        INSERT INTO visits_fts(rowid, chiefComplaint, prescription, doctorNadi, doctorElementBefore)
                        VALUES (new.rowid, COALESCE(new.chiefComplaint,''), COALESCE(new.prescription,''),
                                COALESCE(new.doctorNadi,''), COALESCE(new.doctorElementBefore,''));
                    END
                """)
                db.execSQL("""
                    CREATE TRIGGER visits_fts_update AFTER UPDATE ON visits BEGIN
                        DELETE FROM visits_fts WHERE rowid = old.rowid;
                        INSERT INTO visits_fts(rowid, chiefComplaint, prescription, doctorNadi, doctorElementBefore)
                        VALUES (new.rowid, COALESCE(new.chiefComplaint,''), COALESCE(new.prescription,''),
                                COALESCE(new.doctorNadi,''), COALESCE(new.doctorElementBefore,''));
                    END
                """)
                db.execSQL("""
                    CREATE TRIGGER visits_fts_delete AFTER DELETE ON visits BEGIN
                        DELETE FROM visits_fts WHERE rowid = old.rowid;
                    END
                """)
            }
        }
    }
}
```

---

## TASK 8 — REPOSITORIES
### File: `data/repository/PatientRepository.kt`

```kotlin
package com.swarapulse.data.repository

import com.swarapulse.data.db.dao.PatientDao
import com.swarapulse.data.db.entity.Patient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PatientRepository @Inject constructor(
    private val dao: PatientDao,
    private val ioDispatcher: CoroutineDispatcher
) {
    fun getAllPatients(): Flow<List<Patient>> = dao.getAllPatients()
    fun getActivePatients(): Flow<List<Patient>> = dao.getActivePatients()
    fun getPatientById(id: Long): Flow<Patient?> = dao.getPatientById(id)
    fun searchPatients(query: String): Flow<List<Patient>> =
        dao.searchPatients("$query*")   // FTS5 prefix search
    fun getActivePatientCount(): Flow<Int> = dao.getActivePatientCount()

    suspend fun getPatientByIdOnce(id: Long): Patient? =
        withContext(ioDispatcher) { dao.getPatientByIdOnce(id) }

    suspend fun insertPatient(patient: Patient): Long =
        withContext(ioDispatcher) { dao.insertPatient(patient) }

    suspend fun updatePatient(patient: Patient) =
        withContext(ioDispatcher) { dao.updatePatient(patient) }

    suspend fun deletePatient(patient: Patient) =
        withContext(ioDispatcher) { dao.deletePatient(patient) }
}
```

### File: `data/repository/VisitRepository.kt`

Mirror the same pattern as PatientRepository, wrapping all VisitDao methods.  
Add a helper `suspend fun getVisitCount(): Int` that calls `getAllVisitsOnce().size` on the IO dispatcher.

### File: `data/repository/AppointmentRepository.kt`

Mirror the same pattern for AppointmentDao. Include helpers:
- `suspend fun markCompleted(id: Long)` → calls `updateStatus(id, "COMPLETED")`
- `suspend fun markCancelled(id: Long)` → calls `updateStatus(id, "CANCELLED")`

---

## TASK 9 — HILT DI MODULE
### File: `di/DatabaseModule.kt`

```kotlin
package com.swarapulse.di

import android.content.Context
import androidx.room.Room
import com.swarapulse.data.db.SwaraPulseDatabase
import com.swarapulse.data.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): SwaraPulseDatabase =
        Room.databaseBuilder(ctx, SwaraPulseDatabase::class.java, SwaraPulseDatabase.DATABASE_NAME)
            .addCallback(SwaraPulseDatabase.FTS_CALLBACK)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides fun providePatientDao(db: SwaraPulseDatabase) = db.patientDao()
    @Provides fun provideVisitDao(db: SwaraPulseDatabase) = db.visitDao()
    @Provides fun provideAppointmentDao(db: SwaraPulseDatabase) = db.appointmentDao()

    @Provides @IoDispatcher
    fun provideIoDispatcher() = Dispatchers.IO

    @Provides @Singleton
    fun providePatientRepository(dao: com.swarapulse.data.db.dao.PatientDao,
                                  @IoDispatcher d: kotlinx.coroutines.CoroutineDispatcher)
        = PatientRepository(dao, d)

    @Provides @Singleton
    fun provideVisitRepository(dao: com.swarapulse.data.db.dao.VisitDao,
                                @IoDispatcher d: kotlinx.coroutines.CoroutineDispatcher)
        = VisitRepository(dao, d)

    @Provides @Singleton
    fun provideAppointmentRepository(dao: com.swarapulse.data.db.dao.AppointmentDao,
                                      @IoDispatcher d: kotlinx.coroutines.CoroutineDispatcher)
        = AppointmentRepository(dao, d)
}
```

---

## TASK 10 — DATASTORE (Settings)
### File: `data/datastore/SettingsDataStore.kt`

Create a `SettingsDataStore` class using `DataStore<Preferences>` with these keys and accessors:

```
Keys:
  PIN_HASH       : String  (SHA-256 hex of PIN)
  IS_PIN_SET     : Boolean (false by default)
  DARK_MODE      : Boolean (follows system by default → store null as false)
  DISPLAY_NAME   : String  ("Practitioner")
  TITLE          : String  ("Swara Yoga Practitioner")
  CLINIC_NAME    : String  ("")
  BIOMETRIC_ENABLED : Boolean (true by default)
  SESSION_TOKEN  : String  (UUID string, cleared on logout)

Expose each as a Flow<T> property.
Expose suspend fun setXxx(value) for each key.
Expose suspend fun clearSession() to wipe SESSION_TOKEN.
```

Inject via constructor: `@Inject constructor(@ApplicationContext ctx: Context)`.  
Create the DataStore instance with filename `"swarapulse_settings"`.

### File: `di/DataStoreModule.kt`

Provide `SettingsDataStore` as a `@Singleton` binding.

---

## TASK 11 — AUTH UTILITIES
### File: `data/auth/AuthManager.kt`

```kotlin
package com.swarapulse.data.auth

import com.swarapulse.data.datastore.SettingsDataStore
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class AuthManager @Inject constructor(
    private val settings: SettingsDataStore
) {
    // Hash a PIN string using SHA-256, return hex string
    fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun isPinSet(): Boolean = settings.isPinSet.first()

    suspend fun setupPin(pin: String) {
        settings.setPinHash(hashPin(pin))
        settings.setIsPinSet(true)
    }

    suspend fun verifyPin(pin: String): Boolean {
        val stored = settings.pinHash.first()
        return stored == hashPin(pin)
    }

    suspend fun createSession() {
        settings.setSessionToken(UUID.randomUUID().toString())
    }

    suspend fun isSessionValid(): Boolean =
        settings.sessionToken.first().isNotEmpty()

    suspend fun logout() {
        settings.clearSession()
    }
}
```

---

## TASK 12 — THEME & DESIGN SYSTEM
### File: `presentation/ui/theme/Color.kt`

```kotlin
package com.swarapulse.presentation.ui.theme

import androidx.compose.ui.graphics.Color

// Brand
val Indigo600   = Color(0xFF4F46E5)
val Indigo400   = Color(0xFF818CF8)
val Indigo900   = Color(0xFF312E81)
val Purple600   = Color(0xFF9333EA)
val Purple400   = Color(0xFFC084FC)

// Semantic
val Emerald500  = Color(0xFF10B981)
val Emerald400  = Color(0xFF34D399)
val Rose500     = Color(0xFFF43F5E)
val Rose400     = Color(0xFFFB7185)
val Amber500    = Color(0xFFF59E0B)
val Orange400   = Color(0xFFFB923C)
val Cyan500     = Color(0xFF06B6D4)
val Blue400     = Color(0xFF60A5FA)
val Sky300      = Color(0xFF7DD3FC)
val Green300    = Color(0xFF86EFAC)

// Neutral
val Slate50     = Color(0xFFF8FAFC)
val Slate100    = Color(0xFFF1F5F9)
val Slate200    = Color(0xFFE2E8F0)
val Slate400    = Color(0xFF94A3B8)
val Slate600    = Color(0xFF475569)
val Slate800    = Color(0xFF1E293B)
val Slate900    = Color(0xFF0F172A)

// Element semantic colors
val ElementAir   = Sky300
val ElementFire  = Rose400
val ElementSpace = Purple400
val ElementEarth = Green300
val ElementWater = Blue400

// Nadi semantic colors
val NadiIda      = Indigo400
val NadiPingala  = Orange400
val NadiSushumna = Emerald400

// Gradient helpers (use as Brush.linearGradient arguments)
val GradientPrimary  = listOf(Indigo600, Purple600)
val GradientSuccess  = listOf(Emerald500, Cyan500)
val GradientWarning  = listOf(Amber500, Orange400)
val GradientDanger   = listOf(Rose500, Rose400)
```

### File: `presentation/ui/theme/Type.kt`

```kotlin
package com.swarapulse.presentation.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.swarapulse.R

// Download and place in res/font/:
//   cormorant_garamond_light.ttf      (weight 300)
//   cormorant_garamond_regular.ttf    (weight 400)
//   cormorant_garamond_semibold.ttf   (weight 600)
//   cormorant_garamond_bold.ttf       (weight 700)
//   dm_sans_regular.ttf               (weight 400)
//   dm_sans_medium.ttf                (weight 500)
//   dm_sans_semibold.ttf              (weight 600)

val CormorantGaramond = FontFamily(
    Font(R.font.cormorant_garamond_light,   FontWeight.Light),
    Font(R.font.cormorant_garamond_regular, FontWeight.Normal),
    Font(R.font.cormorant_garamond_semibold,FontWeight.SemiBold),
    Font(R.font.cormorant_garamond_bold,    FontWeight.Bold)
)

val DmSans = FontFamily(
    Font(R.font.dm_sans_regular,  FontWeight.Normal),
    Font(R.font.dm_sans_medium,   FontWeight.Medium),
    Font(R.font.dm_sans_semibold, FontWeight.SemiBold)
)

val SwaraPulseTypography = Typography(
    displayLarge  = TextStyle(fontFamily = CormorantGaramond, fontSize = 40.sp, fontWeight = FontWeight.Light,    letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = CormorantGaramond, fontSize = 32.sp, fontWeight = FontWeight.Normal),
    headlineLarge = TextStyle(fontFamily = CormorantGaramond, fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium= TextStyle(fontFamily = CormorantGaramond, fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontFamily = CormorantGaramond, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleLarge    = TextStyle(fontFamily = DmSans,            fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleMedium   = TextStyle(fontFamily = DmSans,            fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall    = TextStyle(fontFamily = DmSans,            fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge     = TextStyle(fontFamily = DmSans,            fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyMedium    = TextStyle(fontFamily = DmSans,            fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall     = TextStyle(fontFamily = DmSans,            fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelLarge    = TextStyle(fontFamily = DmSans,            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
    labelMedium   = TextStyle(fontFamily = DmSans,            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
    labelSmall    = TextStyle(fontFamily = DmSans,            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
)
```

### File: `presentation/ui/theme/Theme.kt`

Create a `SwaraPulseTheme` composable that:
1. Builds `lightColorScheme` and `darkColorScheme` using Material 3, mapping:
   - `primary` → `Indigo600`, `secondary` → `Purple600`, `tertiary` → `Cyan500`
   - `background` → `Slate50` (light) / `Slate900` (dark)
   - `surface` → `Color.White` (light) / `Slate800` (dark)
   - `error` → `Rose500`
2. Reads dark mode preference from `SettingsDataStore` as a `collectAsState` boolean
3. Applies `MaterialTheme(colorScheme, typography = SwaraPulseTypography, content = content)`
4. Calls `SideEffect` to update status bar color via `WindowCompat`

---

## TASK 13 — REUSABLE COMPOSE COMPONENTS
### File: `presentation/ui/components/Components.kt`

Implement ALL of the following composables in a single file:

#### 1. `GradientCard`
```
A Card with a Brush.linearGradient background drawn via Modifier.background.
Props: gradient: List<Color>, modifier, cornerRadius: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit
```

#### 2. `PatientAvatar`
```
Props: name: String, imagePath: String?, size: Dp = 48.dp, modifier
- If imagePath != null: AsyncImage (Coil) in a Circle clip
- Else: Box with gradient background (Indigo600 → Purple600), 
  centered Text of name.firstOrNull()?.uppercaseChar() in White, 
  fontFamily=CormorantGaramond, fontSize=(size.value * 0.4).sp
```

#### 3. `NadiChip`
```
Props: nadi: Nadi, modifier
- Color map: IDA→NadiIda, PINGALA→NadiPingala, SUSHUMNA→NadiSushumna, 
  others→Slate400
- Icon: IDA→Icons.Rounded.NightlightRound, PINGALA→Icons.Rounded.WbSunny,
  SUSHUMNA→Icons.Rounded.AllInclusive, others→Icons.Rounded.SwapHoriz
- Render as Material3 AssistChip with icon and nadi.displayName() label
- containerColor = chipColor.copy(alpha=0.15f), labelColor = chipColor
```

#### 4. `ElementBadge`
```
Props: element: Element, modifier
- Color map: AIR→ElementAir, FIRE→ElementFire, SPACE→ElementSpace,
  EARTH→ElementEarth, WATER→ElementWater, OTHER→Slate400
- Render as a Row: Text(element.emoji()), Spacer(2.dp), Text(element.displayName())
  all inside a rounded Surface with color.copy(alpha=0.12f) background
- padding: horizontal 8.dp, vertical 4.dp, rounded corner 20.dp
```

#### 5. `SectionHeader`
```
Props: title: String, icon: ImageVector? = null, action: (@Composable () -> Unit)? = null
- Row with optional icon (tinted Indigo600), Text(title, headlineSmall style), 
  Spacer(weight 1f), optional action slot
- Bottom border: Divider with Indigo600.copy(alpha=0.2f)
```

#### 6. `StatCard`
```
Props: label: String, value: String, icon: ImageVector, color: Color, modifier
- ElevatedCard, inside Column: Icon (tinted color), Text(value, headlineMedium), 
  Text(label, labelSmall, Slate600)
- Use animateContentSize() modifier
```

#### 7. `EmptyState`
```
Props: icon: ImageVector, message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null
- Centered Column: Icon(size=64.dp, tint=Slate400), Text(message, bodyMedium, Slate400)
- If actionLabel != null: OutlinedButton(onAction) { Text(actionLabel) }
```

#### 8. `LoadingOverlay`
```
Props: isLoading: Boolean
- AnimatedVisibility wrapping a Box(fillMaxSize) with semi-transparent background
  and centered CircularProgressIndicator(color=Indigo600)
```

#### 9. `ConfirmDialog`
```
Props: title: String, message: String, confirmLabel: String = "Confirm", 
       confirmColor: Color = Rose500, onConfirm: () -> Unit, onDismiss: () -> Unit
- AlertDialog with the given props. Confirm button uses ButtonDefaults.buttonColors(confirmColor)
```

---

## TASK 14 — AUTH SCREEN
### File: `presentation/auth/AuthScreen.kt` + `AuthViewModel.kt`

#### AuthViewModel
```kotlin
// State sealed class
sealed class AuthState {
    object Loading : AuthState()
    object NeedSetup : AuthState()          // first launch, no PIN set
    object NeedPin : AuthState()            // PIN set, show entry
    object NeedBiometric : AuthState()      // trigger biometric prompt
    data class Error(val msg: String) : AuthState()
    object Authenticated : AuthState()
}

// ViewModel exposes:
val authState: StateFlow<AuthState>
val pinDigits: StateFlow<String>    // current PIN input (max 6 chars, masked)
val isSetupConfirmStep: StateFlow<Boolean>  // true when confirming new PIN

fun onStart()            // check isPinSet → emit NeedSetup or NeedBiometric
fun onDigitEntered(d: Char)
fun onBackspace()
fun onBiometricResult(success: Boolean, errorMsg: String?)
fun onPinSubmitted()     // verifyPin or complete setup
```

#### AuthScreen composable
```
Full-screen dark background: Brush.verticalGradient(Slate900 → Slate800)

TOP SECTION (weight 0.4f):
- Large animated logo: 
  - Outer rotating ring (Canvas, dashed stroke, Indigo600.copy(0.3f), animateFloat 20s infinite)
  - Inner pulsing circle (animateFloat scale 0.95→1.05, 2s infinite ease-in-out)
  - "SP" text in CormorantGaramond Bold, 36.sp, White
- "SwaraPulse" title below, CormorantGaramond, 28.sp, White
- Subtitle: "Swara Yoga Patient Management" in DmSans, 12.sp, Slate400

MIDDLE SECTION:
- If NeedSetup or NeedPin:
  - Instruction text: "Create your PIN" or "Enter your PIN" or "Confirm your PIN"
    in DmSans, 14.sp, Slate400
  - PIN dot indicator: Row of 6 circles
    - Filled circle (Indigo600, 14.dp) for each entered digit
    - Empty circle (Slate600 stroke, 14.dp) for remaining
    - Animate fill with animateColorAsState
  - Error shake animation on wrong PIN (use animatable offset, shake left-right-left 200ms)

BOTTOM SECTION:
- Numpad grid (3×4): digits 1–9, *, 0, backspace
  - Each key: CircularButton, 56.dp, Slate800 background, White text, 
    ripple feedback, scale animation on press (0.92f)
  - Backspace shows Icons.Rounded.Backspace icon
  - "*" key empty / disabled
- If biometric available: "Use Biometric" TextButton below numpad (Indigo400 tint)

TRANSITIONS:
- AnimatedContent between NeedSetup and NeedPin states
- On Authenticated: navigate to Dashboard with slide-up transition

- Biometric: call BiometricPrompt from a LaunchedEffect when state == NeedBiometric
  - Title: "SwaraPulse", Subtitle: "Confirm identity to continue"
  - On success → viewModel.onBiometricResult(true, null)
  - On error/fail → viewModel.onBiometricResult(false, errorMsg)
```

---

## TASK 15 — APP ENTRY POINT
### File: `MainActivity.kt`

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            SwaraPulseTheme {
                SwaraPulseNavHost()
            }
        }
    }
}
```

### File: `presentation/navigation/NavHost.kt`

Create `SwaraPulseNavHost()` composable with `rememberNavController()`:

```
Routes:
  "auth"        → AuthScreen  (no bottom bar)
  "dashboard"   → DashboardScreen (with bottom bar)
  "patients"    → PatientListScreen (with bottom bar)
  "patients/{patientId}" → PatientDetailScreen
  "appointments"→ AppointmentsScreen (with bottom bar)
  "analytics"   → AnalyticsScreen (with bottom bar)
  "settings"    → SettingsScreen (with bottom bar)
  "visit/new?patientId={patientId}" → VisitFormScreen
  "visit/{visitId}/edit" → VisitFormScreen

Start destination: "auth"

After auth success → navigate("dashboard") { popUpTo("auth") { inclusive = true } }

Bottom bar items:
  Home (Icons.Rounded.Home, "dashboard")
  Patients (Icons.Rounded.People, "patients")
  Calendar (Icons.Rounded.CalendarMonth, "appointments")
  Analytics (Icons.Rounded.BarChart, "analytics")
  Settings (Icons.Rounded.Settings, "settings")

All non-auth screens wrapped in a Scaffold with the BottomBar.
BottomBar hidden on auth, patient detail, and visit form screens.
```

---

## TASK 16 — APPLICATION CLASS
### File: `SwaraPulseApp.kt`

```kotlin
@HiltAndroidApp
class SwaraPulseApp : Application()
```

Register it in `AndroidManifest.xml` as `android:name=".SwaraPulseApp"`.

---

## COMPLETION CHECKLIST

After writing all files, verify:
- [ ] All `import` statements are correct and complete
- [ ] No file references a class/function that doesn't exist yet
- [ ] `Converters.kt` covers every non-primitive type used in entities
- [ ] `DatabaseModule.kt` provides all DAOs and repositories
- [ ] `AuthScreen` builds without referencing any unimplemented screen
- [ ] All `@HiltViewModel` ViewModels have `@Inject constructor`
- [ ] `SwaraPulseApp` is declared in `AndroidManifest.xml`
- [ ] Font files are listed as expected in `res/font/` — add a comment in `Type.kt` listing the exact filenames to download from Google Fonts

Do not stub out any function bodies. Every function must be fully implemented.
