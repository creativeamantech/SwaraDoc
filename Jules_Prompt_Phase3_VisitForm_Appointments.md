# Jules Prompt — SwaraPulse Phase 3
## Screens: Visit Form (7-step) · Patient Form · Appointments

> Feed this entire file to Jules as a single task.
> Phases 1 & 2 are complete and compiling. Do NOT modify any existing file unless
> explicitly told to UPDATE it. Write complete, working Kotlin — no stubs.

---

## CONTEXT & CONSTRAINTS

Package root: `com.swarapulse`
Available from previous phases (import freely — do not redefine):
- Entities: `Patient`, `Visit`, `Appointment`, `MediaEntry`, `CustomField`, `FieldType`
- Enums: `Nadi`, `Element`, `Mandala`, `Paksha`, `PatientSide`, `Gender`, `AppointmentStatus`
- Repos: `PatientRepository`, `VisitRepository`, `AppointmentRepository`
- `SettingsDataStore`, `AuthManager`, `@IoDispatcher`
- Theme colors, typography, all reusable components from Components.kt
- Extensions: `timeAgo()`, `formatDisplay()`, `formatDateOnly()`, `daysUntil()`, `stripHtml()`

New dependencies required — confirm they are in `build.gradle.kts` before proceeding:
```
// Image picking
implementation("androidx.activity:activity-compose:1.8.x")       // rememberLauncherForActivityResult

// WorkManager + Hilt
implementation("androidx.work:work-runtime-ktx:2.9.x")
implementation("androidx.hilt:hilt-work:1.1.x")
ksp("androidx.hilt:hilt-compiler:1.1.x")

// Accompanist permissions (camera permission)
implementation("com.google.accompanist:accompanist-permissions:0.34.x")
```

---

## TASK 1 — DRAFT VISIT ENTITY (autosave support)
### File: `data/db/entity/DraftVisit.kt`

```kotlin
package com.swarapulse.data.db.entity

import androidx.room.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Temporary autosave entity. One row per in-progress visit form session.
 * Cleared on successful submit or explicit discard.
 */
@Entity(tableName = "draft_visits")
data class DraftVisit(
    @PrimaryKey val sessionId: String,          // UUID, generated when form opens
    val patientId: Long?,                        // null if brand-new patient
    val formStateJson: String,                   // entire VisitFormState serialized as JSON
    val currentStep: Int = 0,
    val lastSavedAt: Instant = Clock.System.now()
)
```

### File: `data/db/dao/DraftVisitDao.kt`

```kotlin
@Dao
interface DraftVisitDao {
    @Query("SELECT * FROM draft_visits WHERE sessionId = :sessionId")
    suspend fun getDraft(sessionId: String): DraftVisit?

    @Query("SELECT * FROM draft_visits ORDER BY lastSavedAt DESC LIMIT 1")
    suspend fun getLatestDraft(): DraftVisit?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDraft(draft: DraftVisit)

    @Query("DELETE FROM draft_visits WHERE sessionId = :sessionId")
    suspend fun deleteDraft(sessionId: String)

    @Query("DELETE FROM draft_visits")
    suspend fun clearAllDrafts()
}
```

### UPDATE: `data/db/SwaraPulseDatabase.kt`

Add `DraftVisit::class` to the `entities` list in `@Database`.
Bump `version` from `1` to `2`.
Add a migration:
```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS draft_visits (
                sessionId TEXT PRIMARY KEY NOT NULL,
                patientId INTEGER,
                formStateJson TEXT NOT NULL,
                currentStep INTEGER NOT NULL DEFAULT 0,
                lastSavedAt INTEGER NOT NULL
            )
        """)
    }
}
```
Add `.addMigrations(MIGRATION_1_2)` to the Room builder in `DatabaseModule.kt`.
Add `abstract fun draftVisitDao(): DraftVisitDao` to the abstract database class.

### UPDATE: `di/DatabaseModule.kt`

Add:
```kotlin
@Provides fun provideDraftVisitDao(db: SwaraPulseDatabase) = db.draftVisitDao()
```

---

## TASK 2 — VISIT FORM STATE MODEL
### File: `presentation/visit/VisitFormState.kt`

```kotlin
package com.swarapulse.presentation.visit

import com.swarapulse.data.db.entity.*
import kotlinx.serialization.Serializable

/**
 * Entire mutable state of the 7-step visit form.
 * Serialized to JSON for autosave. All fields are nullable/defaulted
 * so partial progress can be saved and restored.
 */
@Serializable
data class VisitFormState(

    // ── Step 1: Patient Info ──────────────────────────────────────────
    val name: String = "",
    val age: String = "",                     // String so TextField works naturally; parse to Int on save
    val gender: Gender? = null,
    val mobile: String = "",
    val email: String = "",
    val address: String = "",
    val occupation: String = "",
    val bloodGroup: String = "",
    val category: List<String> = emptyList(),
    val emergencyContactName: String = "",
    val emergencyContact: String = "",
    val isActive: Boolean = true,
    val isProvisional: Boolean = false,

    // ── Step 2: Medical History ───────────────────────────────────────
    val medicalHistory: String = "",          // HTML/plain text

    // ── Step 3: Yogic Evaluation ──────────────────────────────────────
    val patientMandala: Mandala? = null,
    val patientNadi: Nadi? = null,
    val patientElement: Element? = null,
    val patientSide: PatientSide? = null,

    // ── Step 4: Doctor Assessment ─────────────────────────────────────
    val doctorNadi: String = "",
    val doctorElementBefore: String = "",
    val doctorElementAfter: String = "",
    val paksha: Paksha? = null,
    val tithi: String = "",
    val tithiElement: Element? = null,

    // ── Step 5: Clinical Notes ────────────────────────────────────────
    val chiefComplaint: String = "",
    val prescription: String = "",
    val diseaseCategories: List<String> = emptyList(),

    // ── Step 6: Media & Custom ────────────────────────────────────────
    val mediaPaths: List<String> = emptyList(),      // absolute file paths
    val customFields: List<CustomField> = emptyList(),

    // ── Step 7: Timing ────────────────────────────────────────────────
    val visitDateTimeMs: Long? = null,        // epoch ms; null = "now" on submit
    val followupDateTimeMs: Long? = null
)

// Per-step validation results
data class StepValidation(val isValid: Boolean, val errors: Map<String, String> = emptyMap())

fun VisitFormState.validateStep(step: Int): StepValidation = when (step) {
    0 -> {
        val errors = mutableMapOf<String, String>()
        if (name.isBlank())   errors["name"]   = "Name is required"
        if (age.isBlank() || age.toIntOrNull() == null || age.toInt() !in 0..150)
                               errors["age"]    = "Enter a valid age (0–150)"
        if (gender == null)    errors["gender"] = "Select a gender"
        if (mobile.length < 10) errors["mobile"] = "Enter a valid mobile number"
        StepValidation(errors.isEmpty(), errors)
    }
    1 -> StepValidation(true)  // medical history is optional
    2 -> {
        val errors = mutableMapOf<String, String>()
        if (patientMandala == null) errors["mandala"] = "Select a Mandala"
        if (patientNadi == null)    errors["nadi"]    = "Select a Nadi"
        if (patientElement == null) errors["element"] = "Select an Element"
        if (patientSide == null)    errors["side"]    = "Select sitting position"
        StepValidation(errors.isEmpty(), errors)
    }
    3 -> {
        val errors = mutableMapOf<String, String>()
        if (doctorNadi.isBlank())         errors["doctorNadi"]    = "Required"
        if (doctorElementBefore.isBlank()) errors["doctorElement"] = "Required"
        if (paksha == null)                errors["paksha"]        = "Select Paksha"
        if (tithi.isBlank())               errors["tithi"]         = "Select Tithi"
        if (tithiElement == null)          errors["tithiElement"]  = "Select Tithi Element"
        StepValidation(errors.isEmpty(), errors)
    }
    4 -> {
        val errors = mutableMapOf<String, String>()
        if (chiefComplaint.isBlank()) errors["complaint"] = "Enter a chief complaint"
        StepValidation(errors.isEmpty(), errors)
    }
    5 -> StepValidation(true)   // media + custom fields optional
    6 -> StepValidation(true)   // timing: visitDateTime defaults to now on submit
    else -> StepValidation(true)
}
```

---

## TASK 3 — VISIT FORM VIEWMODEL
### File: `presentation/visit/VisitFormViewModel.kt`

