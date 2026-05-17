# Jules Prompt — SwaraPulse Phase 5 (Final)
## Polish: Glance Widget · App Shortcuts · Deep Links · Patient Export · UI Refinements

> Feed this entire file to Jules as a single task.
> Phases 1–4 are complete and compiling. Do NOT modify any existing file unless
> explicitly told to UPDATE it. Write complete, working Kotlin — no stubs.

---

## CONTEXT & CONSTRAINTS

Package root: `com.swarapulse`
All previous layers are complete and available for import.

New dependencies — confirm in `build.gradle.kts`:
```kotlin
// Jetpack Glance (home screen widget)
implementation("androidx.glance:glance-appwidget:1.0.0")
implementation("androidx.glance:glance-material3:1.0.0")

// App shortcuts (static + dynamic)
// Built-in via ShortcutManagerCompat — no extra dep needed

// Splash screen
implementation("androidx.core:core-splashscreen:1.0.1")

// In-app review (optional Google Play — skip if not publishing to Play Store)
// Skip this dep for now

// Deep links — already configured via NavHost intent filters
```

---

## TASK 1 — HOME SCREEN WIDGET (Jetpack Glance)
### File: `widget/TodaySummaryWidget.kt`

```kotlin
package com.swarapulse.widget

import android.content.Context
import androidx.compose.runtime.*
import androidx.glance.*
import androidx.glance.action.*
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.*
import androidx.glance.appwidget.lazy.*
import androidx.glance.appwidget.state.*
import androidx.glance.layout.*
import androidx.glance.material3.ColorProviders
import androidx.glance.state.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.swarapulse.MainActivity
import com.swarapulse.R
import com.swarapulse.data.db.SwaraPulseDatabase
import kotlinx.coroutines.*

class TodaySummaryWidget : GlanceAppWidget() {

    // Widget state: simple data class holding counts
    data class WidgetState(
        val appointmentCount: Int = 0,
        val followupCount: Int    = 0,
        val practitionerName: String = "Practitioner"
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Fetch data directly from Room (widget runs in its own coroutine)
        val db = SwaraPulseDatabase.getInstance(context)  // requires companion object getInstance()
        val now     = System.currentTimeMillis()
        val dayEnd  = now + 86_400_000L

        val todayAppointments = db.appointmentDao().getTodaysAppointmentsOnce(now, dayEnd)
        val followups         = db.visitDao().getFollowupsBetweenOnce(now, now + 48 * 3_600_000L)

        val prefs = context.getSharedPreferences("swarapulse_widget", Context.MODE_PRIVATE)
        val name  = prefs.getString("display_name", "Practitioner") ?: "Practitioner"

        provideContent {
            WidgetContent(
                state = WidgetState(
                    appointmentCount = todayAppointments.size,
                    followupCount    = followups.size,
                    practitionerName = name
                ),
                context = context
            )
        }
    }
}

@Composable
private fun WidgetContent(state: TodaySummaryWidget.WidgetState, context: Context) {
    // Deep link action → opens appointments screen
    val openAppointmentsAction = actionStartActivity<MainActivity>(
        actionParametersOf(MainActivity.DEEP_LINK_KEY to "swarapulse://appointments/today")
    )

    GlanceTheme {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(androidx.glance.ImageProvider(R.drawable.widget_background))
                .clickable(openAppointmentsAction)
                .padding(16.dp),
            contentAlignment = Alignment.TopStart
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {

                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // "SP" logo box
                    Box(
                        modifier = GlanceModifier
                            .size(28.dp)
                            .background(androidx.glance.ImageProvider(R.drawable.widget_logo_bg)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("SP", style = TextStyle(
                            color     = ColorProvider(androidx.compose.ui.graphics.Color.White),
                            fontSize  = 11.sp,
                            fontWeight = FontWeight.Bold
                        ))
                    }
                    Spacer(GlanceModifier.width(8.dp))
                    Text("SwaraPulse", style = TextStyle(
                        color     = ColorProvider(androidx.compose.ui.graphics.Color(0xFF4F46E5)),
                        fontSize  = 13.sp,
                        fontWeight = FontWeight.Bold
                    ))
                }

                Spacer(GlanceModifier.height(12.dp))

                // Two stat boxes side by side
                Row(modifier = GlanceModifier.fillMaxWidth()) {

                    // Appointments today
                    Column(
                        modifier = GlanceModifier.defaultWeight()
                            .background(androidx.glance.ImageProvider(R.drawable.widget_stat_bg_indigo))
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            state.appointmentCount.toString(),
                            style = TextStyle(
                                color     = ColorProvider(androidx.compose.ui.graphics.Color.White),
                                fontSize  = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text("Today's\nAppointments", style = TextStyle(
                            color    = ColorProvider(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f)),
                            fontSize = 10.sp
                        ))
                    }

                    Spacer(GlanceModifier.width(8.dp))

                    // Followups due
                    Column(
                        modifier = GlanceModifier.defaultWeight()
                            .background(androidx.glance.ImageProvider(R.drawable.widget_stat_bg_amber))
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            state.followupCount.toString(),
                            style = TextStyle(
                                color     = ColorProvider(androidx.compose.ui.graphics.Color.White),
                                fontSize  = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text("Followups\nDue (48h)", style = TextStyle(
                            color    = ColorProvider(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f)),
                            fontSize = 10.sp
                        ))
                    }
                }

                Spacer(GlanceModifier.defaultWeight())

                // Footer
                Text("Tap to open schedule",
                     style = TextStyle(
                         color    = ColorProvider(androidx.compose.ui.graphics.Color(0xFF94A3B8)),
                         fontSize = 9.sp
                     ))
            }
        }
    }
}

// ── Widget receiver ────────────────────────────────────────────────────────────
class TodaySummaryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodaySummaryWidget()
}
```

