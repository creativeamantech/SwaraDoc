# SwaraPulse Android — Complete App Blueprint
### Swara Yoga Patient Management · Native Android

---

## 1. Technology Stack

| Layer | Choice | Reason |
|---|---|---|
| Language | **Kotlin** | Modern, null-safe, concise |
| UI | **Jetpack Compose** | Declarative, animation-native, Material 3 |
| Architecture | **MVVM + Clean Architecture** | Testable, separation of concerns |
| Local DB | **Room (SQLite)** | Typed ORM, migrations, LiveData support |
| Navigation | **Navigation Compose** | Type-safe composable routes |
| DI | **Hilt** | Scoped injection, ViewModel support |
| Async | **Kotlin Coroutines + Flow** | Reactive DB updates, background I/O |
| Export | **Apache POI (XLSX) + JSON** | Excel + full backup |
| Rich Text | **Compose Richtext / Markwon** | Formatted notes |
| Charts | **Vico Charts** | Beautiful animated charts for Compose |
| Image | **Coil** | Async image loading |
| Biometrics | **BiometricPrompt API** | Fingerprint / Face unlock instead of PIN |
| Date/Time | **kotlinx-datetime** | Multiplatform-ready date ops |
| Notifications | **WorkManager** | Follow-up & appointment reminders |
| Preferences | **DataStore (Proto)** | Typed settings storage |
| PDF | **Android PdfDocument API** | Export patient records as PDF |

---

## 2. Database Schema (Room)

### 2.1 `patients` table
```kotlin
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
    val category: List<String> = emptyList(),   // TypeConverter → JSON
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
```

### 2.2 `visits` table
```kotlin
@Entity(
    tableName = "visits",
    foreignKeys = [ForeignKey(entity = Patient::class, 
        parentColumns = ["id"], childColumns = ["patientId"],
        onDelete = CASCADE)]
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
    val tithi: String,                  // "1"–"15" or "Other"
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
```

### 2.3 `appointments` table
```kotlin
@Entity(
    tableName = "appointments",
    foreignKeys = [ForeignKey(entity = Patient::class,
        parentColumns = ["id"], childColumns = ["patientId"],
        onDelete = CASCADE)]
)
data class Appointment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val dateTime: Instant,
    val purpose: String? = null,
    val notes: String? = null,
    val status: AppointmentStatus = SCHEDULED,  // SCHEDULED, COMPLETED, CANCELLED
    val createdAt: Instant = Clock.System.now()
)
```

### 2.4 Supporting Types (TypeConverters)
```kotlin
enum class Nadi { IDA, PINGALA, SUSHUMNA, SHIFT_IDA_PINGALA, SHIFT_PINGALA_IDA, OTHER }
enum class Element { AIR, FIRE, SPACE, EARTH, WATER, OTHER }
enum class Mandala { IDA, PINGALA, OTHER }
enum class Paksha { SHUKLA, KRISHNA }
enum class PatientSide { RIGHT, LEFT, FRONT }
enum class Gender { MALE, FEMALE, OTHER }

data class MediaEntry(val filePath: String, val date: Instant)
data class CustomField(val id: String, val label: String, 
                       val type: FieldType, val value: String)
enum class FieldType { TEXT, NUMBER, DATE }
```

---

## 3. App Architecture

```
app/
├── data/
│   ├── db/          ← Room DB, DAOs, TypeConverters
│   ├── repository/  ← PatientRepo, VisitRepo, AppointmentRepo
│   └── export/      ← ExcelExporter, JsonBackup, PdfExporter
├── domain/
│   ├── model/       ← Domain models (separate from DB entities)
│   ├── usecase/     ← Business logic (SearchPatients, GetAnalytics…)
│   └── mapper/      ← Entity ↔ Domain mappers
├── presentation/
│   ├── ui/
│   │   ├── theme/        ← MaterialTheme, Typography, Colors
│   │   ├── components/   ← Reusable Compose components
│   │   ├── dashboard/
│   │   ├── patients/
│   │   ├── visits/
│   │   ├── appointments/
│   │   ├── analytics/
│   │   └── settings/
│   └── viewmodel/
└── di/              ← Hilt modules
```