```kotlin
@HiltViewModel
class VisitFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val patientRepo: PatientRepository,
    private val visitRepo: VisitRepository,
    private val draftDao: DraftVisitDao,
    private val settingsDataStore: SettingsDataStore,
    @IoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    // ── Navigation args ───────────────────────────────────────────────
    // patientId: -1L means brand-new patient not yet in DB
    private val patientId: Long = savedStateHandle.get<Long>("patientId") ?: -1L
    // visitId: non-null means EDIT mode
    private val editVisitId: Long? = savedStateHandle.get<Long>("visitId")

    // ── Session UUID for autosave ─────────────────────────────────────
    private val sessionId = java.util.UUID.randomUUID().toString()

    // ── Form state ────────────────────────────────────────────────────
    private val _formState = MutableStateFlow(VisitFormState())
    val formState: StateFlow<VisitFormState> = _formState.asStateFlow()

    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private val _stepErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val stepErrors: StateFlow<Map<String, String>> = _stepErrors.asStateFlow()

    // ── UI events ────────────────────────────────────────────────────
    sealed class UiEvent {
        data class ShowSnackbar(val message: String) : UiEvent()
        object NavigateBack : UiEvent()
        object SaveSuccess : UiEvent()
    }
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    // ── Submit state ─────────────────────────────────────────────────
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    // ── Existing patient (if patientId != -1) ────────────────────────
    val existingPatient: StateFlow<Patient?> =
        if (patientId > 0) patientRepo.getPatientById(patientId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
        else MutableStateFlow(null)

    // ── All patients for search in Step 1 (new visit, patient picker) ─
    val allPatients: StateFlow<List<Patient>> =
        patientRepo.getAllPatients()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch(io) {
            when {
                // EDIT mode: load visit data into form
                editVisitId != null -> loadVisitForEdit(editVisitId)

                // Existing patient: pre-fill patient fields
                patientId > 0 -> {
                    val p = patientRepo.getPatientByIdOnce(patientId)
                    p?.let { prefillFromPatient(it) }
                }

                // Try to restore latest autosave draft
                else -> {
                    val draft = draftDao.getLatestDraft()
                    draft?.let {
                        val restored = kotlinx.serialization.json.Json.decodeFromString<VisitFormState>(it.formStateJson)
                        _formState.value = restored
                        _currentStep.value = it.currentStep
                    }
                }
            }
        }

        // Autosave every 30 seconds
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000L)
                autosave()
            }
        }
    }

    // ── Form field updaters (one per field group) ─────────────────────

    fun updateName(v: String)          { _formState.update { it.copy(name = v) } }
    fun updateAge(v: String)           { _formState.update { it.copy(age = v) } }
    fun updateGender(v: Gender)        { _formState.update { it.copy(gender = v) } }
    fun updateMobile(v: String)        { _formState.update { it.copy(mobile = v) } }
    fun updateEmail(v: String)         { _formState.update { it.copy(email = v) } }
    fun updateAddress(v: String)       { _formState.update { it.copy(address = v) } }
    fun updateOccupation(v: String)    { _formState.update { it.copy(occupation = v) } }
    fun updateBloodGroup(v: String)    { _formState.update { it.copy(bloodGroup = v) } }
    fun updateCategory(v: List<String>){ _formState.update { it.copy(category = v) } }
    fun updateEmergencyContactName(v: String) { _formState.update { it.copy(emergencyContactName = v) } }
    fun updateEmergencyContact(v: String)     { _formState.update { it.copy(emergencyContact = v) } }
    fun updateIsActive(v: Boolean)     { _formState.update { it.copy(isActive = v) } }
    fun updateIsProvisional(v: Boolean){ _formState.update { it.copy(isProvisional = v) } }

    fun updateMedicalHistory(v: String){ _formState.update { it.copy(medicalHistory = v) } }

    fun updatePatientMandala(v: Mandala)   { _formState.update { it.copy(patientMandala = v) } }
    fun updatePatientNadi(v: Nadi)         { _formState.update { it.copy(patientNadi = v) } }
    fun updatePatientElement(v: Element)   { _formState.update { it.copy(patientElement = v) } }
    fun updatePatientSide(v: PatientSide)  { _formState.update { it.copy(patientSide = v) } }

    fun updateDoctorNadi(v: String)         { _formState.update { it.copy(doctorNadi = v) } }
    fun updateDoctorElementBefore(v: String){ _formState.update { it.copy(doctorElementBefore = v) } }
    fun updateDoctorElementAfter(v: String) { _formState.update { it.copy(doctorElementAfter = v) } }
    fun updatePaksha(v: Paksha)             { _formState.update { it.copy(paksha = v) } }
    fun updateTithi(v: String)              { _formState.update { it.copy(tithi = v) } }
    fun updateTithiElement(v: Element)      { _formState.update { it.copy(tithiElement = v) } }

    fun updateChiefComplaint(v: String)     { _formState.update { it.copy(chiefComplaint = v) } }
    fun updatePrescription(v: String)       { _formState.update { it.copy(prescription = v) } }
    fun updateDiseaseCategories(v: List<String>) { _formState.update { it.copy(diseaseCategories = v) } }

    fun addMediaPath(path: String)  { _formState.update { it.copy(mediaPaths = it.mediaPaths + path) } }
    fun removeMediaPath(path: String){ _formState.update { it.copy(mediaPaths = it.mediaPaths - path) } }
    fun addCustomField(field: CustomField) { _formState.update { it.copy(customFields = it.customFields + field) } }
    fun removeCustomField(id: String)      { _formState.update { it.copy(customFields = it.customFields.filterNot { f -> f.id == id }) } }
    fun updateCustomFieldValue(id: String, value: String) {
        _formState.update {
            it.copy(customFields = it.customFields.map { f -> if (f.id == id) f.copy(value = value) else f })
        }
    }

    fun updateVisitDateTime(ms: Long)   { _formState.update { it.copy(visitDateTimeMs = ms) } }
    fun updateFollowupDateTime(ms: Long?){ _formState.update { it.copy(followupDateTimeMs = ms) } }

    // ── Step navigation ───────────────────────────────────────────────

    fun goToStep(step: Int) {
        if (step in 0..6) _currentStep.value = step
    }

    /** Validate current step then advance. Returns true if advanced. */
    fun tryAdvance(): Boolean {
        val validation = _formState.value.validateStep(_currentStep.value)
        _stepErrors.value = validation.errors
        return if (validation.isValid) {
            if (_currentStep.value < 6) _currentStep.value++
            true
        } else false
    }

    fun goBack() {
        _stepErrors.value = emptyMap()
        if (_currentStep.value > 0) _currentStep.value--
    }

    // ── Autosave ──────────────────────────────────────────────────────

    private suspend fun autosave() {
        val json = kotlinx.serialization.json.Json.encodeToString(
            VisitFormState.serializer(), _formState.value
        )
        draftDao.saveDraft(
            DraftVisit(
                sessionId     = sessionId,
                patientId     = if (patientId > 0) patientId else null,
                formStateJson = json,
                currentStep   = _currentStep.value,
                lastSavedAt   = kotlinx.datetime.Clock.System.now()
            )
        )
        _events.emit(UiEvent.ShowSnackbar("Draft saved"))
    }

    // ── Submit ────────────────────────────────────────────────────────

    fun submit() {
        // Validate all steps before submitting
        val allErrors = (0..6).mapNotNull { step ->
            val v = _formState.value.validateStep(step)
            if (!v.isValid) step to v.errors else null
        }
        if (allErrors.isNotEmpty()) {
            // Jump to first invalid step
            _currentStep.value = allErrors.first().first
            _stepErrors.value = allErrors.first().second
            return
        }

        viewModelScope.launch(io) {
            _isSaving.value = true
            try {
                val state = _formState.value
                val now   = kotlinx.datetime.Clock.System.now()

                // 1. Upsert patient
                val savedPatientId: Long = when {
                    editVisitId != null -> patientId  // edit mode, patient exists
                    patientId > 0       -> {
                        // Update existing patient fields
                        val existing = patientRepo.getPatientByIdOnce(patientId)!!
                        patientRepo.updatePatient(
                            existing.copy(
                                name                 = state.name,
                                age                  = state.age.toIntOrNull() ?: existing.age,
                                gender               = state.gender ?: existing.gender,
                                mobile               = state.mobile,
                                email                = state.email.ifBlank { null },
                                address              = state.address.ifBlank { null },
                                occupation           = state.occupation.ifBlank { null },
                                bloodGroup           = state.bloodGroup.ifBlank { null },
                                category             = state.category,
                                medicalHistory       = state.medicalHistory.ifBlank { null },
                                emergencyContact     = state.emergencyContact.ifBlank { null },
                                emergencyContactName = state.emergencyContactName.ifBlank { null },
                                isActive             = state.isActive,
                                isProvisional        = state.isProvisional,
                                lastVisitDate        = now,
                                updatedAt            = now
                            )
                        )
                        patientId
                    }
                    else -> {
                        // Insert new patient
                        patientRepo.insertPatient(
                            Patient(
                                name                 = state.name,
                                age                  = state.age.toIntOrNull() ?: 0,
                                gender               = state.gender ?: Gender.OTHER,
                                mobile               = state.mobile,
                                email                = state.email.ifBlank { null },
                                address              = state.address.ifBlank { null },
                                occupation           = state.occupation.ifBlank { null },
                                bloodGroup           = state.bloodGroup.ifBlank { null },
                                category             = state.category,
                                medicalHistory       = state.medicalHistory.ifBlank { null },
                                emergencyContact     = state.emergencyContact.ifBlank { null },
                                emergencyContactName = state.emergencyContactName.ifBlank { null },
                                isActive             = state.isActive,
                                isProvisional        = state.isProvisional,
                                lastVisitDate        = now,
                                createdAt            = now,
                                updatedAt            = now
                            )
                        )
                    }
                }

                // 2. Build visit
                val visitDateTime = state.visitDateTimeMs?.let {
                    kotlinx.datetime.Instant.fromEpochMilliseconds(it)
                } ?: now

                val followupInstant = state.followupDateTimeMs?.let {
                    kotlinx.datetime.Instant.fromEpochMilliseconds(it)
                }

                val mediaEntries = state.mediaPaths.map { path ->
                    MediaEntry(filePath = path, dateTaken = now.toEpochMilliseconds())
                }

                val visit = Visit(
                    id                  = if (editVisitId != null) editVisitId else 0,
                    patientId           = savedPatientId,
                    dateTime            = visitDateTime,
                    patientMandala      = state.patientMandala ?: Mandala.OTHER,
                    patientNadi         = state.patientNadi ?: Nadi.OTHER,
                    patientElement      = state.patientElement ?: Element.OTHER,
                    patientSide         = state.patientSide ?: PatientSide.FRONT,
                    doctorNadi          = state.doctorNadi,
                    doctorElementBefore = state.doctorElementBefore,
                    doctorElementAfter  = state.doctorElementAfter.ifBlank { null },
                    paksha              = state.paksha ?: Paksha.SHUKLA,
                    tithi               = state.tithi,
                    tithiElement        = state.tithiElement ?: Element.OTHER,
                    chiefComplaint      = state.chiefComplaint,
                    prescription        = state.prescription,
                    diseaseCategories   = state.diseaseCategories,
                    mediaEntries        = mediaEntries,
                    customFields        = state.customFields,
                    followupDate        = followupInstant,
                    editedAt            = if (editVisitId != null) now else null,
                    createdAt           = now
                )

                // 3. Upsert visit
                if (editVisitId != null) visitRepo.updateVisit(visit)
                else visitRepo.insertVisit(visit)

                // 4. Clear autosave draft
                draftDao.deleteDraft(sessionId)

                _events.emit(UiEvent.SaveSuccess)

            } catch (e: Exception) {
                _events.emit(UiEvent.ShowSnackbar("Save failed: ${e.message}"))
            } finally {
                _isSaving.value = false
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private fun prefillFromPatient(p: Patient) {
        _formState.update { s ->
            s.copy(
                name                 = p.name,
                age                  = p.age.toString(),
                gender               = p.gender,
                mobile               = p.mobile,
                email                = p.email ?: "",
                address              = p.address ?: "",
                occupation           = p.occupation ?: "",
                bloodGroup           = p.bloodGroup ?: "",
                category             = p.category,
                emergencyContactName = p.emergencyContactName ?: "",
                emergencyContact     = p.emergencyContact ?: "",
                medicalHistory       = p.medicalHistory ?: "",
                isActive             = p.isActive,
                isProvisional        = p.isProvisional
            )
        }
    }

    private suspend fun loadVisitForEdit(visitId: Long) {
        val visits = visitRepo.getVisitsForPatientOnce(patientId)
        val visit  = visits.firstOrNull { it.id == visitId } ?: return
        val p      = patientRepo.getPatientByIdOnce(visit.patientId) ?: return
        prefillFromPatient(p)
        _formState.update { s ->
            s.copy(
                patientMandala      = visit.patientMandala,
                patientNadi         = visit.patientNadi,
                patientElement      = visit.patientElement,
                patientSide         = visit.patientSide,
                doctorNadi          = visit.doctorNadi,
                doctorElementBefore = visit.doctorElementBefore,
                doctorElementAfter  = visit.doctorElementAfter ?: "",
                paksha              = visit.paksha,
                tithi               = visit.tithi,
                tithiElement        = visit.tithiElement,
                chiefComplaint      = visit.chiefComplaint,
                prescription        = visit.prescription,
                diseaseCategories   = visit.diseaseCategories,
                mediaPaths          = visit.mediaEntries.map { it.filePath },
                customFields        = visit.customFields,
                visitDateTimeMs     = visit.dateTime.toEpochMilliseconds(),
                followupDateTimeMs  = visit.followupDate?.toEpochMilliseconds()
            )
        }
    }
}
```