### UPDATE: `data/db/SwaraPulseDatabase.kt`

Add a `companion object` singleton accessor for widget use (widgets can't use Hilt):
```kotlin
companion object {
    // ... existing FTS_CALLBACK and MIGRATION constants ...

    @Volatile private var INSTANCE: SwaraPulseDatabase? = null

    fun getInstance(context: Context): SwaraPulseDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                SwaraPulseDatabase::class.java,
                DATABASE_NAME
            )
                .addCallback(FTS_CALLBACK)
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { INSTANCE = it }
        }
    }
}
```

### UPDATE: `data/db/dao/AppointmentDao.kt`

Add suspend version for widget (no Flow):
```kotlin
@Query("""
    SELECT * FROM appointments
    WHERE dateTime >= :dayStart AND dateTime < :dayEnd
    AND status = 'SCHEDULED'
    ORDER BY dateTime ASC
""")
suspend fun getTodaysAppointmentsOnce(dayStart: Long, dayEnd: Long): List<Appointment>
```

### Widget drawables (describe to Jules — it should create XML vector drawables):

#### `res/drawable/widget_background.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#FFFFFF" />
    <corners android:radius="16dp" />
    <stroke android:width="1dp" android:color="#E2E8F0" />
</shape>
```

#### `res/drawable/widget_stat_bg_indigo.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <gradient android:startColor="#4F46E5" android:endColor="#9333EA"
              android:angle="135" android:type="linear" />
    <corners android:radius="12dp" />
</shape>
```

#### `res/drawable/widget_stat_bg_amber.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <gradient android:startColor="#F59E0B" android:endColor="#F97316"
              android:angle="135" android:type="linear" />
    <corners android:radius="12dp" />
</shape>
```

#### `res/drawable/widget_logo_bg.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#4F46E5" />
    <corners android:radius="6dp" />
</shape>
```

### Widget configuration XML:
#### `res/xml/today_summary_widget_info.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="180dp"
    android:minHeight="110dp"
    android:targetCellWidth="3"
    android:targetCellHeight="2"
    android:updatePeriodMillis="1800000"
    android:initialLayout="@layout/glance_default_loading_layout"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen"
    android:description="@string/widget_description" />
```

Add to `res/values/strings.xml`:
```xml
<string name="widget_description">Today\'s appointment and followup summary</string>
```

### UPDATE: `AndroidManifest.xml`

Add inside `<application>`:
```xml
<receiver
    android:name=".widget.TodaySummaryWidgetReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/today_summary_widget_info" />
</receiver>
```

---

## TASK 2 — APP SHORTCUTS
### File: `shortcuts/ShortcutManager.kt`

```kotlin
package com.swarapulse.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.swarapulse.MainActivity
import com.swarapulse.R
import com.swarapulse.data.db.entity.Patient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppShortcutManager @Inject constructor(
    private val context: Context
) {
    companion object {
        const val ACTION_NEW_PATIENT    = "com.swarapulse.action.NEW_PATIENT"
        const val ACTION_TODAY_SCHEDULE = "com.swarapulse.action.TODAY_SCHEDULE"
        const val ACTION_NEW_VISIT      = "com.swarapulse.action.NEW_VISIT"
        const val EXTRA_PATIENT_ID      = "patientId"
    }

    // ── Static-style shortcuts (pushed dynamically at runtime) ──────────────
    fun pushStaticShortcuts() {
        val newPatient = ShortcutInfoCompat.Builder(context, "shortcut_new_patient")
            .setShortLabel("New Patient")
            .setLongLabel("Add New Patient")
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_person_add))
            .setIntent(
                Intent(context, MainActivity::class.java).apply {
                    action = ACTION_NEW_PATIENT
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            )
            .build()

        val todaySchedule = ShortcutInfoCompat.Builder(context, "shortcut_today_schedule")
            .setShortLabel("Schedule")
            .setLongLabel("Today's Schedule")
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_calendar))
            .setIntent(
                Intent(context, MainActivity::class.java).apply {
                    action = ACTION_TODAY_SCHEDULE
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            )
            .build()

        ShortcutManagerCompat.setDynamicShortcuts(context, listOf(newPatient, todaySchedule))
    }

    // ── Dynamic: "New visit for [last patient]" ─────────────────────────────
    fun pushLastPatientShortcut(patient: Patient) {
        val shortcut = ShortcutInfoCompat.Builder(context, "shortcut_last_patient")
            .setShortLabel("Visit ${patient.name.split(" ").first()}")
            .setLongLabel("New Visit for ${patient.name}")
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_stethoscope))
            .setIntent(
                Intent(context, MainActivity::class.java).apply {
                    action = ACTION_NEW_VISIT
                    putExtra(EXTRA_PATIENT_ID, patient.id)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            )
            .build()

        // Keep max 3 shortcuts: replace last_patient, keep others
        val existing = ShortcutManagerCompat.getDynamicShortcuts(context)
            .filter { it.id != "shortcut_last_patient" }
            .take(2)
        ShortcutManagerCompat.setDynamicShortcuts(context, existing + shortcut)
    }

    fun reportShortcutUsed(id: String) {
        ShortcutManagerCompat.reportShortcutUsed(context, id)
    }
}
```

### Shortcut vector drawables (create all three in `res/drawable/`):

#### `res/drawable/ic_shortcut_person_add.xml`
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="#FFFFFF">
    <path android:fillColor="@android:color/white"
          android:pathData="M15,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4c-2.21,0 -4,1.79 -4,4S12.79,12 15,12zM6,10L6,7H4v3H1v2h3v3h2v-3h3v-2H6zM15,14c-2.67,0 -8,1.34 -8,4v2h16v-2C23,15.34 17.67,14 15,14z"/>
</vector>
```