---

## 4. Navigation Map

```
NavGraph:
  AUTH
    └── /auth              (Biometric / PIN lock screen)

  MAIN (BottomNav)
    ├── /dashboard
    ├── /patients
    │     ├── /patients/{id}
    │     │     ├── overview
    │     │     ├── visits
    │     │     ├── trends
    │     │     └── media
    │     └── /patients/new
    ├── /appointments
    ├── /analytics
    └── /settings

  MODAL (Full-screen dialogs)
    ├── /visit/new?patientId={id}
    ├── /visit/{visitId}/edit
    └── /appointment/new?patientId={id}
```

**Bottom Navigation Tabs:**
| Icon | Label | Badge |
|---|---|---|
| Home | Dashboard | — |
| People | Patients | count |
| Calendar | Appointments | today count |
| BarChart | Analytics | — |
| Settings | Settings | — |

---

## 5. Screen-by-Screen Plan

---

### 5.1 Auth Screen
- **Biometric first** (fingerprint / face) using `BiometricPrompt`
- Fallback to 6-digit PIN (hashed with SHA-256, stored in DataStore)
- Setup mode for first launch
- Beautiful full-screen gradient background with pulsing mandala SVG animation
- "SwaraPulse" wordmark with subtle glow

---

### 5.2 Dashboard
**Top Bar:** App logo + practitioner name + dark mode toggle + notification bell

**Content (LazyColumn):**

1. **Greeting Card** — Time-aware ("Good morning, Dr. …") with today's date in Hindu calendar alongside Gregorian
2. **Stats Row (horizontal scroll):**
   - Active Patients · Total Visits · This Month · Pending Followups
3. **Today's Appointments** — Horizontal card strip; each card shows time, patient name, purpose; tap → start visit
4. **Upcoming Followups (7 days)** — Timeline-style list with color-coded urgency
5. **Recent Activity** — Last 5 visits with patient avatar, name, Nadi/Element chips, time-ago
6. **Quick Actions FAB** — Expandable: New Patient · New Visit · New Appointment

---

### 5.3 Patients List
**Top Bar:** Search field (always visible) + filter icon + view mode toggle (grid/list)

**Filter Sheet (bottom sheet):**
- Status: All / Active / Inactive / Provisional
- Sort: Name A–Z / Recent / Oldest / Most Visits
- Gender filter chips
- Blood group filter

**Grid View:** 2-column cards
- Gradient avatar circle with initial
- Name, Age/Gender chip, Last visit
- Nadi + Element badges
- Long-press → contextual menu (Edit, New Visit, Export, Delete)

**List View:** Single-row items with swipe actions
- Swipe right → New Visit (green)
- Swipe left → Delete (red, with confirm)

**FAB:** + New Patient

---

### 5.4 Patient Detail
**Top App Bar:** Back + Edit + More (Export PDF, Export Excel, Share, Delete)

**Header Card (collapsible):**
- Large gradient avatar / profile photo (tap to change)
- Name (large, editable via tap)
- Age · Gender · Blood Group chips
- Mobile (tap to call) · Email (tap to mail)
- Address with Maps deep-link

**Quick Stats Strip:**
- Total Visits · Last Visit · Avg/month · Followup Rate

**Pager Tabs:**

#### Tab 1 — Overview
- Dominant Nadi & Element mini-chart
- Medical history (rich text render)
- Top complaints list
- Upcoming followup alert card (amber)
- Recent 3 visits preview

#### Tab 2 — Visits
- Search + filter bar
- Sort toggle
- Visit cards (expandable with animation):
  - Date/time chip + "Initial Visit" badge if first
  - Parameter chips: Mandala · Nadi · Element · Side
  - Doctor params · Tithi info
  - Complaint & Prescription (expandable rich text)
  - Disease category chips
  - Media thumbnails (tap → full-screen viewer)
  - Custom fields
  - Followup date
  - Edit button