---

## TASK 4 — VISIT FORM SHARED COMPOSABLES
### File: `presentation/visit/VisitFormComponents.kt`

Implement all reusable composables used across form steps:

### 4.1 `FormStepProgressBar`
```kotlin
@Composable
fun FormStepProgressBar(
    totalSteps: Int,
    currentStep: Int,
    stepTitles: List<String>,
    onStepClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Horizontal row of totalSteps segments
    // Each segment: filled rectangle (Indigo600) if index <= currentStep, else Slate200
    // Height: 4.dp, rounded corners, gap 4.dp between segments
    // Below segments: row of step titles — only current title is visible (labelSmall, Indigo600)
    //   others show their number dimmed (labelSmall, Slate400)
    // Tapping a completed step (index < currentStep) navigates back to it
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(totalSteps) { index ->
                val filled = index <= currentStep
                val color  = if (filled) Indigo600 else Slate200
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(animateColorAsState(color, label = "step$index").value)
                        .clickable(enabled = index < currentStep) { onStepClick(index) }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(totalSteps) { index ->
                Text(
                    text = if (index == currentStep) stepTitles[index] else "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == currentStep) Indigo600 else Slate400,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
```

### 4.2 `FormCard`
```kotlin
// A Card container with a left-side colored accent bar and title
@Composable
fun FormCard(
    title: String,
    accentColor: Color = Indigo600,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row {
            // Left accent bar
            Box(Modifier.width(4.dp).fillMaxHeight().background(accentColor))
            Column(
                Modifier.padding(16.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = accentColor)
                content()
            }
        }
    }
}
```

### 4.3 `FormTextField`
```kotlin
@Composable
fun FormTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    singleLine: Boolean = true,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    Column(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            singleLine = singleLine,
            trailingIcon = trailingIcon,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        )
        error?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
                 modifier = Modifier.padding(start = 8.dp, top = 4.dp))
        }
    }
}
```

### 4.4 `FormDropdown`
```kotlin
// A labeled ExposedDropdownMenuBox for selecting a single enum value
@Composable
fun <T> FormDropdown(
    label: String,
    selected: T?,
    options: List<T>,
    displayName: (T) -> String,
    onSelect: (T) -> Unit,
    error: String? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selected?.let { displayName(it) } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                isError = error != null,
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(displayName(option)) },
                        onClick = { onSelect(option); expanded = false }
                    )
                }
            }
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.labelSmall,
                 color = MaterialTheme.colorScheme.error,
                 modifier = Modifier.padding(start = 8.dp, top = 4.dp))
        }
    }
}
```

### 4.5 `FormChipSelector`
```kotlin
// Horizontal scrollable row of FilterChips — select one from a list
@Composable
fun <T> FormChipSelector(
    label: String,
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    displayName: (T) -> String,
    chipColor: (T) -> Color = { Indigo600 },
    error: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Slate600)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { option ->
                val isSelected = selected == option
                FilterChip(
                    selected = isSelected,
                    onClick  = { onSelect(option) },
                    label    = { Text(displayName(option), style = MaterialTheme.typography.labelSmall) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipColor(option).copy(alpha = 0.15f),
                        selectedLabelColor = chipColor(option)
                    )
                )
            }
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.labelSmall,
                 color = MaterialTheme.colorScheme.error,
                 modifier = Modifier.padding(start = 4.dp))
        }
    }
}
```