#### `res/drawable/ic_shortcut_calendar.xml`
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFF"
          android:pathData="M20,3h-1V1h-2v2H7V1H5v2H4C2.9,3 2,3.9 2,5v16c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V5C22,3.9 21.1,3 20,3zM20,21H4V8h16V21z"/>
</vector>
```

#### `res/drawable/ic_shortcut_stethoscope.xml`
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFF"
          android:pathData="M19,8C17.34,8 16,9.34 16,11c0,1.3 0.84,2.4 2,2.82V16c0,1.65 -1.35,3 -3,3s-3,-1.35 -3,-3v-1.27C13.75,14.24 15,12.76 15,11V5c0,-0.55 -0.45,-1 -1,-1h-1V2H9v2H8C7.45,3 7,3.45 7,4v7c0,1.76 1.25,3.24 2.91,3.73l0,0V16c0,2.76 2.24,5 5,5s5,-2.24 5,-5v-2.18C21.16,13.4 22,12.3 22,11C22,9.34 20.66,8 19,8zM9,11V5h4v6c0,1.1 -0.9,2 -2,2S9,12.1 9,11zM19,12c-0.55,0 -1,-0.45 -1,-1s0.45,-1 1,-1s1,0.45 1,1S19.55,12 19,12z"/>
</vector>
```

### UPDATE: `di/DatabaseModule.kt`

Add:
```kotlin
@Provides @Singleton
fun provideAppShortcutManager(@ApplicationContext ctx: Context): AppShortcutManager =
    AppShortcutManager(ctx)
```

### UPDATE: `SwaraPulseApp.kt`

```kotlin
@HiltAndroidApp
class SwaraPulseApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var shortcutManager: AppShortcutManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        FollowupReminderWorker.schedule(this)
        // Push static shortcuts after Hilt injection completes
        shortcutManager.pushStaticShortcuts()
    }
}
```