#### Tab 3 — Trends
- Vico line chart: visits over 6 months
- Element distribution: animated pie / donut chart
- Nadi frequency bar chart
- Treatment insights row

#### Tab 4 — Media
- Staggered grid gallery
- Tap → full-screen with zoom + share
- Show date below each image

---

### 5.5 New / Edit Visit Form

**Form presented as a full-screen bottom sheet or separate screen**

Multi-step with progress indicator (7 steps):

| Step | Title | Fields |
|---|---|---|
| 1 | Patient | Name, Age, Gender, Mobile, Email, Address, Occupation, Blood Group, Category tags, Emergency contact |
| 2 | Medical History | Rich text editor |
| 3 | Yogic Evaluation | Mandala, Nadi, Element, Sitting Position |
| 4 | Doctor Assessment | Dr. Nadi, Dr. Element Before/After, Paksha, Tithi, Tithi Element |
| 5 | Clinical Notes | Chief Complaint (rich text), Prescription (rich text), Disease categories |
| 6 | Media & Custom | Image attachments (camera / gallery), Custom key-value fields |
| 7 | Timing | Visit datetime, Followup date, Save |

**UX Details:**
- Step indicator at top (segmented progress bar)
- Back/Next buttons + swipe to navigate steps
- Each step validates before advancing
- Autosave draft to Room every 30s
- Snackbar "Draft saved" confirmation
- Voice-to-text support on rich text fields (mic button)

---

### 5.6 Appointments

**Layout:** Vertical pager or tabs: Today · Upcoming · Past

**Appointment Card:**
- Date block with gradient (today = indigo, future = slate)
- Patient name (tap → patient detail)
- Time · Purpose · Notes
- Action buttons: ✓ Complete · ✗ Cancel · ▶ Start Visit · 🗑 Delete
- Status badge with color

**Schedule FAB → Bottom Sheet:**
- Patient search / select (with recent patients)
- Date & Time picker (Material 3 date-time picker)
- Purpose + Notes
- Set reminder toggle → creates WorkManager notification

---

### 5.7 Analytics

**Header:** Time filter chips — 30D · 90D · 6M · 1Y · All

**Content (LazyColumn):**

1. **Key Metrics Row:** 4 cards (Patients, Visits, Avg/patient, Complaints)
2. **Correlation Insights Card:** Nadi alignment %, Element alignment %, Tithi correlation — gorgeous gradient card
3. **Patient Characteristics:** 2×2 chart grid
   - Nadi distribution (horizontal bar)
   - Element distribution (donut)
   - Mandala types (pie)
   - Sitting position (bar)
4. **Doctor & Temporal Factors:** 2×2 chart grid
5. **Demographics:** Age group pyramid chart · Gender pie
6. **Top Complaints:** Ranked list with progress bars, medal icons for top 3
7. **Full Visit Table:** Scrollable data table with filter/sort, CSV export button

---

### 5.8 Settings

**Sections:**

1. **Profile** — Display name, title, profile photo, clinic name
2. **Security** — Change PIN, Toggle biometric, Session timeout
3. **Backup & Restore**
   - Backup to JSON (Downloads folder / Google Drive share)
   - Backup to Excel (multi-sheet)
   - Restore from JSON (with overwrite warning)
   - Auto-backup schedule (daily/weekly via WorkManager)
4. **Appearance** — Dark/Light/System theme, Accent color picker
5. **Notifications** — Followup reminder lead time, Appointment reminders
6. **Data** — Clear drafts, Storage usage, Export all as PDF
7. **About** — Version, Changelog, Licenses

---

## 6. Smart & Modern Android Features

### 6.1 Notifications (WorkManager)
```
FollowupReminderWorker:
  - Runs daily at 7:00 AM
  - Queries followup_date for next 2 days
  - Posts grouped notification per patient

AppointmentReminderWorker:
  - Fires 30 min before each appointment
  - Action buttons: "Start Visit" (deep links into app) | "Snooze 15min"
```