### 4.6 `TagInputField`
```kotlin
// Input field that builds a list of String tags
// Press Enter or comma → add tag. Tap X on chip → remove.
@Composable
fun TagInputField(
    label: String,
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    suggestions: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }

    fun addTag(tag: String) {
        val trimmed = tag.trim().filter { it != ',' }
        if (trimmed.isNotBlank() && !tags.contains(trimmed)) {
            onTagsChange(tags + trimmed)
        }
        inputText = ""
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Slate600)
        // Existing tags
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick  = {},
                        label    = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                        trailingIcon = {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove $tag",
                                 modifier = Modifier.size(14.dp).clickable { onTagsChange(tags - tag) })
                        }
                    )
                }
            }
        }
        // Input row
        OutlinedTextField(
            value = inputText,
            onValueChange = { v ->
                if (v.endsWith(',') || v.endsWith('\n')) addTag(v)
                else inputText = v
                showSuggestions = v.isNotBlank()
            },
            placeholder = { Text("Type and press Enter…") },
            singleLine  = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { addTag(inputText) }),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        )
        // Autocomplete suggestions
        if (showSuggestions) {
            val filtered = suggestions.filter {
                it.contains(inputText, ignoreCase = true) && !tags.contains(it)
            }.take(4)
            if (filtered.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filtered) { s ->
                        SuggestionChip(onClick = { addTag(s) },
                                       label = { Text(s, style = MaterialTheme.typography.labelSmall) })
                    }
                }
            }
        }
    }
}
```

### 4.7 `DateTimePickerField`
```kotlin
// Tappable field that opens a DatePickerDialog then TimePickerDialog
@Composable
fun DateTimePickerField(
    label: String,
    epochMs: Long?,
    onDateTimeSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isOptional: Boolean = false
) {
    val context = LocalContext.current
    val displayText = epochMs?.let {
        kotlinx.datetime.Instant.fromEpochMilliseconds(it).formatDisplay()
    } ?: if (isOptional) "Not set (tap to set)" else "Tap to select"

    OutlinedTextField(
        value = displayText,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
        trailingIcon = {
            if (isOptional && epochMs != null) {
                IconButton(onClick = { /* clear: call onDateTimeSelected with 0 or handle null */ }) {
                    Icon(Icons.Rounded.Clear, contentDescription = "Clear date")
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                // Open Material DatePicker
                val cal = java.util.Calendar.getInstance()
                epochMs?.let { cal.timeInMillis = it }
                android.app.DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        cal.set(year, month, day)
                        // Then open TimePicker
                        android.app.TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
                                cal.set(java.util.Calendar.MINUTE, minute)
                                onDateTimeSelected(cal.timeInMillis)
                            },
                            cal.get(java.util.Calendar.HOUR_OF_DAY),
                            cal.get(java.util.Calendar.MINUTE),
                            true // 24h
                        ).show()
                    },
                    cal.get(java.util.Calendar.YEAR),
                    cal.get(java.util.Calendar.MONTH),
                    cal.get(java.util.Calendar.DAY_OF_MONTH)
                ).show()
            },
        shape = RoundedCornerShape(8.dp)
    )
}
```

---

## TASK 5 — SEVEN STEP COMPOSABLES
### File: `presentation/visit/steps/Step1PatientInfo.kt`

```kotlin
@Composable
fun Step1PatientInfo(
    state: VisitFormState,
    errors: Map<String, String>,
    vm: VisitFormViewModel,
    isEditingExisting: Boolean
) {
    // If editing an existing patient, show a notice that patient info will be updated.
    // Fields: Name*, Age*, Gender* (chip selector), Mobile*, Email, Address (multiline),
    //         Occupation, Blood Group (dropdown: A+/A-/B+/B-/AB+/AB-/O+/O-/Unknown),
    //         Category tags (TagInputField), Emergency Contact Name, Emergency Contact Phone
    //         isActive toggle switch, isProvisional toggle switch
    //
    // Group into two FormCards:
    //   Card 1 "Basic Info" (accent Indigo600): Name, Age, Gender, Mobile, Email
    //   Card 2 "Additional Info" (accent Purple600): Address, Occupation, Blood Group, Category
    //   Card 3 "Emergency & Status" (accent Cyan500): Emergency name, phone, Active toggle, Provisional toggle
    //
    // All fields connect to vm.updateXxx() functions.
    // Show error text from errors map under each field using FormTextField's error param.
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            if (isEditingExisting) {
                Surface(color = Amber500.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Info, null, tint = Amber500, modifier = Modifier.size(18.dp))
                        Text("Updating will save changes to this patient's profile.",
                             style = MaterialTheme.typography.bodySmall, color = Amber500)
                    }
                }
            }
        }
        item {
            FormCard("Basic Information", accentColor = Indigo600) {
                FormTextField("Full Name *", state.name, vm::updateName, errors["name"])
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormTextField("Age *", state.age, vm::updateAge, errors["age"],
                                  KeyboardType.Number, modifier = Modifier.weight(1f))
                    FormDropdown("Blood Group", state.bloodGroup.ifBlank { null },
                                 listOf("A+","A-","B+","B-","AB+","AB-","O+","O-","Unknown"),
                                 displayName = { it }, onSelect = vm::updateBloodGroup,
                                 modifier = Modifier.weight(1f))
                }
                FormChipSelector(
                    label = "Gender *",
                    options = Gender.values().toList(),
                    selected = state.gender,
                    onSelect = vm::updateGender,
                    displayName = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                    error = errors["gender"]
                )
                FormTextField("Mobile *", state.mobile, vm::updateMobile, errors["mobile"],
                              KeyboardType.Phone)
                FormTextField("Email", state.email, vm::updateEmail, keyboardType = KeyboardType.Email)
            }
        }
        item {
            FormCard("Additional Information", accentColor = Purple600) {
                FormTextField("Address", state.address, vm::updateAddress,
                              singleLine = false, imeAction = ImeAction.Next)
                FormTextField("Occupation", state.occupation, vm::updateOccupation)
                TagInputField(
                    label = "Categories / Labels",
                    tags = state.category,
                    onTagsChange = vm::updateCategory,
                    suggestions = listOf("Chronic", "Acute", "Follow-up", "New", "Pediatric", "Geriatric")
                )
            }
        }
        item {
            FormCard("Emergency & Status", accentColor = Cyan500) {
                FormTextField("Emergency Contact Name", state.emergencyContactName,
                              vm::updateEmergencyContactName)
                FormTextField("Emergency Contact Phone", state.emergencyContact,
                              vm::updateEmergencyContact, keyboardType = KeyboardType.Phone)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Active Patient", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = state.isActive, onCheckedChange = vm::updateIsActive)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Provisional", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = state.isProvisional, onCheckedChange = vm::updateIsProvisional)
                }
            }
        }
    }
}
```

### File: `presentation/visit/steps/Step2MedicalHistory.kt`

```kotlin
@Composable
fun Step2MedicalHistory(state: VisitFormState, vm: VisitFormViewModel) {
    // Single FormCard "Medical History" (accent Emerald500)
    // Inside: a large multiline OutlinedTextField (minLines=10) for medicalHistory
    // + a mic button (Icons.Rounded.Mic) in the trailing icon that launches
    //   speech-to-text via Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    //   using rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult())
    //   Append recognized text to existing medicalHistory
    // Show character count below the field.
    // Helper text: "Document ongoing conditions, allergies, and past treatments."
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spokenText = result.data
            ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull() ?: return@rememberLauncherForActivityResult
        vm.updateMedicalHistory(state.medicalHistory + " " + spokenText)
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            FormCard("Medical History", accentColor = Emerald500) {
                Text("Document ongoing conditions, allergies, and past treatments.",
                     style = MaterialTheme.typography.bodySmall, color = Slate600)
                OutlinedTextField(
                    value = state.medicalHistory,
                    onValueChange = vm::updateMedicalHistory,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                    placeholder = { Text("Enter medical history…") },
                    minLines = 8,
                    trailingIcon = {
                        IconButton(onClick = {
                            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                         android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak medical history…")
                            }
                            speechLauncher.launch(intent)
                        }) { Icon(Icons.Rounded.Mic, "Dictate", tint = Indigo600) }
                    },
                    shape = RoundedCornerShape(8.dp)
                )
                Text("${state.medicalHistory.length} characters",
                     style = MaterialTheme.typography.labelSmall, color = Slate400)
            }
        }
    }
}
```

### File: `presentation/visit/steps/Step3YogicEvaluation.kt`