---

## TASK 3 — DEEP LINK HANDLING
### UPDATE: `MainActivity.kt`

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        val DEEP_LINK_KEY = androidx.glance.action.ActionParameters.Key<String>("deepLink")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash screen
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            SwaraPulseTheme {
                // Parse deep link / shortcut intent into startDestination override
                val startRoute = resolveStartRoute(intent)
                SwaraPulseNavHost(startRoute = startRoute)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Recreate to re-run NavHost with new intent
        recreate()
    }

    private fun resolveStartRoute(intent: Intent?): String? {
        intent ?: return null
        return when {
            // Widget deep link via data URI
            intent.data?.toString()?.startsWith("swarapulse://") == true -> {
                val uri = intent.data.toString()
                when {
                    uri.contains("/appointments") -> "appointments"
                    uri.contains("/patients/")   -> "patients/${uri.substringAfterLast('/')}"
                    uri.contains("/visits/new")  -> {
                        val id = intent.data?.getQueryParameter("patientId") ?: "-1"
                        "visit/new?patientId=$id"
                    }
                    else -> null
                }
            }
            // App shortcut intents
            intent.action == AppShortcutManager.ACTION_NEW_PATIENT    -> "patients/new"
            intent.action == AppShortcutManager.ACTION_TODAY_SCHEDULE -> "appointments"
            intent.action == AppShortcutManager.ACTION_NEW_VISIT      -> {
                val patientId = intent.getLongExtra(AppShortcutManager.EXTRA_PATIENT_ID, -1L)
                "visit/new?patientId=$patientId"
            }
            else -> null
        }
    }
}
```

### UPDATE: `presentation/navigation/NavHost.kt`

Add `startRoute` parameter and apply it:
```kotlin
@Composable
fun SwaraPulseNavHost(startRoute: String? = null) {
    val navController = rememberNavController()
    val isAuthenticated = remember { mutableStateOf(false) }

    // Determine effective start destination
    val startDest = "auth"  // Always start with auth; post-auth deep link is handled below

    NavHost(navController = navController, startDestination = startDest) {

        // ... all existing composable() blocks unchanged ...

        // After auth success, navigate to startRoute if provided, else dashboard
        // The AuthScreen's onSuccess callback already calls:
        //   navController.navigate("dashboard") { popUpTo("auth") { inclusive = true } }
        // UPDATE that callback to instead navigate to:
        //   startRoute ?: "dashboard"
        // Pass startRoute down to AuthScreen via a CompositionLocal or parameter.
    }
}
```

**Implementation note:** Pass `startRoute` from `SwaraPulseNavHost` into the auth composable so on successful authentication it navigates to `startRoute ?: "dashboard"` instead of always going to dashboard.

### UPDATE: `presentation/auth/AuthScreen.kt`

Change the navigation call on successful auth:
```kotlin
// Replace:
//   navController.navigate("dashboard") { popUpTo("auth") { inclusive = true } }
// With:
    navController.navigate(startRoute ?: "dashboard") {
        popUpTo("auth") { inclusive = true }
    }
// where startRoute is passed as a parameter to AuthScreen composable
```

### UPDATE: `AndroidManifest.xml` — add deep link intent filter to MainActivity:
```xml
<activity android:name=".MainActivity" ...>
    <!-- existing intent-filter for MAIN/LAUNCHER -->

    <!-- Deep link intent filter -->
    <intent-filter android:autoVerify="false">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="swarapulse" />
    </intent-filter>

    <!-- Shortcut actions -->
    <intent-filter>
        <action android:name="com.swarapulse.action.NEW_PATIENT" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
    <intent-filter>
        <action android:name="com.swarapulse.action.TODAY_SCHEDULE" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
    <intent-filter>
        <action android:name="com.swarapulse.action.NEW_VISIT" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

---

## TASK 4 — PATIENT EXPORT BUTTON IN DETAIL SCREEN
### File: `presentation/patients/PatientExportViewModel.kt`