### 6.2 Widgets (Android App Widget)
- **Today's Summary Widget:** Appointment count + followup count, tap to open
- **Quick Add Widget:** One-tap to start new visit

### 6.3 Search (Jetpack App Search or SQLite FTS5)
```sql
CREATE VIRTUAL TABLE patients_fts USING fts5(
  name, mobile, email, address, chief_complaint, disease_categories
);
-- Triggers keep FTS in sync with main tables
```

### 6.4 Export Formats

| Format | Contents | Trigger |
|---|---|---|
| **JSON** | Full DB dump (all tables) | Settings → Backup |
| **Excel (.xlsx)** | Sheet per table + Analytics sheet | Patient detail / Settings |
| **PDF** | Single patient full report with formatted notes | Patient detail → Share |
| **CSV** | Visit records flat table | Analytics → Export |

### 6.5 Shortcuts (App Shortcuts API)
- **Dynamic:** "New Visit for [last patient]"
- **Static:** "Add Patient", "Today's Schedule"

### 6.6 Deep Links
```
swarapulse://patients/{id}
swarapulse://visits/new?patientId={id}
swarapulse://appointments/today
```

### 6.7 Backup to Cloud
- Share JSON backup via Android Sharesheet → user can save to Drive/Dropbox/email
- Auto-backup SAF (Storage Access Framework) to user-chosen folder

---

## 7. Design System

### Color Tokens
```kotlin
object SwaraPulseColors {
    // Light
    val primary = Color(0xFF4F46E5)        // Indigo 600
    val secondary = Color(0xFF9333EA)       // Purple 600
    val tertiary = Color(0xFF06B6D4)        // Cyan 500
    val surface = Color(0xFFF8FAFC)         // Slate 50
    val error = Color(0xFFF43F5E)           // Rose 500
    val warning = Color(0xFFF59E0B)         // Amber 500
    val success = Color(0xFF10B981)         // Emerald 500
    
    // Element colors (for chips/charts)
    val elementAir   = Color(0xFF7DD3FC)   // Sky 300
    val elementFire  = Color(0xFFFB7185)   // Rose 400
    val elementSpace = Color(0xFFC084FC)   // Purple 400
    val elementEarth = Color(0xFF86EFAC)   // Green 300
    val elementWater = Color(0xFF60A5FA)   // Blue 400
    
    // Nadi colors
    val nadiIda      = Color(0xFF818CF8)   // Indigo 400 (lunar/cool)
    val nadiPingala  = Color(0xFFFB923C)   // Orange 400 (solar/warm)
    val nadiSushumna = Color(0xFF34D399)   // Emerald 400 (central/balance)
}
```

### Typography
```kotlin
// Use Google Fonts: Cormorant Garamond (display) + DM Sans (body)
// Gives classical-meets-modern feel perfect for Swara Yoga context
val swaraPulseTypography = Typography(
    displayLarge = TextStyle(fontFamily = CormorantGaramond, 
                             fontSize = 40.sp, fontWeight = W300),
    headlineMedium = TextStyle(fontFamily = CormorantGaramond, 
                               fontSize = 24.sp, fontWeight = W600),
    bodyMedium = TextStyle(fontFamily = DmSans, fontSize = 14.sp),
    labelSmall = TextStyle(fontFamily = DmSans, fontSize = 11.sp, 
                           letterSpacing = 0.8.sp, fontWeight = W600)
)
```

### Component Library

#### NadiChip
```kotlin
@Composable fun NadiChip(nadi: Nadi, modifier: Modifier = Modifier)
// Color-coded chip with Moon icon (Ida), Sun icon (Pingala), ∞ (Sushumna)
```

#### ElementBadge
```kotlin
@Composable fun ElementBadge(element: Element)
// Each element has icon: 💨 Air, 🔥 Fire, ✨ Space, 🌍 Earth, 💧 Water
```