```kotlin
@Composable
fun Step3YogicEvaluation(state: VisitFormState, errors: Map<String, String>, vm: VisitFormViewModel) {
    // FormCard "Yogic Evaluation" (accent = Indigo600 with a subtle mandala background doodle)
    // Fields:
    //   Mandala: FormChipSelector(Mandala.values(), chipColor: IDA→NadiIda, PINGALA→NadiPingala, OTHER→Slate400)
    //   Patient Nadi: FormChipSelector(Nadi.values(), color: IDA→NadiIda, PINGALA→NadiPingala, SUSHUMNA→NadiSushumna, else Slate400)
    //   Patient Element: FormChipSelector(Element.values(), each element's semantic color)
    //   Sitting Position: FormChipSelector(PatientSide.values(), all Indigo600)
    //
    // Below the chips: a live "Reading Summary" card showing the chosen values as
    //   NadiChip + ElementBadge side by side, animated with AnimatedContent
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            FormCard("Yogic Evaluation", accentColor = Indigo600) {
                FormChipSelector("Mandala *", Mandala.values().toList(), state.patientMandala,
                    vm::updatePatientMandala,
                    displayName = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                    chipColor = { m -> when(m) { Mandala.IDA -> NadiIda; Mandala.PINGALA -> NadiPingala; else -> Slate400 } },
                    error = errors["mandala"])
                FormChipSelector("Patient Nadi *", Nadi.values().toList(), state.patientNadi,
                    vm::updatePatientNadi, displayName = { it.displayName() },
                    chipColor = { n -> when(n) { Nadi.IDA -> NadiIda; Nadi.PINGALA -> NadiPingala; Nadi.SUSHUMNA -> NadiSushumna; else -> Slate400 } },
                    error = errors["nadi"])
                FormChipSelector("Patient Element *", Element.values().toList(), state.patientElement,
                    vm::updatePatientElement, displayName = { it.displayName() },
                    chipColor = { e -> when(e) { Element.AIR -> ElementAir; Element.FIRE -> ElementFire; Element.SPACE -> ElementSpace; Element.EARTH -> ElementEarth; Element.WATER -> ElementWater; else -> Slate400 } },
                    error = errors["element"])
                FormChipSelector("Sitting Position *", PatientSide.values().toList(), state.patientSide,
                    vm::updatePatientSide,
                    displayName = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                    error = errors["side"])
            }
        }
        item {
            // Live reading summary
            if (state.patientNadi != null || state.patientElement != null) {
                Surface(shape = RoundedCornerShape(12.dp),
                        color = Indigo600.copy(alpha = 0.05f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Indigo600.copy(alpha = 0.2f))) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Visibility, null, tint = Indigo600, modifier = Modifier.size(16.dp))
                        Text("Reading: ", style = MaterialTheme.typography.labelSmall, color = Slate600)
                        state.patientNadi?.let { NadiChip(it) }
                        state.patientElement?.let { ElementBadge(it) }
                    }
                }
            }
        }
    }
}
```

### File: `presentation/visit/steps/Step4DoctorAssessment.kt`

```kotlin
@Composable
fun Step4DoctorAssessment(state: VisitFormState, errors: Map<String, String>, vm: VisitFormViewModel) {
    // Two FormCards:
    // Card 1 "Doctor Assessment" (accent Emerald500):
    //   - Doctor Nadi: FormTextField (free text, practitioner types their own nadi)
    //   - Doctor Element Before: FormTextField
    //   - Doctor Element After: FormTextField (optional — label shows "(after treatment)")
    //
    // Card 2 "Lunar / Temporal Factors" (accent Amber500):
    //   - Paksha: FormChipSelector(SHUKLA, KRISHNA)
    //     SHUKLA chip → Moon growing icon visual (Icons.Rounded.Brightness4)
    //     KRISHNA chip → Moon waning icon (Icons.Rounded.Brightness3)
    //   - Tithi: horizontal scrollable row of numbered chips 1–15 + "Other"
    //     using LazyRow with FilterChip for each; selected = highlighted Amber500
    //   - Tithi Element: FormChipSelector(Element.values()) same colors as Step 3
    //
    // Below: a "Match Indicator" card — shows if patientNadi matches doctorNadi (string equality)
    //   and patientElement matches doctorElement (string equality)
    //   Use green checkmark (Icons.Rounded.CheckCircle, Emerald500) if match,
    //   amber warning (Icons.Rounded.Warning, Amber500) if no match
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            FormCard("Doctor's Assessment", accentColor = Emerald500) {
                FormTextField("Doctor's Nadi *", state.doctorNadi, vm::updateDoctorNadi,
                              errors["doctorNadi"])
                FormTextField("Doctor's Element (Before) *", state.doctorElementBefore,
                              vm::updateDoctorElementBefore, errors["doctorElement"])
                FormTextField("Doctor's Element (After Treatment)", state.doctorElementAfter,
                              vm::updateDoctorElementAfter)
            }
        }
        item {
            FormCard("Lunar / Temporal Factors", accentColor = Amber500) {
                FormChipSelector("Paksha *", Paksha.values().toList(), state.paksha,
                    vm::updatePaksha, displayName = { it.displayName() },
                    chipColor = { Amber500 }, error = errors["paksha"])
                // Tithi selector
                Text("Tithi *", style = MaterialTheme.typography.labelMedium, color = Slate600)
                val tithiOptions = (1..15).map { it.toString() } + "Other"
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(tithiOptions) { t ->
                        FilterChip(
                            selected = state.tithi == t,
                            onClick  = { vm.updateTithi(t) },
                            label    = { Text(t, style = MaterialTheme.typography.labelSmall) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Amber500.copy(alpha = 0.15f),
                                selectedLabelColor = Amber500)
                        )
                    }
                }
                errors["tithi"]?.let { Text(it, style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error) }
                FormChipSelector("Tithi Element *", Element.values().toList(), state.tithiElement,
                    vm::updateTithiElement, displayName = { it.displayName() },
                    chipColor = { e -> when(e) { Element.AIR -> ElementAir; Element.FIRE -> ElementFire; Element.SPACE -> ElementSpace; Element.EARTH -> ElementEarth; Element.WATER -> ElementWater; else -> Slate400 } },
                    error = errors["tithiElement"])
            }
        }
        item {
            // Nadi/Element match indicator
            val nadiMatch = state.patientNadi?.displayName()?.equals(state.doctorNadi, ignoreCase = true) == true
            val elemMatch = state.patientElement?.displayName()?.equals(state.doctorElementBefore, ignoreCase = true) == true
            Surface(shape = RoundedCornerShape(12.dp),
                    color = if (nadiMatch && elemMatch) Emerald500.copy(alpha=0.08f) else Amber500.copy(alpha=0.08f)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Alignment Check", style = MaterialTheme.typography.labelMedium, color = Slate600)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(if (nadiMatch) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                             null, tint = if (nadiMatch) Emerald500 else Amber500,
                             modifier = Modifier.size(16.dp))
                        Text("Nadi ${if (nadiMatch) "aligned" else "differs"}",
                             style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(if (elemMatch) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                             null, tint = if (elemMatch) Emerald500 else Amber500,
                             modifier = Modifier.size(16.dp))
                        Text("Element ${if (elemMatch) "aligned" else "differs"}",
                             style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
```

### File: `presentation/visit/steps/Step5ClinicalNotes.kt`

```kotlin
@Composable
fun Step5ClinicalNotes(state: VisitFormState, errors: Map<String, String>, vm: VisitFormViewModel) {
    // Chief Complaint large multiline field with voice-to-text mic button (same pattern as Step 2)
    // Prescription large multiline field with mic button
    // Disease Categories: TagInputField with common suggestions
    //
    // Common disease category suggestions:
    // listOf("Respiratory","Digestive","Neurological","Musculoskeletal","Cardiovascular",
    //        "Dermatological","Endocrine","Urological","Gynecological","Psychiatric",
    //        "Ophthalmic","ENT","Dental","Pediatric","Oncological","Autoimmune")
    //
    // Two FormCards:
    //   Card 1 "Chief Complaint" (accent Rose500) — complaint field + error
    //   Card 2 "Prescription & Categories" (accent Emerald500) — prescription + disease tags
    val speechLauncherComplaint = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return@rememberLauncherForActivityResult
        vm.updateChiefComplaint(state.chiefComplaint + " " + text)
    }
    val speechLauncherPrescription = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return@rememberLauncherForActivityResult
        vm.updatePrescription(state.prescription + " " + text)
    }
    fun makeSpeechIntent(prompt: String) = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, prompt)
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            FormCard("Chief Complaint", accentColor = Rose500) {
                errors["complaint"]?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Rose500)
                }
                OutlinedTextField(
                    value = state.chiefComplaint, onValueChange = vm::updateChiefComplaint,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                    placeholder = { Text("Describe the chief complaint…") },
                    minLines = 5, isError = errors["complaint"] != null,
                    trailingIcon = {
                        IconButton(onClick = { speechLauncherComplaint.launch(makeSpeechIntent("Speak complaint…")) }) {
                            Icon(Icons.Rounded.Mic, "Dictate", tint = Rose500)
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }
        item {
            FormCard("Prescription & Categories", accentColor = Emerald500) {
                OutlinedTextField(
                    value = state.prescription, onValueChange = vm::updatePrescription,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                    placeholder = { Text("Enter prescription / treatment plan…") },
                    minLines = 5, label = { Text("Prescription") },
                    trailingIcon = {
                        IconButton(onClick = { speechLauncherPrescription.launch(makeSpeechIntent("Speak prescription…")) }) {
                            Icon(Icons.Rounded.Mic, "Dictate", tint = Emerald500)
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                )
                TagInputField(
                    label = "Disease Categories",
                    tags = state.diseaseCategories, onTagsChange = vm::updateDiseaseCategories,
                    suggestions = listOf("Respiratory","Digestive","Neurological","Musculoskeletal",
                        "Cardiovascular","Dermatological","Endocrine","Urological",
                        "Gynecological","Psychiatric","Ophthalmic","ENT","Pediatric","Autoimmune")
                )
            }
        }
    }
}
```