```kotlin
@HiltViewModel
class PatientExportViewModel @Inject constructor(
    private val visitRepo: VisitRepository,
    private val pdfExporter: PdfExporter,
    private val excelExporter: ExcelExporter,
    @IoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    sealed class ExportEvent {
        data class ShareFile(val file: java.io.File, val mimeType: String) : ExportEvent()
        data class Error(val message: String) : ExportEvent()
    }

    private val _events = MutableSharedFlow<ExportEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<ExportEvent> = _events.asSharedFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    fun exportAsPdf(patient: Patient) {
        viewModelScope.launch(io) {
            _isExporting.value = true
            try {
                val visits = visitRepo.getVisitsForPatientOnce(patient.id)
                val file   = pdfExporter.exportPatientReport(patient, visits)
                _events.emit(ExportEvent.ShareFile(file, "application/pdf"))
            } catch (e: Exception) {
                _events.emit(ExportEvent.Error("PDF export failed: ${e.message}"))
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun exportAsExcel(patient: Patient) {
        viewModelScope.launch(io) {
            _isExporting.value = true
            try {
                val visits = visitRepo.getVisitsForPatientOnce(patient.id)
                val file   = excelExporter.exportPatient(patient, visits)
                _events.emit(ExportEvent.ShareFile(
                    file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                ))
            } catch (e: Exception) {
                _events.emit(ExportEvent.Error("Excel export failed: ${e.message}"))
            } finally {
                _isExporting.value = false
            }
        }
    }
}
```

### UPDATE: `presentation/patients/PatientDetailScreen.kt`

Inject `PatientExportViewModel` and wire the export menu items.

**In `PatientDetailTopBar`**, update the `DropdownMenu`:
```kotlin
@Composable
fun PatientDetailTopBar(
    patient: Patient?,
    exportVm: PatientExportViewModel = hiltViewModel(),  // ADD this
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onNewVisit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val isExporting by exportVm.isExporting.collectAsStateWithLifecycle()

    // Handle export events
    LaunchedEffect(Unit) {
        exportVm.events.collect { event ->
            when (event) {
                is PatientExportViewModel.ExportEvent.ShareFile -> {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", event.file
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = event.mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Export Patient"))
                }
                is PatientExportViewModel.ExportEvent.Error -> { /* snackbar */ }
            }
        }
    }

    var showMenu by remember { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(patient?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (isExporting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp),
                                              strokeWidth = 2.dp, color = Indigo600)
                }
            }
        },
        actions = {
            IconButton(onClick = onEdit)     { Icon(Icons.Rounded.Edit, "Edit") }
            IconButton(onClick = onNewVisit) { Icon(Icons.Rounded.Add, "New Visit") }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Rounded.MoreVert, "More")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    // Export as PDF
                    DropdownMenuItem(
                        text = { Text("Export as PDF") },
                        leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, null, tint = Rose500) },
                        onClick = {
                            showMenu = false
                            patient?.let { exportVm.exportAsPdf(it) }
                        }
                    )
                    // Export as Excel
                    DropdownMenuItem(
                        text = { Text("Export as Excel") },
                        leadingIcon = { Icon(Icons.Rounded.TableChart, null, tint = Emerald500) },
                        onClick = {
                            showMenu = false
                            patient?.let { exportVm.exportAsExcel(it) }
                        }
                    )
                    Divider()
                    // Delete
                    DropdownMenuItem(
                        text = { Text("Delete Patient", color = Rose500) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Rose500) },
                        onClick = { showMenu = false; onDelete() }
                    )
                }
            }
        }
    )
}
```

---

## TASK 5 — SPLASH SCREEN
### UPDATE: `res/values/themes.xml` (or `res/values/themes/themes.xml`)

Add splash screen theme:
```xml
<style name="SwaraPulseSplashTheme" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">#0F172A</item>
    <!-- Use adaptive icon as splash icon -->
    <item name="windowSplashScreenAnimatedIcon">@drawable/ic_splash_logo</item>
    <item name="windowSplashScreenAnimationDuration">400</item>
    <item name="postSplashScreenTheme">@style/Theme.SwaraPulse</item>
</style>
```

#### `res/drawable/ic_splash_logo.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- Outer ring -->
    <path
        android:strokeColor="#818CF8"
        android:strokeWidth="3"
        android:fillColor="@android:color/transparent"
        android:pathData="M54,10 A44,44 0 1,1 53.9,10 Z" />
    <!-- Inner circle fill -->
    <path
        android:fillColor="#4F46E5"
        android:pathData="M54,22 A32,32 0 1,1 53.9,22 Z" />
    <!-- SP text approximation via path (two rectangles) -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M40,38 L40,70 L44,70 L44,56 L52,56 C56,56 59,53 59,49 C59,45 56,42 52,42 L40,42 Z M44,42 L52,42 C54,42 55,43.5 55,45.5 C55,47.5 54,49 52,49 L44,49 Z" />
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M62,55 C58,55 62,55 62,55 C62,51 65,49 69,50 L69,46 C63,45 58,48 58,55 C58,62 65,63 68,64 C71,65 72,66 72,68 C72,70 70,71 68,71 C65,71 63,69 63,67 L59,67 C59,71 62,74 68,74 C73,74 76,71 76,68 C76,62 68,61 65,60 C63,59 62,57 62,55 Z" />
</vector>
```

Update `AndroidManifest.xml` to use splash theme:
```xml
<activity
    android:name=".MainActivity"
    android:theme="@style/SwaraPulseSplashTheme"
    ...>