#### GradientCard
```kotlin
@Composable fun GradientCard(
    gradient: Brush,
    content: @Composable ColumnScope.() -> Unit
)
```

#### PatientAvatar
```kotlin
@Composable fun PatientAvatar(
    name: String,
    imagePath: String?,
    size: Dp = 48.dp
)
// Shows photo if available, else gradient circle with initial
```

#### SwipeableVisitCard
```kotlin
// Swipe right = Edit, Swipe left = Delete
// Expand animation reveals full details
```

#### MandalaWheelChart (Custom Canvas)
```kotlin
// Beautiful circular chart showing Element/Nadi distribution
// Drawn with Canvas API, animated on entry
```

---

## 8. Prompt Engineering for AI Coding Agents

Use the following structured prompt blocks to build this with **Jules / Gemini Code Assist / Cursor**:

---

### Prompt Block 1 — Project Setup
```
Create a new Android project named "SwaraPulse" with:
- Kotlin + Jetpack Compose + Material 3
- Min SDK 26, Target SDK 34
- Hilt for DI
- Room + Kotlin Coroutines + Flow
- Navigation Compose
- Vico Charts library
- Coil for image loading
- kotlinx-datetime
- Apache POI for Excel export
- Google Fonts: Cormorant Garamond + DM Sans

Setup base theme with the color tokens and typography defined in 
SwaraPulseColors and swaraPulseTypography. Implement dark mode toggle 
with DataStore preference.
```

---

### Prompt Block 2 — Database Layer
```
Create the Room database schema for SwaraPulse with three entities:
Patient, Visit, Appointment.

Include:
- TypeConverters for: List<String> (JSON), Instant (Long epoch ms), 
  all enum classes, List<MediaEntry>, List<CustomField>
- DAOs with Flow<List<T>> return types for reactive updates
- FTS5 virtual table for full-text search on patients + visits
- Database migrations scaffold
- Repository classes wrapping DAOs with coroutine dispatchers
```

---

### Prompt Block 3 — Auth Screen
```
Build the Auth screen composable for SwaraPulse:
- Uses BiometricPrompt API for fingerprint/face auth
- Fallback to 6-digit PIN entry with custom dot-indicator display
- PIN hashed with SHA-256, stored in DataStore<Preferences>
- First-launch setup flow: enter PIN → confirm PIN → save
- Animated SwaraPulse logo with pulsing glow on dark gradient background
- On success: navigate to Dashboard
```

---

### Prompt Block 4 — Dashboard Screen
```
Build the Dashboard screen with ViewModel + Compose UI:
- ViewModel exposes StateFlow<DashboardUiState> with: 
  patientCount, visitCount, monthVisits, upcomingFollowups (7 days), 
  todaysAppointments, recentVisits (last 5)
- UI: greeting card with time-aware message, horizontal stats strip, 
  today's appointments horizontal LazyRow, followup timeline list, 
  recent activity list, expandable FAB
- Animate numbers counting up on first load (CountUpText composable)
```

---

### Prompt Block 5 — Patient List + Detail
```
Build PatientListScreen and PatientDetailScreen:
- List: search with FTS5, filter bottom sheet, grid/list toggle, 
  swipe actions (new visit / delete)
- Detail: top bar with export options, collapsible header card, 
  HorizontalPager with 4 tabs (Overview, Visits, Trends, Media)
- Visits tab: expandable SwipeableVisitCard with all fields, 
  "Initial Visit" badge for first chronological visit
- Trends tab: Vico line chart (visits over time) + donut chart (elements) 
  + bar chart (nadi frequency)
- Media tab: LazyVerticalStaggeredGrid gallery, tap for zoomable full-screen
```

---