### File: `presentation/visit/steps/Step6MediaCustom.kt`

```kotlin
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun Step6MediaCustom(state: VisitFormState, vm: VisitFormViewModel) {
    val context = LocalContext.current
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)

    // Camera: capture image → save to app's files dir → add path
    var tempCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) tempCameraUri?.path?.let { vm.addMediaPath(it) }
    }

    // Gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        // Copy to internal storage to ensure persistence
        val inputStream = context.contentResolver.openInputStream(uri) ?: return@rememberLauncherForActivityResult
        val fileName = "media_${System.currentTimeMillis()}.jpg"
        val dest = java.io.File(context.filesDir, "media").also { it.mkdirs() }
        val destFile = java.io.File(dest, fileName)
        inputStream.use { input -> destFile.outputStream().use { output -> input.copyTo(output) } }
        vm.addMediaPath(destFile.absolutePath)
    }

    fun launchCamera() {
        val dir = java.io.File(context.filesDir, "media").also { it.mkdirs() }
        val file = java.io.File(dir, "cam_${System.currentTimeMillis()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        tempCameraUri = uri
        cameraLauncher.launch(uri)
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

        item {
            FormCard("Media Attachments", accentColor = Cyan500) {
                // Media action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (cameraPermission.status.isGranted) launchCamera()
                            else cameraPermission.launchPermissionRequest()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.CameraAlt, null, modifier = Modifier.size(16.dp))
                        Spacer(4.dp)
                        Text("Camera")
                    }
                    OutlinedButton(onClick = { galleryLauncher.launch("image/*") },
                                   modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.PhotoLibrary, null, modifier = Modifier.size(16.dp))
                        Spacer(4.dp)
                        Text("Gallery")
                    }
                }

                // Preview grid
                if (state.mediaPaths.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.mediaPaths) { path ->
                            Box {
                                AsyncImage(
                                    model = java.io.File(path),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(90.dp).clip(RoundedCornerShape(8.dp))
                                )
                                IconButton(
                                    onClick = { vm.removeMediaPath(path) },
                                    modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
                                        .background(Rose500.copy(alpha=0.9f), CircleShape)
                                ) {
                                    Icon(Icons.Rounded.Close, "Remove", tint = Color.White,
                                         modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            FormCard("Custom Fields", accentColor = Purple600) {
                Text("Add any additional data fields specific to this visit.",
                     style = MaterialTheme.typography.bodySmall, color = Slate600)

                state.customFields.forEach { field ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(field.label, style = MaterialTheme.typography.labelSmall, color = Slate600)
                            OutlinedTextField(
                                value = field.value,
                                onValueChange = { vm.updateCustomFieldValue(field.id, it) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = when (field.type) {
                                        FieldType.NUMBER -> KeyboardType.Number
                                        FieldType.DATE   -> KeyboardType.Text
                                        else             -> KeyboardType.Text
                                    }
                                )
                            )
                        }
                        IconButton(onClick = { vm.removeCustomField(field.id) }) {
                            Icon(Icons.Rounded.Delete, "Remove field", tint = Rose500)
                        }
                    }
                }

                // Add custom field button → dialog
                var showAddDialog by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(4.dp)
                    Text("Add Custom Field")
                }

                if (showAddDialog) {
                    AddCustomFieldDialog(
                        onAdd = { label, type ->
                            vm.addCustomField(CustomField(
                                id = java.util.UUID.randomUUID().toString(),
                                label = label, type = type, value = ""
                            ))
                            showAddDialog = false
                        },
                        onDismiss = { showAddDialog = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddCustomFieldDialog(onAdd: (String, FieldType) -> Unit, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf("") }
    var type  by remember { mutableStateOf(FieldType.TEXT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Field") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it },
                                  label = { Text("Field Label") }, singleLine = true,
                                  modifier = Modifier.fillMaxWidth())
                FormChipSelector("Type", FieldType.values().toList(), type, { type = it },
                                 displayName = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } })
            }
        },
        confirmButton = {
            Button(onClick = { if (label.isNotBlank()) onAdd(label, type) },
                   enabled = label.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
```

### File: `presentation/visit/steps/Step7Timing.kt`

```kotlin
@Composable
fun Step7Timing(state: VisitFormState, vm: VisitFormViewModel, isSaving: Boolean, onSubmit: () -> Unit) {
    // Visit date/time: DateTimePickerField — defaults to now if not set
    // Followup date/time: DateTimePickerField (optional, clearable)
    // Submit button: large gradient button
    // Summary card: shows all key values from all 7 steps as a review before submitting
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            FormCard("Visit Timing", accentColor = Indigo600) {
                DateTimePickerField("Visit Date & Time *", state.visitDateTimeMs, vm::updateVisitDateTime)
                DateTimePickerField("Next Followup (optional)", state.followupDateTimeMs,
                                    onDateTimeSelected = vm::updateFollowupDateTime, isOptional = true)
            }
        }
        item {
            // Quick summary review card
            FormCard("Review Summary", accentColor = Slate600) {
                SummaryRow("Patient",  state.name.ifBlank { "–" })
                SummaryRow("Age / Gender", "${state.age.ifBlank{"–"}} / ${state.gender?.name ?: "–"}")
                SummaryRow("Nadi",  state.patientNadi?.displayName() ?: "–")
                SummaryRow("Element", state.patientElement?.displayName() ?: "–")
                SummaryRow("Mandala", state.patientMandala?.name ?: "–")
                SummaryRow("Dr. Nadi", state.doctorNadi.ifBlank { "–" })
                SummaryRow("Paksha / Tithi", "${state.paksha?.displayName() ?: "–"} / ${state.tithi.ifBlank{"–"}}")
                if (state.chiefComplaint.isNotBlank()) {
                    SummaryRow("Complaint", state.chiefComplaint.stripHtml().take(80))
                }
                if (state.diseaseCategories.isNotEmpty()) {
                    SummaryRow("Categories", state.diseaseCategories.joinToString(", "))
                }
            }
        }
        item {
            // Submit button
            Button(
                onClick = onSubmit,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo600),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(8.dp)
                    Text("Saving…")
                } else {
                    Icon(Icons.Rounded.Save, null)
                    Spacer(8.dp)
                    Text("Save Record", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Slate400, modifier = Modifier.weight(0.4f))
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.6f),
             maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    Divider(color = Slate200, modifier = Modifier.padding(vertical = 4.dp))
}
```

---

## TASK 6 — VISIT FORM HOST SCREEN
### File: `presentation/visit/VisitFormScreen.kt`