```

---

## TASK 6 — LAST PATIENT SHORTCUT INTEGRATION

### UPDATE: `presentation/visit/VisitFormViewModel.kt`

After a successful `submit()`, push the "last patient" shortcut:
```kotlin
// Add injection in constructor:
private val shortcutManager: AppShortcutManager

// After visitRepo.insertVisit(visit) or updateVisit(visit) succeeds,
// add this line before emitting SaveSuccess:
val savedPatient = patientRepo.getPatientByIdOnce(savedPatientId)
savedPatient?.let {
    withContext(kotlinx.coroutines.Dispatchers.Main) {
        shortcutManager.pushLastPatientShortcut(it)
    }
}
```

---

## TASK 7 — WIDGET UPDATE WORKER
### File: `worker/WidgetUpdateWorker.kt`

```kotlin
package com.swarapulse.worker

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.*
import com.swarapulse.widget.TodaySummaryWidget
import com.swarapulse.widget.TodaySummaryWidgetReceiver
import java.util.concurrent.TimeUnit

class WidgetUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        try {
            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(TodaySummaryWidget::class.java)
            glanceIds.forEach { glanceId ->
                TodaySummaryWidget().update(context, glanceId)
            }
        } catch (e: Exception) {
            return Result.retry()
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "widget_update",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
```

### UPDATE: `SwaraPulseApp.kt`

Add `WidgetUpdateWorker.schedule(this)` in `onCreate()`:
```kotlin
override fun onCreate() {
    super.onCreate()
    FollowupReminderWorker.schedule(this)
    WidgetUpdateWorker.schedule(this)
    shortcutManager.pushStaticShortcuts()
}
```

---

## TASK 8 — UI POLISH PASS

### 8.1 Transition animations between screens
### UPDATE: `presentation/navigation/NavHost.kt`

Wrap all `composable()` calls with enter/exit transitions:
```kotlin
// Add this extension at the top of the NavHost file
import androidx.navigation.compose.composable
import com.google.accompanist.navigation.animation.*  // if using accompanist
// OR use built-in Compose navigation transitions (Navigation Compose 2.7+):

// Apply to all composable() calls:
composable(
    route = "dashboard",
    enterTransition = {
        slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) +
        fadeIn(animationSpec = tween(300))
    },
    exitTransition = {
        slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)) +
        fadeOut(animationSpec = tween(300))
    },
    popEnterTransition = {
        slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) +
        fadeIn(animationSpec = tween(300))
    },
    popExitTransition = {
        slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) +
        fadeOut(animationSpec = tween(300))
    }
) { DashboardScreen(navController) }

// Apply the SAME transition spec to ALL other composable() routes.
// Create a helper extension to avoid repetition:
```

```kotlin
// Helper: define once, use everywhere
private val SlideTransitionSpec = object {
    val enter    = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(280)) + fadeIn(tween(280))
    val exit     = slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(280)) + fadeOut(tween(280))
    val popEnter = slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(280)) + fadeIn(tween(280))
    val popExit  = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(280)) + fadeOut(tween(280))
}
// Then apply via enterTransition = { SlideTransitionSpec.enter } etc.
```

### 8.2 Pull-to-refresh on Patient List
### UPDATE: `presentation/patients/PatientListScreen.kt`

Wrap the `LazyColumn` / `LazyVerticalGrid` in a `PullToRefreshBox`:
```kotlin
// Add dep: already in material3 1.2+
// androidx.compose.material3.pullrefresh — use PullToRefreshBox

val pullRefreshState = rememberPullToRefreshState()
var isRefreshing by remember { mutableStateOf(false) }

PullToRefreshBox(
    state = pullRefreshState,
    onRefresh = {
        isRefreshing = true
        // The Flow in ViewModel auto-refreshes; just show spinner briefly
        scope.launch {
            delay(600)
            isRefreshing = false
        }
    },
    isRefreshing = isRefreshing
) {
    // existing LazyColumn / LazyVerticalGrid content
}
```

### 8.3 Haptic feedback on key actions

Add to key interactive moments:
```kotlin
val haptic = LocalHapticFeedback.current