### Prompt Block 6 — Visit Form (Multi-step)
```
Build a 7-step visit form as a full-screen composable:
- Segmented progress bar at top (7 segments)
- Each step is a separate composable: 
  Step1PatientInfo, Step2MedicalHistory, Step3YogicEval, 
  Step4DoctorAssessment, Step5ClinicalNotes, Step6MediaCustom, Step7Timing
- Navigation: back/next buttons + HorizontalPager swipe
- Validation: each step validates on "Next" press using state holders
- Rich text: use Compose-Richtext library for complaint/prescription
- Media: camera intent + gallery picker, store to internal app storage, 
  save path in MediaEntry
- Autosave draft to Room as DraftVisit every 30 seconds
- On submit: save Visit + update patient.lastVisitDate + schedule 
  followup WorkManager task if followupDate set
```

---

### Prompt Block 7 — Appointments
```
Build AppointmentsScreen with:
- Tabs: Today / Upcoming / Past
- AppointmentCard with swipe-to-complete and swipe-to-cancel
- FAB → ModalBottomSheet for scheduling: patient search dropdown 
  (FTS5-backed), Material3 DateTimePicker, purpose/notes fields
- On save: schedule AppointmentReminderWorker for 30 min before
- "Start Visit" action deep-links to visit form pre-filled with patientId
```

---

### Prompt Block 8 — Analytics
```
Build AnalyticsScreen with:
- Time filter chips (30D, 90D, 6M, 1Y, All) updating StateFlow
- Correlation engine in ViewModel: compute Nadi alignment %, 
  Element alignment %, Tithi-Element correlation from Visit data
- Vico BarChart for distributions, donut for proportions
- Top complaints: parse chiefComplaint HTML → extract text → 
  tokenize → frequency count → top 10 list with progress bars
- Scrollable visit data table with horizontal scroll + sticky header
- Export table as CSV via ShareSheet
```

---

### Prompt Block 9 — Export System
```
Build export utilities:
1. PdfExporter: use Android PdfDocument API to generate patient report PDF
   - Page 1: patient info, yogic profile, stats
   - Pages 2+: each visit formatted
   - Share via FileProvider + ShareSheet

2. ExcelExporter: Apache POI XSSF
   - Sheet 1: Patient Info
   - Sheet 2: All Visits (one row per visit, all fields as columns)
   - Sheet 3: Analytics summary (element counts, nadi counts)
   - Save to Downloads, notify user

3. JsonBackup: serialize all Room data to JSON, save to SAF location
4. JsonRestore: parse JSON, clear DB, re-insert all records
```

---

### Prompt Block 10 — Notifications & Widgets
```
Build notification system:
1. FollowupReminderWorker (daily, 7 AM): 
   query visits where followupDate is within 48 hours, 
   post grouped notification with patient name
   
2. AppointmentReminderWorker (per appointment, 30 min before):
   post notification with "Start Visit" PendingIntent + "Snooze" action

3. GlanceAppWidget (Jetpack Glance):
   - TodaySummaryWidget: shows today's appointment count + followup count
   - Tap opens AppointmentsScreen via deep link
```

---

## 9. Project File Structure