```kotlin
@Composable
fun VisitFormScreen(
    navController: NavController,
    vm: VisitFormViewModel = hiltViewModel()
) {
    val formState    by vm.formState.collectAsStateWithLifecycle()
    val currentStep  by vm.currentStep.collectAsStateWithLifecycle()
    val stepErrors   by vm.stepErrors.collectAsStateWithLifecycle()
    val isSaving     by vm.isSaving.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Handle one-off events
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is VisitFormViewModel.UiEvent.ShowSnackbar ->
                    scope.launch { snackbarHostState.showSnackbar(event.message, duration = SnackbarDuration.Short) }
                is VisitFormViewModel.UiEvent.SaveSuccess  -> navController.popBackStack()
                is VisitFormViewModel.UiEvent.NavigateBack -> navController.popBackStack()
            }
        }
    }

    val stepTitles = listOf("Patient", "History", "Evaluation", "Assessment", "Notes", "Media", "Timing")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { if (currentStep > 0) vm.goBack() else navController.popBackStack() }) {
                            Icon(if (currentStep > 0) Icons.Rounded.ArrowBack else Icons.Rounded.Close, "Back")
                        }
                    },
                    title = {
                        Column {
                            Text(stepTitles.getOrNull(currentStep) ?: "Visit",
                                 style = MaterialTheme.typography.titleMedium)
                            Text("Step ${currentStep + 1} of 7",
                                 style = MaterialTheme.typography.labelSmall, color = Slate400)
                        }
                    },
                    actions = {
                        // Autosave indicator
                        TextButton(onClick = { /* manual save draft */ }) {
                            Icon(Icons.Rounded.CloudDone, null,
                                 modifier = Modifier.size(16.dp), tint = Emerald500)
                            Spacer(4.dp)
                            Text("Saved", style = MaterialTheme.typography.labelSmall, color = Emerald500)
                        }
                    }
                )
                // Progress bar
                FormStepProgressBar(
                    totalSteps   = 7,
                    currentStep  = currentStep,
                    stepTitles   = stepTitles,
                    onStepClick  = vm::goToStep,
                    modifier     = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        },
        bottomBar = {
            // Bottom navigation buttons (Back + Next/Save)
            Surface(shadowElevation = 8.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (currentStep > 0) {
                        OutlinedButton(
                            onClick = vm::goBack,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.ChevronLeft, null, modifier = Modifier.size(16.dp))
                            Spacer(4.dp)
                            Text("Back")
                        }
                    }
                    if (currentStep < 6) {
                        Button(
                            onClick = { vm.tryAdvance() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
                        ) {
                            Text("Next")
                            Spacer(4.dp)
                            Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(16.dp))
                        }
                    }
                    // On step 7 the Submit button is inside Step7Timing itself
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            val isEditingExisting = vm.existingPatient.collectAsStateWithLifecycle().value != null

            when (currentStep) {
                0 -> Step1PatientInfo(formState, stepErrors, vm, isEditingExisting)
                1 -> Step2MedicalHistory(formState, vm)
                2 -> Step3YogicEvaluation(formState, stepErrors, vm)
                3 -> Step4DoctorAssessment(formState, stepErrors, vm)
                4 -> Step5ClinicalNotes(formState, stepErrors, vm)
                5 -> Step6MediaCustom(formState, vm)
                6 -> Step7Timing(formState, vm, isSaving, onSubmit = vm::submit)
            }

            LoadingOverlay(isLoading = isSaving)
        }
    }
}
```

---

## TASK 7 — APPOINTMENTS VIEWMODEL
### File: `presentation/appointments/AppointmentViewModel.kt`

```kotlin
@HiltViewModel
class AppointmentViewModel @Inject constructor(
    private val appointmentRepo: AppointmentRepository,
    private val patientRepo: PatientRepository,
    @IoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    // Join appointments with patient names for display
    data class AppointmentWithName(val appointment: Appointment, val patientName: String)

    private fun List<Appointment>.withNames(patients: List<Patient>): List<AppointmentWithName> {
        val map = patients.associateBy { it.id }
        return map { a -> AppointmentWithName(a, map[a.patientId]?.name ?: "Unknown") }
    }

    private val allPatients = patientRepo.getAllPatients()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todaysAppointments: StateFlow<List<AppointmentWithName>> = combine(
        appointmentRepo.getTodaysAppointments(todayStartMs(), todayEndMs()),
        allPatients
    ) { appts, patients -> appts.withNames(patients) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val upcomingAppointments: StateFlow<List<AppointmentWithName>> = combine(
        appointmentRepo.getUpcomingAppointments(),
        allPatients
    ) { appts, patients ->
        appts.filter { it.dateTime.toEpochMilliseconds() >= todayEndMs() }.withNames(patients)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pastAppointments: StateFlow<List<AppointmentWithName>> = combine(
        appointmentRepo.getPastAppointments(),
        allPatients
    ) { appts, patients -> appts.withNames(patients) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // New appointment form state
    data class NewAppointmentState(
        val patientId: Long? = null,
        val patientName: String = "",
        val patientSearchQuery: String = "",
        val dateTimeMs: Long? = null,
        val purpose: String = "",
        val notes: String = "",
        val setReminder: Boolean = true
    )
    private val _newApptState = MutableStateFlow(NewAppointmentState())
    val newApptState: StateFlow<NewAppointmentState> = _newApptState.asStateFlow()

    val patientSearchResults: StateFlow<List<Patient>> = combine(
        _newApptState.map { it.patientSearchQuery }.distinctUntilChanged().debounce(300),
        allPatients
    ) { q, all ->
        if (q.isBlank()) all.take(8)
        else all.filter { it.name.contains(q, ignoreCase = true) || it.mobile.contains(q) }.take(8)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updatePatientSearch(q: String) { _newApptState.update { it.copy(patientSearchQuery = q) } }
    fun selectPatient(p: Patient)      { _newApptState.update { it.copy(patientId = p.id, patientName = p.name, patientSearchQuery = p.name) } }
    fun updateDateTime(ms: Long)       { _newApptState.update { it.copy(dateTimeMs = ms) } }
    fun updatePurpose(v: String)       { _newApptState.update { it.copy(purpose = v) } }
    fun updateNotes(v: String)         { _newApptState.update { it.copy(notes = v) } }
    fun toggleReminder()               { _newApptState.update { it.copy(setReminder = !it.setReminder) } }

    fun scheduleAppointment(onDone: () -> Unit) {
        val s = _newApptState.value
        val patientId = s.patientId ?: return
        val dateTimeMs = s.dateTimeMs ?: return
        viewModelScope.launch(io) {
            val id = appointmentRepo.insertAppointment(
                Appointment(
                    patientId = patientId,
                    dateTime  = kotlinx.datetime.Instant.fromEpochMilliseconds(dateTimeMs),
                    purpose   = s.purpose.ifBlank { null },
                    notes     = s.notes.ifBlank { null }
                )
            )
            // Reset form
            _newApptState.value = NewAppointmentState()
            withContext(kotlinx.coroutines.Dispatchers.Main) { onDone() }
        }
    }

    fun markCompleted(id: Long) = viewModelScope.launch(io) { appointmentRepo.markCompleted(id) }
    fun markCancelled(id: Long) = viewModelScope.launch(io) { appointmentRepo.markCancelled(id) }
    fun deleteAppointment(appt: Appointment) = viewModelScope.launch(io) { appointmentRepo.deleteAppointment(appt) }

    private fun todayStartMs(): Long {
        val tz    = java.util.TimeZone.getDefault()
        val cal   = java.util.Calendar.getInstance(tz)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0);       cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
    private fun todayEndMs() = todayStartMs() + 86_400_000L
}
```

---

## TASK 8 — APPOINTMENTS SCREEN
### File: `presentation/appointments/AppointmentsScreen.kt`