// In PatientGridCard long-press:
.combinedClickable(
    onLongClick = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        showMenu = true
    }
)

// In VisitFormScreen "Save Record" button onClick:
Button(onClick = {
    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    vm.submit()
})

// In AuthScreen numpad digit buttons:
CircularButton(onClick = {
    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    vm.onDigitEntered(digit)
})
```

### 8.4 Bottom navigation badge counts

### UPDATE: `presentation/navigation/NavHost.kt`

The bottom navigation bar should show live badge counts. Create a `BottomNavViewModel`:

```kotlin
// File: presentation/navigation/BottomNavViewModel.kt
@HiltViewModel
class BottomNavViewModel @Inject constructor(
    private val appointmentRepo: AppointmentRepository,
    private val visitRepo: VisitRepository
) : ViewModel() {

    // Today's appointment count for badge
    private val now    = System.currentTimeMillis()
    private val dayEnd = now + 86_400_000L

    val todayAppointmentCount: StateFlow<Int> = appointmentRepo
        .getTodaysAppointments(now, dayEnd)
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val followupDueCount: StateFlow<Int> = visitRepo
        .getFollowupsBetween(now, now + 48 * 3_600_000L)
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}
```

Wire into the `BottomBar` composable inside `NavHost.kt`:
```kotlin
@Composable
fun BottomBar(navController: NavController, vm: BottomNavViewModel = hiltViewModel()) {
    val currentDest   by navController.currentBackStackEntryAsState()
    val currentRoute   = currentDest?.destination?.route
    val appointCount  by vm.todayAppointmentCount.collectAsStateWithLifecycle()

    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == "dashboard",
            onClick  = { navController.navigate("dashboard") { launchSingleTop = true } },
            icon     = { Icon(Icons.Rounded.Home, null) },
            label    = { Text("Home") }
        )
        NavigationBarItem(
            selected = currentRoute == "patients",
            onClick  = { navController.navigate("patients") { launchSingleTop = true } },
            icon     = { Icon(Icons.Rounded.People, null) },
            label    = { Text("Patients") }
        )
        NavigationBarItem(
            selected = currentRoute == "appointments",
            onClick  = { navController.navigate("appointments") { launchSingleTop = true } },
            icon     = {
                BadgedBox(badge = {
                    if (appointCount > 0) Badge { Text(appointCount.toString()) }
                }) {
                    Icon(Icons.Rounded.CalendarMonth, null)
                }
            },
            label    = { Text("Schedule") }
        )
        NavigationBarItem(
            selected = currentRoute == "analytics",
            onClick  = { navController.navigate("analytics") { launchSingleTop = true } },
            icon     = { Icon(Icons.Rounded.BarChart, null) },
            label    = { Text("Analytics") }
        )
        NavigationBarItem(
            selected = currentRoute == "settings",
            onClick  = { navController.navigate("settings") { launchSingleTop = true } },
            icon     = { Icon(Icons.Rounded.Settings, null) },
            label    = { Text("Settings") }
        )
    }
}
```

### 8.5 Empty state illustrations

Replace plain `EmptyState` composable (from Components.kt) with animated version:

```kotlin
// UPDATE Components.kt — replace EmptyState with:
@Composable
fun EmptyState(
    icon: ImageVector,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Animate icon with gentle floating motion
    val infiniteTransition = rememberInfiniteTransition(label = "float")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = -6f, targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatOffset"
    )

    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Floating icon with circular backdrop
        Box(
            modifier = Modifier
                .size(100.dp)
                .offset(y = offsetY.dp)
                .background(Indigo600.copy(alpha = 0.06f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = Indigo600.copy(alpha = 0.4f)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = Slate400,
             textAlign = TextAlign.Center)
        actionLabel?.let { label ->
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onAction?.invoke() },
                colors = ButtonDefaults.buttonColors(containerColor = Indigo600),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(label)
            }
        }
    }
}
```

### 8.6 `StatCard` count-up animation polish

Update `StatCard` in `Components.kt` to use `CountUpText` from Dashboard (move it to Components.kt so it's shared):
```kotlin
// Move CountUpText from DashboardScreen.kt to Components.kt
// Then StatCard becomes:
@Composable
fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    val numericValue = value.toIntOrNull()
    ElevatedCard(modifier = modifier, shape = RoundedCornerShape(16.dp),
                 elevation = CardDefaults.elevatedCardElevation(2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            if (numericValue != null) {
                CountUpText(target = numericValue, style = MaterialTheme.typography.headlineMedium)
            } else {
                Text(value, style = MaterialTheme.typography.headlineMedium)
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = Slate600)
        }
    }
}
```

---

## TASK 9 — FINAL MANIFEST & STRINGS CLEANUP
### Final `AndroidManifest.xml` additions

Ensure these are present (consolidating all additions from Phases 3–5):
```xml
<manifest ...>
    <!-- Permissions -->
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-feature android:name="android.hardware.camera" android:required="false" />

    <application
        android:name=".SwaraPulseApp"
        android:label="SwaraPulse"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:allowBackup="false"
        android:theme="@style/SwaraPulseSplashTheme"
        android:supportsRtl="true">

        <activity android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <intent-filter android:autoVerify="false">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="swarapulse" />
            </intent-filter>
            <intent-filter>
                <action android:name="com.swarapulse.action.NEW_PATIENT" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
            <intent-filter>
                <action android:name="com.swarapulse.action.TODAY_SCHEDULE" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
            <intent-filter>
                <action android:name="com.swarapulse.action.NEW_VISIT" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>

        <!-- FileProvider (from Phase 3) -->
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data android:name="android.support.FILE_PROVIDER_PATHS"
                       android:resource="@xml/file_paths" />
        </provider>

        <!-- Widget receiver (from Task 1) -->
        <receiver android:name=".widget.TodaySummaryWidgetReceiver"
                  android:exported="true">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data android:name="android.appwidget.provider"
                       android:resource="@xml/today_summary_widget_info" />
        </receiver>

        <!-- WorkManager custom init (from Phase 4) -->
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup.InitializationProvider"
                tools:node="remove" />
        </provider>

    </application>