```
SwaraPulse/
├── app/src/main/
│   ├── java/com/swarapulse/
│   │   ├── SwaraPulseApp.kt          ← Hilt application
│   │   ├── MainActivity.kt           ← NavHost setup
│   │   ├── data/
│   │   │   ├── db/
│   │   │   │   ├── SwaraPulseDatabase.kt
│   │   │   │   ├── entity/           ← Patient, Visit, Appointment
│   │   │   │   ├── dao/              ← PatientDao, VisitDao, AppointmentDao
│   │   │   │   └── converter/        ← TypeConverters
│   │   │   ├── repository/
│   │   │   │   ├── PatientRepository.kt
│   │   │   │   ├── VisitRepository.kt
│   │   │   │   └── AppointmentRepository.kt
│   │   │   ├── datastore/
│   │   │   │   └── SettingsDataStore.kt
│   │   │   └── export/
│   │   │       ├── PdfExporter.kt
│   │   │       ├── ExcelExporter.kt
│   │   │       └── JsonBackupManager.kt
│   │   ├── domain/
│   │   │   ├── model/                ← Domain models
│   │   │   ├── usecase/              ← Use cases
│   │   │   └── mapper/               ← Entity ↔ Domain mappers
│   │   ├── presentation/
│   │   │   ├── theme/
│   │   │   │   ├── Color.kt
│   │   │   │   ├── Type.kt
│   │   │   │   └── Theme.kt
│   │   │   ├── components/           ← Shared Compose components
│   │   │   ├── auth/                 ← AuthScreen + AuthViewModel
│   │   │   ├── dashboard/            ← DashboardScreen + ViewModel
│   │   │   ├── patients/             ← List, Detail, screens + VMs
│   │   │   ├── visit/                ← Form, steps + ViewModel
│   │   │   ├── appointments/         ← Screen + ViewModel
│   │   │   ├── analytics/            ← Screen + ViewModel
│   │   │   └── settings/             ← Screen + ViewModel
│   │   ├── worker/
│   │   │   ├── FollowupReminderWorker.kt
│   │   │   └── AppointmentReminderWorker.kt
│   │   ├── widget/
│   │   │   └── TodaySummaryWidget.kt
│   │   └── di/
│   │       ├── DatabaseModule.kt
│   │       ├── RepositoryModule.kt
│   │       └── ExportModule.kt
│   └── res/
│       ├── font/                     ← Cormorant Garamond, DM Sans
│       ├── drawable/                 ← Vector icons, mandala SVG
│       └── xml/                      ← Shortcuts, widget config
└── build.gradle.kts
```

---

## 10. Key Gradle Dependencies

```kotlin
// build.gradle.kts (app)

// Compose
implementation("androidx.compose.ui:ui:1.6.x")
implementation("androidx.compose.material3:material3:1.2.x")
implementation("androidx.navigation:navigation-compose:2.7.x")

// Room
implementation("androidx.room:room-runtime:2.6.x")
implementation("androidx.room:room-ktx:2.6.x")
ksp("androidx.room:room-compiler:2.6.x")

// Hilt
implementation("com.google.dagger:hilt-android:2.50")
ksp("com.google.dagger:hilt-android-compiler:2.50")
implementation("androidx.hilt:hilt-navigation-compose:1.1.x")

// DataStore
implementation("androidx.datastore:datastore-preferences:1.0.x")

// Async
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.x")
implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.x")

// Charts
implementation("com.patrykandpatrick.vico:compose-m3:1.13.x")

// Image
implementation("io.coil-kt:coil-compose:2.5.x")

// Rich Text
implementation("com.halilibo.compose-richtext:richtext-ui-material3:0.17.x")

// Export
implementation("org.apache.poi:poi-ooxml:5.2.x")

// Biometrics
implementation("androidx.biometric:biometric:1.1.x")

// WorkManager
implementation("androidx.work:work-runtime-ktx:2.9.x")
implementation("androidx.hilt:hilt-work:1.1.x")

// Glance Widgets
implementation("androidx.glance:glance-appwidget:1.0.x")
implementation("androidx.glance:glance-material3:1.0.x")

// Serialization (for TypeConverters)
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.x")
```

---

## 11. Phased Development Roadmap

| Phase | Scope | Est. Effort |
|---|---|---|
| **Phase 1** | Auth + DB + Navigation scaffold | 2–3 days |
| **Phase 2** | Patient CRUD (List + Detail + Form) | 3–4 days |
| **Phase 3** | Visit Form (all 7 steps) | 4–5 days |
| **Phase 4** | Dashboard + Appointments | 2–3 days |
| **Phase 5** | Analytics + Charts | 2–3 days |
| **Phase 6** | Export (PDF + Excel + JSON) | 2–3 days |
| **Phase 7** | Notifications + Widget | 1–2 days |
| **Phase 8** | Polish, animations, dark mode, testing | 2–3 days |

**Total estimated: ~18–26 days** for a single developer using AI coding agents

---

*SwaraPulse Android — Bridging Swara Yoga wisdom with modern clinical precision.*