```kotlin
@Composable
fun AppointmentsScreen(
    navController: NavController,
    vm: AppointmentViewModel = hiltViewModel()
) {
    val todayAppts    by vm.todaysAppointments.collectAsStateWithLifecycle()
    val upcomingAppts by vm.upcomingAppointments.collectAsStateWithLifecycle()
    val pastAppts     by vm.pastAppointments.collectAsStateWithLifecycle()
    var showSchedule  by remember { mutableStateOf(false) }
    var selectedTab   by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appointments", style = MaterialTheme.typography.headlineMedium,
                               fontFamily = CormorantGaramond) },
                actions = {
                    // Count badge on today tab
                    BadgedBox(badge = {
                        if (todayAppts.isNotEmpty()) Badge { Text(todayAppts.size.toString()) }
                    }) {
                        Icon(Icons.Rounded.Today, null)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Schedule") },
                icon = { Icon(Icons.Rounded.Add, null) },
                onClick = { showSchedule = true },
                containerColor = Indigo600,
                contentColor = Color.White
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            // Tab row
            TabRow(selectedTabIndex = selectedTab) {
                listOf("Today (${todayAppts.size})", "Upcoming", "Past").forEachIndexed { i, title ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i },
                        text = { Text(title, style = MaterialTheme.typography.titleSmall) })
                }
            }

            when (selectedTab) {
                0 -> AppointmentList(
                    appointments = todayAppts,
                    emptyMessage = "No appointments today",
                    navController = navController, vm = vm, isToday = true
                )
                1 -> AppointmentList(
                    appointments = upcomingAppts,
                    emptyMessage = "No upcoming appointments",
                    navController = navController, vm = vm, isToday = false
                )
                2 -> AppointmentList(
                    appointments = pastAppts,
                    emptyMessage = "No past appointments",
                    navController = navController, vm = vm, isToday = false,
                    isPast = true
                )
            }
        }
    }

    // Schedule modal
    if (showSchedule) {
        ScheduleAppointmentSheet(vm = vm, onDismiss = { showSchedule = false })
    }
}

@Composable
private fun AppointmentList(
    appointments: List<AppointmentViewModel.AppointmentWithName>,
    emptyMessage: String,
    navController: NavController,
    vm: AppointmentViewModel,
    isToday: Boolean,
    isPast: Boolean = false
) {
    if (appointments.isEmpty()) {
        EmptyState(icon = Icons.Rounded.EventAvailable, message = emptyMessage,
                   modifier = Modifier.fillMaxSize())
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp),
                   verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(appointments, key = { it.appointment.id }) { item ->
                AppointmentCard(
                    item = item, isToday = isToday, isPast = isPast,
                    onPatientTap = { navController.navigate("patients/${item.appointment.patientId}") },
                    onStartVisit = { navController.navigate("visit/new?patientId=${item.appointment.patientId}") },
                    onComplete   = { vm.markCompleted(item.appointment.id) },
                    onCancel     = { vm.markCancelled(item.appointment.id) },
                    onDelete     = { vm.deleteAppointment(item.appointment) }
                )
            }
        }
    }
}

@Composable
private fun AppointmentCard(
    item: AppointmentViewModel.AppointmentWithName,
    isToday: Boolean,
    isPast: Boolean,
    onPatientTap: () -> Unit,
    onStartVisit: () -> Unit,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val appt = item.appointment
    val ldt  = appt.dateTime.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().alpha(if (isPast) 0.65f else 1f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row {
            // Date block (left accent)
            Box(
                Modifier.width(64.dp).fillMaxHeight()
                    .background(
                        brush = if (isToday) Brush.verticalGradient(listOf(Indigo600, Purple600))
                                else Brush.verticalGradient(listOf(Slate200, Slate200))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(ldt.month.name.take(3).uppercase(),
                         style = MaterialTheme.typography.labelSmall,
                         color = if (isToday) Color.White else Slate600)
                    Text("${ldt.dayOfMonth}",
                         style = MaterialTheme.typography.headlineSmall,
                         color = if (isToday) Color.White else Slate800,
                         fontFamily = CormorantGaramond)
                    Text("%02d:%02d".format(ldt.hour, ldt.minute),
                         style = MaterialTheme.typography.labelSmall,
                         color = if (isToday) Color.White.copy(0.8f) else Slate400)
                }
            }

            // Content
            Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(item.patientName, style = MaterialTheme.typography.titleSmall,
                         modifier = Modifier.clickable(onClick = onPatientTap))
                    // Status badge
                    val (statusColor, statusLabel) = when (appt.status) {
                        AppointmentStatus.SCHEDULED  -> Indigo600 to "Scheduled"
                        AppointmentStatus.COMPLETED  -> Emerald500 to "Done"
                        AppointmentStatus.CANCELLED  -> Rose500 to "Cancelled"
                    }
                    Surface(color = statusColor.copy(alpha=0.12f), shape = RoundedCornerShape(4.dp)) {
                        Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor,
                             modifier = Modifier.padding(horizontal=6.dp, vertical=2.dp))
                    }
                }
                appt.purpose?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Slate600) }
                appt.notes?.let   { Text(it, style = MaterialTheme.typography.bodySmall, color = Slate400, maxLines = 1) }

                // Action buttons (only for scheduled)
                if (appt.status == AppointmentStatus.SCHEDULED) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Start visit
                        FilledTonalButton(onClick = onStartVisit,
                                          contentPadding = PaddingValues(horizontal=10.dp, vertical=4.dp)) {
                            Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(14.dp))
                            Spacer(2.dp)
                            Text("Start", style = MaterialTheme.typography.labelSmall)
                        }
                        // Complete
                        IconButton(onClick = onComplete, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.CheckCircle, "Complete", tint = Emerald500,
                                 modifier = Modifier.size(18.dp))
                        }
                        // Cancel
                        IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Cancel, "Cancel", tint = Amber500,
                                 modifier = Modifier.size(18.dp))
                        }
                        // Delete
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Delete, "Delete", tint = Rose500,
                                 modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleAppointmentSheet(vm: AppointmentViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state      by vm.newApptState.collectAsStateWithLifecycle()
    val results    by vm.patientSearchResults.collectAsStateWithLifecycle()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(16.dp).navigationBarsPadding(),
               verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Schedule Appointment", style = MaterialTheme.typography.titleLarge)

            // Patient search
            OutlinedTextField(
                value = state.patientSearchQuery,
                onValueChange = vm::updatePatientSearch,
                label = { Text("Patient") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )
            // Search results dropdown
            if (results.isNotEmpty() && state.patientId == null) {
                LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
                    items(results) { p ->
                        ListItem(
                            headlineContent = { Text(p.name) },
                            supportingContent = { Text(p.mobile) },
                            leadingContent = { PatientAvatar(p.name, null, 36.dp) },
                            modifier = Modifier.clickable { vm.selectPatient(p) }
                        )
                        Divider()
                    }
                }
            }

            DateTimePickerField("Date & Time", state.dateTimeMs, vm::updateDateTime)

            OutlinedTextField(value = state.purpose, onValueChange = vm::updatePurpose,
                              label = { Text("Purpose") }, singleLine = true,
                              modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))

            OutlinedTextField(value = state.notes, onValueChange = vm::updateNotes,
                              label = { Text("Notes (optional)") }, minLines = 2,
                              modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Set Reminder (30 min before)", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = state.setReminder, onCheckedChange = { vm.toggleReminder() })
            }

            Button(
                onClick = { vm.scheduleAppointment { onDismiss() } },
                enabled = state.patientId != null && state.dateTimeMs != null,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo600),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Schedule Appointment", style = MaterialTheme.typography.titleSmall) }

            Spacer(Modifier.height(8.dp))
        }
    }
}
```

---

## TASK 9 — MANIFEST & FILEPROVIDER

### UPDATE: `AndroidManifest.xml`

Add inside `<manifest>`:
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

Add inside `<application>`:
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

### New file: `res/xml/file_paths.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <files-path name="media" path="media/" />
</paths>
```

---

## TASK 10 — UPDATE NAVHOST
### UPDATE: `presentation/navigation/NavHost.kt`

Replace the placeholder `"patients/new"` composable and add visit form routes:

```kotlin
// New patient form = visit form with patientId = -1 (new patient flow)
composable(
    route = "patients/new",
) {
    VisitFormScreen(navController = navController)
}

// Visit form for existing patient
composable(
    route = "visit/new?patientId={patientId}",
    arguments = listOf(navArgument("patientId") {
        type = NavType.LongType; defaultValue = -1L
    })
) {
    VisitFormScreen(navController = navController)
}

// Edit existing visit
composable(
    route = "visit/{visitId}/edit",
    arguments = listOf(
        navArgument("visitId")   { type = NavType.LongType },
        navArgument("patientId") { type = NavType.LongType }
    )
) {
    VisitFormScreen(navController = navController)
}

// Appointments screen
composable("appointments") {
    AppointmentsScreen(navController = navController)
}
```

---

## COMPLETION CHECKLIST

- [ ] `DraftVisit` added to `@Database` entities list, version bumped to 2
- [ ] `MIGRATION_1_2` added to `DatabaseModule` builder chain
- [ ] `VisitFormState` is `@Serializable` and all nested types (`Mandala`, `Nadi`, etc.) are also `@Serializable` — if enums are not yet annotated, add `@Serializable` to each enum in `Enums.kt`
- [ ] `FormChipSelector` uses `LazyRow` not `FlowRow` (avoids double-scroll conflicts in LazyColumn)
- [ ] `Step6MediaCustom` references `FileProvider` with the correct authority `"${context.packageName}.fileprovider"`
- [ ] `DateTimePickerField` modifier chain: the `Modifier.clickable` is placed BEFORE `menuAnchor` to avoid intercept conflict
- [ ] Camera permission requested via `rememberPermissionState` before launching camera intent
- [ ] `VisitFormViewModel.submit()` handles the `patientId == -1L && editVisitId == null` branch (new patient + new visit)
- [ ] `AppointmentViewModel` `todayStartMs()` / `todayEndMs()` are called inside coroutines, not at declaration time
- [ ] All new screens added to `NavHost` with correct argument types
- [ ] `@Serializable` annotation added to `CustomField`, `MediaEntry`, `FieldType` in `SupportingModels.kt` if not already present