</manifest>
```

### UPDATE: `res/xml/file_paths.xml`

Extend to cover cache exports dir:
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <files-path name="media"   path="media/" />
    <cache-path name="exports" path="exports/" />
</paths>
```

---

## COMPLETION CHECKLIST

- [ ] `SwaraPulseDatabase.getInstance()` companion object present — widget needs it since it cannot use Hilt injection
- [ ] `AppointmentDao.getTodaysAppointmentsOnce()` (suspend, no Flow) added for widget
- [ ] All 4 shortcut vector drawables created in `res/drawable/`
- [ ] `widget_background.xml`, `widget_stat_bg_indigo.xml`, `widget_stat_bg_amber.xml`, `widget_logo_bg.xml` created in `res/drawable/`
- [ ] `today_summary_widget_info.xml` created in `res/xml/`
- [ ] `TodaySummaryWidgetReceiver` registered in `AndroidManifest.xml`
- [ ] `ic_splash_logo.xml` created in `res/drawable/`
- [ ] `SwaraPulseSplashTheme` defined in `res/values/themes.xml` and `installSplashScreen()` called before `super.onCreate()` in `MainActivity`
- [ ] `AppShortcutManager` injected into `SwaraPulseApp` — `@Inject lateinit var` requires `SwaraPulseApp` to be annotated `@HiltAndroidApp` (already done in Phase 4)
- [ ] `PatientExportViewModel` is a separate `@HiltViewModel` — do not merge into `PatientDetailViewModel`; both can be active in the same `PatientDetailScreen` via separate `hiltViewModel()` calls
- [ ] `BottomNavViewModel` uses `launchSingleTop = true` in all `navigate()` calls to avoid back-stack duplication
- [ ] `CountUpText` moved from `DashboardScreen.kt` to `Components.kt` — remove the duplicate definition from Dashboard
- [ ] `EmptyState` updated composable in `Components.kt` replaces the original definition — not a second composable
- [ ] `SlideTransitionSpec` applied to ALL composable routes in `NavHost.kt` including `auth`, `patients/{patientId}`, `visit/new`, `visit/{visitId}/edit`
- [ ] `res/xml/file_paths.xml` updated to include `<cache-path name="exports" path="exports/" />` (exports go to `cacheDir/exports/`)
- [ ] `WidgetUpdateWorker` does NOT use `@HiltWorker` (it has no Hilt dependencies) — plain `CoroutineWorker` constructor is correct
- [ ] After all changes, do a full project build and resolve any remaining import conflicts — `kotlinx.datetime.Instant` vs `java.time.Instant` particularly in export utilities
