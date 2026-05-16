# Jules Prompt — SwaraPulse Phase 2
## Screens: Dashboard · Patient List · Patient Detail

> Feed this entire file to Jules as a single task.
> Phase 1 is complete: DB entities, DAOs, Repositories, TypeConverters, SettingsDataStore,
> AuthManager, Theme (Color.kt / Type.kt / Theme.kt), reusable Components.kt, AuthScreen,
> and NavHost are all implemented and compiling.

---

## CONTEXT & CONSTRAINTS

Package root: `com.swarapulse`
All screens use **Jetpack Compose + Material 3**.
Every screen follows **MVVM**: a `@HiltViewModel` ViewModel exposes `StateFlow<UiState>`,
the composable collects it with `collectAsStateWithLifecycle()`.
Do NOT modify any file from Phase 1.
Do NOT leave any function body as a stub — write complete, working Kotlin.
Use `Dispatchers.IO` (via `@IoDispatcher`) for all DB work in ViewModels.

Available from Phase 1 (import freely):
- Entities: `Patient`, `Visit`, `Appointment`, `VisitWithPatientName`
- Enums: `Nadi`, `Element`, `Mandala`, `Paksha`, `PatientSide`, `Gender`, `AppointmentStatus`
- Supporting: `MediaEntry`, `CustomField`
- Repos: `PatientRepository`, `VisitRepository`, `AppointmentRepository`
- Theme colors: `Indigo600`, `Purple600`, `Cyan500`, `Emerald500`, `Rose500`, `Amber500`,
  `Orange400`, `Slate50/100/200/400/600/800/900`, `NadiIda`, `NadiPingala`, `NadiSushumna`,
  `ElementAir/Fire/Space/Earth/Water`, `GradientPrimary`, `GradientSuccess`, `GradientWarning`, `GradientDanger`
- Components: `GradientCard`, `PatientAvatar`, `NadiChip`, `ElementBadge`,
  `SectionHeader`, `StatCard`, `EmptyState`, `LoadingOverlay`, `ConfirmDialog`
- Typography: `SwaraPulseTypography`, `CormorantGaramond`, `DmSans`

---

## TASK 1 — DOMAIN / USE-CASE HELPERS
### File: `domain/usecase/DashboardUseCases.kt`

```kotlin
package com.swarapulse.domain.usecase

import com.swarapulse.data.db.dao.VisitWithPatientName
import com.swarapulse.data.db.entity.*
import com.swarapulse.data.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.*
import javax.inject.Inject

// Bundles all data the Dashboard needs into one combined flow
class GetDashboardDataUseCase @Inject constructor(
    private val patientRepo: PatientRepository,
    private val visitRepo: VisitRepository,
    private val appointmentRepo: AppointmentRepository
) {
    operator fun invoke(): Flow<DashboardData> {
        val now = Clock.System.now()
        val tz  = TimeZone.currentSystemDefault()
        val today = now.toLocalDateTime(tz).date

        // Start of current month (epoch ms)
        val monthStart = LocalDateTime(today.year, today.month, 1, 0, 0)
            .toInstant(tz).toEpochMilliseconds()

        // Window for today's appointments
        val dayStart = today.atStartOfDayIn(tz).toEpochMilliseconds()
        val dayEnd   = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds()

        // Window for followups: now → +7 days
        val followupFrom = now.toEpochMilliseconds()
        val followupTo   = now.plus(7, DateTimeUnit.DAY, tz).toEpochMilliseconds()

        return combine(
            patientRepo.getActivePatientCount(),
            visitRepo.getTotalVisitCount(),
            visitRepo.getVisitsThisMonth(monthStart),
            visitRepo.getFollowupsBetween(followupFrom, followupTo),
            appointmentRepo.getTodaysAppointments(dayStart, dayEnd),
            visitRepo.getRecentVisits()
        ) { values ->
            DashboardData(
                activePatientCount  = values[0] as Int,
                totalVisitCount     = values[1] as Int,
                visitsThisMonth     = values[2] as Int,
                @Suppress("UNCHECKED_CAST")
                upcomingFollowups   = values[3] as List<Visit>,
                @Suppress("UNCHECKED_CAST")
                todaysAppointments  = values[4] as List<Appointment>,
                @Suppress("UNCHECKED_CAST")
                recentVisits        = values[5] as List<VisitWithPatientName>
            )
        }
    }
}

data class DashboardData(
    val activePatientCount: Int,
    val totalVisitCount: Int,
    val visitsThisMonth: Int,
    val upcomingFollowups: List<Visit>,
    val todaysAppointments: List<Appointment>,
    val recentVisits: List<VisitWithPatientName>
)
```

### File: `domain/usecase/PatientUseCases.kt`

```kotlin
package com.swarapulse.domain.usecase

import com.swarapulse.data.db.entity.*
import com.swarapulse.data.repository.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

class GetFilteredPatientsUseCase @Inject constructor(
    private val repo: PatientRepository
) {
    // Returns a Flow that reacts to filter/sort/search changes
    operator fun invoke(
        query: String,
        statusFilter: PatientStatusFilter,
        genderFilter: Gender?,
        sortOrder: PatientSortOrder
    ): Flow<List<Patient>> {
        val base: Flow<List<Patient>> = if (query.isBlank()) {
            repo.getAllPatients()
        } else {
            repo.searchPatients(query)
        }
        return base.map { patients ->
            patients
                .filter { p ->
                    when (statusFilter) {
                        PatientStatusFilter.ALL          -> true
                        PatientStatusFilter.ACTIVE       -> p.isActive && !p.isProvisional
                        PatientStatusFilter.INACTIVE     -> !p.isActive
                        PatientStatusFilter.PROVISIONAL  -> p.isProvisional
                    }
                }
                .filter { p -> genderFilter == null || p.gender == genderFilter }
                .let { list ->
                    when (sortOrder) {
                        PatientSortOrder.NAME_ASC   -> list.sortedBy { it.name }
                        PatientSortOrder.NAME_DESC  -> list.sortedByDescending { it.name }
                        PatientSortOrder.RECENT     -> list.sortedByDescending { it.lastVisitDate }
                        PatientSortOrder.OLDEST     -> list.sortedBy { it.createdAt }
                    }
                }
        }
    }
}

enum class PatientStatusFilter { ALL, ACTIVE, INACTIVE, PROVISIONAL }
enum class PatientSortOrder    { NAME_ASC, NAME_DESC, RECENT, OLDEST }
```

---

## TASK 2 — DASHBOARD VIEWMODEL
### File: `presentation/dashboard/DashboardViewModel.kt`

```kotlin
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getDashboardData: GetDashboardDataUseCase,
    private val settingsDataStore: SettingsDataStore,
    private val patientRepo: PatientRepository,
    @IoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    // UI state sealed class
    sealed class UiState {
        object Loading : UiState()
        data class Success(val data: DashboardData, val practitionerName: String) : UiState()
        data class Error(val msg: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                getDashboardData(),
                settingsDataStore.displayName
            ) { data, name -> UiState.Success(data, name) }
                .catch { e -> emit(UiState.Error(e.message ?: "Unknown error")) }
                .collect { _uiState.value = it }
        }
    }
}
```

---

## TASK 3 — DASHBOARD SCREEN
### File: `presentation/dashboard/DashboardScreen.kt`

Implement a full `DashboardScreen` composable. The screen is a `Scaffold` with a custom
`TopAppBar` and a `LazyColumn` body. No bottom bar here — it is provided by the NavHost scaffold.

### 3.1 TopAppBar
```
- navigationIcon: Row { 
    Box(24.dp, gradient Indigo600→Purple600, rounded 8.dp) { Text("SP", White, 11.sp, bold) }
    Spacer(8.dp)
    Text("SwaraPulse", headlineSmall, CormorantGaramond) 
  }
- actions:
    IconButton(Icons.Rounded.DarkMode / LightMode) → collect darkMode from SettingsDataStore,
        toggle via settingsDataStore.setDarkMode(!current)
    IconButton(Icons.Rounded.Notifications) → no-op for now (Phase 7)
```

### 3.2 Greeting Card
```kotlin
// Full-width card, gradient background GradientPrimary (Indigo600 → Purple600)
// Content:
//   - greeting: "Good Morning ☀️" / "Good Afternoon 🌤" / "Good Evening 🌙"
//     derive from LocalTime hour: <12 = morning, <17 = afternoon, else evening
//   - "Dr. {practitionerName}" in displayLarge (CormorantGaramond 40sp Light) White
//   - Today's date: formatGregorianDate() + " · " + formatHinduApprox() in labelSmall Slate200
//     (Hindu approx: show Paksha + Tithi if derivable, else just Gregorian)
//   - Subtle semi-transparent mandala SVG in bottom-right corner (use Canvas to draw
//     concentric circles with dashed strokes, White alpha 0.08f, 3 rings at 60/90/120.dp)
//
// Use animateFloatAsState to animate from 0f→1f on composition for a fade+slide-up entry
```

### 3.3 Stats Row
```kotlin
// LazyRow (horizontalArrangement = spacedBy(12.dp), contentPadding 16.dp)
// 4 StatCards:
val stats = listOf(
    Triple("Active Patients", data.activePatientCount.toString(), Icons.Rounded.People       to Indigo600),
    Triple("Total Visits",    data.totalVisitCount.toString(),    Icons.Rounded.Assignment   to Purple600),
    Triple("This Month",      data.visitsThisMonth.toString(),    Icons.Rounded.CalendarMonth to Cyan500),
    Triple("Followups Due",   data.upcomingFollowups.size.toString(), Icons.Rounded.Alarm    to Amber500)
)
// Each StatCard: width = 140.dp, ElevatedCard, shadow-md
// Animate the numeric value counting up from 0 using CountUpText composable (see below)
```

#### CountUpText Composable (define in the same file or Components.kt):
```kotlin
@Composable
fun CountUpText(
    target: Int,
    durationMs: Int = 800,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    color: Color = Color.Unspecified
) {
    var displayed by remember { mutableIntStateOf(0) }
    LaunchedEffect(target) {
        val steps = 20
        val delay = durationMs / steps
        val increment = target / steps.coerceAtLeast(1)
        for (i in 1..steps) {
            delay(delay.toLong())
            displayed = (increment * i).coerceAtMost(target)
        }
        displayed = target
    }
    Text(text = displayed.toString(), style = style, color = color)
}
```

### 3.4 Today's Appointments Section
```kotlin
SectionHeader(
    title = "Today's Appointments",
    icon = Icons.Rounded.CalendarToday,
    action = { TextButton(onClick = { navController.navigate("appointments") }) { Text("See All") } }
)

if (data.todaysAppointments.isEmpty()) {
    EmptyState(
        icon = Icons.Rounded.EventAvailable,
        message = "No appointments today",
        modifier = Modifier.height(100.dp)
    )
} else {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
        items(data.todaysAppointments) { appt ->
            AppointmentMiniCard(
                appointment = appt,
                onStartVisit = { navController.navigate("visit/new?patientId=${appt.patientId}") },
                onTap = { navController.navigate("patients/${appt.patientId}") }
            )
        }
    }
}
```

#### AppointmentMiniCard (define locally):
```
ElevatedCard, width = 200.dp
- Top: gradient strip (Indigo600→Purple600 if today, else Slate200) showing time (HH:mm)
- Body: patient name (titleMedium, max 1 line), purpose (bodySmall, Slate600, max 1 line)
- Bottom: "Start Visit" FilledTonalButton (small, Emerald500 tint)
  → navigates to visit form
```

### 3.5 Upcoming Followups Section
```kotlin
SectionHeader(title = "Upcoming Followups", icon = Icons.Rounded.Alarm)

// For each visit in data.upcomingFollowups (max 5):
// Compute daysUntil = followupDate - now in days
// Color: daysUntil == 0 → Rose500 (today!), daysUntil == 1 → Amber500, else Emerald500
```

#### FollowupTimelineItem (define locally):
```
Row:
  Left: vertical line (2.dp wide, color) connecting items + colored dot (8.dp circle)
  Center (weight 1f):
    - Patient name (titleSmall) — fetch from patientRepo by patientId via a produceState
    - Chief complaint snippet (bodySmall, Slate600, max 1 line, strip HTML tags)
  Right:
    - "Today" / "Tomorrow" / "In N days" label (labelSmall, color)
    - Tap whole row → navigate to patient detail
```

### 3.6 Recent Activity Section
```kotlin
SectionHeader(title = "Recent Activity", icon = Icons.Rounded.History)

// For each item in data.recentVisits (max 5):
```

#### RecentVisitRow (define locally):
```
ListItem(
  headlineContent = { Text(item.patientName, titleSmall) },
  supportingContent = {
    Row {
      NadiChip(item.visit.patientNadi)
      Spacer(4.dp)
      ElementBadge(item.visit.patientElement)
    }
  },
  leadingContent = { PatientAvatar(name = item.patientName, imagePath = null, size = 40.dp) },
  trailingContent = { Text(item.visit.dateTime.timeAgo(), labelSmall, Slate400) },
  modifier = Modifier.clickable { navController.navigate("patients/${item.visit.patientId}") }
)
Divider(color = Slate200)
```

### 3.7 Expandable FAB
```kotlin
// Declare in DashboardScreen's Scaffold.floatingActionButton slot
var fabExpanded by remember { mutableStateOf(false) }

Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    AnimatedVisibility(visible = fabExpanded, enter = fadeIn() + slideInVertically(), exit = fadeOut() + slideOutVertically()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
            MiniActionFab(label = "New Appointment", icon = Icons.Rounded.CalendarMonth, color = Cyan500) {
                navController.navigate("appointments"); fabExpanded = false
            }
            MiniActionFab(label = "New Visit", icon = Icons.Rounded.Assignment, color = Purple600) {
                navController.navigate("visit/new?patientId=-1"); fabExpanded = false
            }
            MiniActionFab(label = "New Patient", icon = Icons.Rounded.PersonAdd, color = Indigo600) {
                navController.navigate("patients/new"); fabExpanded = false
            }
        }
    }
    FloatingActionButton(
        onClick = { fabExpanded = !fabExpanded },
        containerColor = Indigo600
    ) {
        Icon(
            imageVector = if (fabExpanded) Icons.Rounded.Close else Icons.Rounded.Add,
            contentDescription = null,
            tint = Color.White
        )
    }
}
```

#### MiniActionFab (define locally):
```kotlin
@Composable
fun MiniActionFab(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = RoundedCornerShape(8.dp), color = Slate800.copy(alpha = 0.85f)) {
            Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                 style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
        SmallFloatingActionButton(onClick = onClick, containerColor = color) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
    }
}
```

### 3.8 Utility Extensions (define in `presentation/util/Extensions.kt`)

```kotlin
package com.swarapulse.presentation.util

import kotlinx.datetime.*
import java.time.format.DateTimeFormatter
import java.util.Locale

fun Instant.timeAgo(): String {
    val now = Clock.System.now()
    val diff = now - this
    return when {
        diff.inWholeMinutes < 1  -> "just now"
        diff.inWholeMinutes < 60 -> "${diff.inWholeMinutes}m ago"
        diff.inWholeHours < 24   -> "${diff.inWholeHours}h ago"
        diff.inWholeDays < 7     -> "${diff.inWholeDays}d ago"
        diff.inWholeDays < 30    -> "${diff.inWholeDays / 7}w ago"
        else                     -> "${diff.inWholeDays / 30}mo ago"
    }
}

fun Instant.formatDisplay(): String {
    val ldt = this.toLocalDateTime(TimeZone.currentSystemDefault())
    return "%02d %s %d, %02d:%02d".format(
        ldt.dayOfMonth,
        ldt.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() },
        ldt.year, ldt.hour, ldt.minute
    )
}

fun Instant.formatDateOnly(): String {
    val ldt = this.toLocalDateTime(TimeZone.currentSystemDefault())
    return "%02d %s %d".format(
        ldt.dayOfMonth,
        ldt.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() },
        ldt.year
    )
}

fun Instant.daysUntil(): Long {
    val now = Clock.System.now()
    return (this - now).inWholeDays
}

// Strip basic HTML tags from rich text for preview snippets
fun String.stripHtml(): String =
    this.replace(Regex("<[^>]*>"), "").trim()
```

---

## TASK 4 — PATIENTS LIST VIEWMODEL
### File: `presentation/patients/PatientListViewModel.kt`

```kotlin
@HiltViewModel
class PatientListViewModel @Inject constructor(
    private val getFilteredPatients: GetFilteredPatientsUseCase,
    private val patientRepo: PatientRepository,
    @IoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    // Filter state (exposed for UI to read & update)
    private val _query         = MutableStateFlow("")
    private val _statusFilter  = MutableStateFlow(PatientStatusFilter.ALL)
    private val _genderFilter  = MutableStateFlow<Gender?>(null)
    private val _sortOrder     = MutableStateFlow(PatientSortOrder.RECENT)
    private val _viewMode      = MutableStateFlow(PatientViewMode.GRID)

    val query        : StateFlow<String>               = _query.asStateFlow()
    val statusFilter : StateFlow<PatientStatusFilter>  = _statusFilter.asStateFlow()
    val genderFilter : StateFlow<Gender?>              = _genderFilter.asStateFlow()
    val sortOrder    : StateFlow<PatientSortOrder>     = _sortOrder.asStateFlow()
    val viewMode     : StateFlow<PatientViewMode>      = _viewMode.asStateFlow()

    // Debounced patient list — reacts to any filter/query change
    val patients: StateFlow<List<Patient>> = combine(
        _query.debounce(300),
        _statusFilter,
        _genderFilter,
        _sortOrder
    ) { q, status, gender, sort ->
        getFilteredPatients(q, status, gender, sort)
    }
        .flatMapLatest { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Delete state for confirm dialog
    private val _pendingDelete = MutableStateFlow<Patient?>(null)
    val pendingDelete: StateFlow<Patient?> = _pendingDelete.asStateFlow()

    fun onQueryChange(q: String) { _query.value = q }
    fun onStatusFilterChange(f: PatientStatusFilter) { _statusFilter.value = f }
    fun onGenderFilterChange(g: Gender?) { _genderFilter.value = g }
    fun onSortOrderChange(s: PatientSortOrder) { _sortOrder.value = s }
    fun toggleViewMode() { _viewMode.value = if (_viewMode.value == PatientViewMode.GRID) PatientViewMode.LIST else PatientViewMode.GRID }
    fun requestDelete(patient: Patient) { _pendingDelete.value = patient }
    fun cancelDelete() { _pendingDelete.value = null }

    fun confirmDelete() {
        val p = _pendingDelete.value ?: return
        viewModelScope.launch(io) {
            patientRepo.deletePatient(p)
            _pendingDelete.value = null
        }
    }
}

enum class PatientViewMode { GRID, LIST }
```

---

## TASK 5 — PATIENTS LIST SCREEN
### File: `presentation/patients/PatientListScreen.kt`

### 5.1 Screen structure
```
Scaffold(
  topBar = { PatientListTopBar(vm) },
  floatingActionButton = { AddPatientFab(navController) }
) { padding ->
  Column {
    if (patients.isEmpty()) EmptyState(...)
    else AnimatedContent(viewMode) {
      if GRID → LazyVerticalGrid(columns = Fixed(2), ...)
      if LIST → LazyColumn(...)
    }
  }
  if (pendingDelete != null) ConfirmDialog(...)
  FilterBottomSheet(if shown)
}
```

### 5.2 PatientListTopBar
```kotlin
@Composable
fun PatientListTopBar(vm: PatientListViewModel) {
    val query by vm.query.collectAsStateWithLifecycle()
    val viewMode by vm.viewMode.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }

    Column {
        TopAppBar(
            title = { Text("Patients", style = MaterialTheme.typography.headlineMedium,
                          fontFamily = CormorantGaramond) },
            actions = {
                // View mode toggle
                IconButton(onClick = vm::toggleViewMode) {
                    Icon(
                        if (viewMode == PatientViewMode.GRID) Icons.Rounded.ViewList
                        else Icons.Rounded.GridView,
                        contentDescription = "Toggle view"
                    )
                }
                // Filter icon with badge if filters active
                BadgedBox(badge = {
                    if (vm.statusFilter.value != PatientStatusFilter.ALL || vm.genderFilter.value != null)
                        Badge()
                }) {
                    IconButton(onClick = { showFilters = true }) {
                        Icon(Icons.Rounded.FilterList, contentDescription = "Filter")
                    }
                }
            }
        )
        // Persistent search bar below top bar
        OutlinedTextField(
            value = query,
            onValueChange = vm::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search by name, mobile, email…") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                AnimatedVisibility(visible = query.isNotEmpty()) {
                    IconButton(onClick = { vm.onQueryChange("") }) {
                        Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
    }
    // Show FilterBottomSheet if showFilters == true
    if (showFilters) {
        FilterBottomSheet(vm = vm, onDismiss = { showFilters = false })
    }
}
```

### 5.3 FilterBottomSheet
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(vm: PatientListViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val statusFilter by vm.statusFilter.collectAsStateWithLifecycle()
    val genderFilter by vm.genderFilter.collectAsStateWithLifecycle()
    val sortOrder    by vm.sortOrder.collectAsStateWithLifecycle()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {

            Text("Filter & Sort", style = MaterialTheme.typography.titleLarge)

            // Status filter
            Text("Status", style = MaterialTheme.typography.labelMedium, color = Slate600)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PatientStatusFilter.values().forEach { f ->
                    FilterChip(
                        selected = statusFilter == f,
                        onClick = { vm.onStatusFilterChange(f) },
                        label = { Text(f.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            // Gender filter
            Text("Gender", style = MaterialTheme.typography.labelMedium, color = Slate600)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = genderFilter == null,
                    onClick = { vm.onGenderFilterChange(null) },
                    label = { Text("Any") }
                )
                Gender.values().forEach { g ->
                    FilterChip(
                        selected = genderFilter == g,
                        onClick = { vm.onGenderFilterChange(g) },
                        label = { Text(g.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            // Sort
            Text("Sort By", style = MaterialTheme.typography.labelMedium, color = Slate600)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PatientSortOrder.values().forEach { s ->
                    val label = when (s) {
                        PatientSortOrder.NAME_ASC  -> "Name A–Z"
                        PatientSortOrder.NAME_DESC -> "Name Z–A"
                        PatientSortOrder.RECENT    -> "Most Recent"
                        PatientSortOrder.OLDEST    -> "Oldest First"
                    }
                    FilterChip(
                        selected = sortOrder == s,
                        onClick = { vm.onSortOrderChange(s) },
                        label = { Text(label) }
                    )
                }
            }

            // Reset + Done buttons
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        vm.onStatusFilterChange(PatientStatusFilter.ALL)
                        vm.onGenderFilterChange(null)
                        vm.onSortOrderChange(PatientSortOrder.RECENT)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Reset") }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Done") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
```

### 5.4 PatientGridCard
```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PatientGridCard(
    patient: Patient,
    onTap: () -> Unit,
    onNewVisit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onTap, onLongClick = { showMenu = true }),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // Avatar + status badge
            Box {
                PatientAvatar(name = patient.name, imagePath = patient.profileImagePath, size = 56.dp)
                if (!patient.isActive) {
                    Badge(
                        containerColor = Rose500,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) { Text("Off", style = MaterialTheme.typography.labelSmall, color = Color.White) }
                }
                if (patient.isProvisional) {
                    Badge(
                        containerColor = Amber500,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) { Text("Prov", style = MaterialTheme.typography.labelSmall, color = Color.White) }
                }
            }

            // Name
            Text(
                text = patient.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Age · Gender chip
            SuggestionChip(
                onClick = {},
                label = {
                    Text(
                        "${patient.age}y · ${patient.gender.name.lowercase().replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )

            // Last visit
            patient.lastVisitDate?.let { lv ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.AccessTime, contentDescription = null,
                         modifier = Modifier.size(12.dp), tint = Slate400)
                    Text(lv.timeAgo(), style = MaterialTheme.typography.labelSmall, color = Slate400)
                }
            }

            // Mobile
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.Phone, contentDescription = null,
                     modifier = Modifier.size(12.dp), tint = Slate400)
                Text(patient.mobile, style = MaterialTheme.typography.labelSmall, color = Slate600,
                     maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            // "New Visit" quick button
            FilledTonalButton(
                onClick = onNewVisit,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Indigo600.copy(alpha = 0.1f))
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null,
                     modifier = Modifier.size(14.dp), tint = Indigo600)
                Spacer(4.dp)
                Text("New Visit", style = MaterialTheme.typography.labelSmall, color = Indigo600)
            }
        }

        // Long-press context menu
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("View Details") },
                leadingIcon = { Icon(Icons.Rounded.Person, null) },
                onClick = { showMenu = false; onTap() }
            )
            DropdownMenuItem(
                text = { Text("New Visit") },
                leadingIcon = { Icon(Icons.Rounded.Assignment, null) },
                onClick = { showMenu = false; onNewVisit() }
            )
            Divider()
            DropdownMenuItem(
                text = { Text("Delete", color = Rose500) },
                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Rose500) },
                onClick = { showMenu = false; onDelete() }
            )
        }
    }
}
```

### 5.5 PatientListRow (for LIST view with swipe-to-action)
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientListRow(
    patient: Patient,
    onTap: () -> Unit,
    onNewVisit: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { onNewVisit(); false }  // swipe right → new visit
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); false }    // swipe left → delete
                else -> false
            }
        },
        positionalThreshold = { it * 0.4f }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color by animateColorAsState(
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Emerald500
                    SwipeToDismissBoxValue.EndToStart -> Rose500
                    else -> Color.Transparent
                }
            )
            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Icons.Rounded.Add
                SwipeToDismissBoxValue.EndToStart -> Icons.Rounded.Delete
                else -> null
            }
            Box(
                Modifier.fillMaxSize().background(color).padding(horizontal = 24.dp),
                contentAlignment = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    else -> Alignment.CenterEnd
                }
            ) {
                icon?.let { Icon(it, contentDescription = null, tint = Color.White) }
            }
        }
    ) {
        ListItem(
            headlineContent = { Text(patient.name, style = MaterialTheme.typography.titleSmall) },
            supportingContent = {
                Text(
                    "${patient.age}y · ${patient.gender.name.lowercase().replaceFirstChar { it.uppercase() }} · ${patient.mobile}",
                    style = MaterialTheme.typography.bodySmall, color = Slate600
                )
            },
            leadingContent = {
                PatientAvatar(name = patient.name, imagePath = patient.profileImagePath, size = 44.dp)
            },
            trailingContent = {
                patient.lastVisitDate?.let { Text(it.timeAgo(), style = MaterialTheme.typography.labelSmall, color = Slate400) }
            },
            modifier = Modifier.clickable(onClick = onTap)
                .background(MaterialTheme.colorScheme.surface)
        )
    }
    Divider(color = Slate200)
}
```

### 5.6 Putting it all together — main composable body
```kotlin
@Composable
fun PatientListScreen(
    navController: NavController,
    vm: PatientListViewModel = hiltViewModel()
) {
    val patients  by vm.patients.collectAsStateWithLifecycle()
    val viewMode  by vm.viewMode.collectAsStateWithLifecycle()
    val pendingDelete by vm.pendingDelete.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { PatientListTopBar(vm) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("New Patient") },
                icon = { Icon(Icons.Rounded.PersonAdd, null) },
                onClick = { navController.navigate("patients/new") },
                containerColor = Indigo600,
                contentColor = Color.White
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (patients.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.People,
                    message = "No patients found",
                    actionLabel = "Add First Patient",
                    onAction = { navController.navigate("patients/new") },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AnimatedContent(targetState = viewMode, label = "viewMode") { mode ->
                    when (mode) {
                        PatientViewMode.GRID ->
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(patients, key = { it.id }) { patient ->
                                    PatientGridCard(
                                        patient = patient,
                                        onTap   = { navController.navigate("patients/${patient.id}") },
                                        onNewVisit = { navController.navigate("visit/new?patientId=${patient.id}") },
                                        onDelete   = { vm.requestDelete(patient) },
                                        modifier = Modifier.animateItemPlacement()
                                    )
                                }
                            }
                        PatientViewMode.LIST ->
                            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                                items(patients, key = { it.id }) { patient ->
                                    PatientListRow(
                                        patient    = patient,
                                        onTap      = { navController.navigate("patients/${patient.id}") },
                                        onNewVisit = { navController.navigate("visit/new?patientId=${patient.id}") },
                                        onDelete   = { vm.requestDelete(patient) }
                                    )
                                }
                            }
                    }
                }
            }

            // Delete confirmation dialog
            pendingDelete?.let { patient ->
                ConfirmDialog(
                    title = "Delete Patient",
                    message = "Delete ${patient.name} and all their visits? This cannot be undone.",
                    confirmLabel = "Delete",
                    confirmColor = Rose500,
                    onConfirm = vm::confirmDelete,
                    onDismiss = vm::cancelDelete
                )
            }
        }
    }
}
```

---

## TASK 6 — PATIENT DETAIL VIEWMODEL
### File: `presentation/patients/PatientDetailViewModel.kt`

```kotlin
@HiltViewModel
class PatientDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val patientRepo: PatientRepository,
    private val visitRepo: VisitRepository,
    private val appointmentRepo: AppointmentRepository,
    @IoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    private val patientId: Long = checkNotNull(savedStateHandle["patientId"])

    // --- Patient stream
    val patient: StateFlow<Patient?> =
        patientRepo.getPatientById(patientId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- Visit stream (newest first)
    val visits: StateFlow<List<Visit>> =
        visitRepo.getVisitsForPatient(patientId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Derived analytics (computed from visits)
    data class PatientAnalytics(
        val totalVisits: Int,
        val lastVisitAgo: String,
        val avgVisitsPerMonth: Float,
        val followupRate: Float,                      // % visits that had followup set
        val dominantNadi: Nadi?,
        val dominantElement: Element?,
        val nadiFrequency: Map<Nadi, Int>,
        val elementFrequency: Map<Element, Int>,
        val topComplaints: List<String>,              // top 3 normalized complaint snippets
        val upcomingFollowup: Visit?                  // earliest future followup
    )

    val analytics: StateFlow<PatientAnalytics> = visits.map { list ->
        val now = Clock.System.now()
        val sorted = list.sortedBy { it.dateTime }

        // Nadi + Element frequency
        val nadiFreq    = list.groupingBy { it.patientNadi }.eachCount()
        val elementFreq = list.groupingBy { it.patientElement }.eachCount()

        // Avg visits/month: span from first to now in months
        val spanMonths = if (sorted.isEmpty()) 1f else {
            val diffDays = (now - sorted.first().dateTime).inWholeDays.toFloat()
            (diffDays / 30f).coerceAtLeast(1f)
        }

        // Top complaints: strip HTML → take first 60 chars → deduplicate
        val complaints = list
            .map { it.chiefComplaint.stripHtml().take(60).trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(3).map { it.key }

        // Next upcoming followup
        val upcoming = list.filter {
            it.followupDate != null && it.followupDate > now
        }.minByOrNull { it.followupDate!! }

        PatientAnalytics(
            totalVisits        = list.size,
            lastVisitAgo       = list.firstOrNull()?.dateTime?.timeAgo() ?: "No visits",
            avgVisitsPerMonth  = list.size / spanMonths,
            followupRate       = if (list.isEmpty()) 0f else list.count { it.followupDate != null } / list.size.toFloat(),
            dominantNadi       = nadiFreq.maxByOrNull { it.value }?.key,
            dominantElement    = elementFreq.maxByOrNull { it.value }?.key,
            nadiFrequency      = nadiFreq,
            elementFrequency   = elementFreq,
            topComplaints      = complaints,
            upcomingFollowup   = upcoming
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PatientAnalytics(0,"",0f,0f,null,null, emptyMap(), emptyMap(), emptyList(), null))

    // Visit search/filter within detail
    private val _visitQuery       = MutableStateFlow("")
    private val _visitNadiFilter  = MutableStateFlow<Nadi?>(null)
    private val _visitSortNewest  = MutableStateFlow(true)

    val visitQuery      : StateFlow<String>  = _visitQuery.asStateFlow()
    val visitNadiFilter : StateFlow<Nadi?>   = _visitNadiFilter.asStateFlow()
    val visitSortNewest : StateFlow<Boolean> = _visitSortNewest.asStateFlow()

    val filteredVisits: StateFlow<List<Visit>> = combine(
        visits, _visitQuery, _visitNadiFilter, _visitSortNewest
    ) { all, q, nadiF, newest ->
        all.filter { v ->
            (q.isBlank() || v.chiefComplaint.stripHtml().contains(q, ignoreCase = true)
                    || v.diseaseCategories.any { it.contains(q, ignoreCase = true) })
                    && (nadiF == null || v.patientNadi == nadiF)
        }.let { if (newest) it.sortedByDescending { v -> v.dateTime }
                else it.sortedBy { v -> v.dateTime } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Mutation helpers
    fun onVisitQueryChange(q: String)     { _visitQuery.value = q }
    fun onVisitNadiFilter(n: Nadi?)       { _visitNadiFilter.value = n }
    fun toggleVisitSort()                 { _visitSortNewest.value = !_visitSortNewest.value }

    fun deleteVisit(visit: Visit) {
        viewModelScope.launch(io) { visitRepo.deleteVisit(visit) }
    }

    fun deletePatient(onDone: () -> Unit) {
        viewModelScope.launch(io) {
            patient.value?.let { patientRepo.deletePatient(it) }
            withContext(Dispatchers.Main) { onDone() }
        }
    }
}
```

---

## TASK 7 — PATIENT DETAIL SCREEN
### File: `presentation/patients/PatientDetailScreen.kt`

Implement `PatientDetailScreen` as a full composable using `HorizontalPager` with 4 tabs.

### 7.1 Screen scaffold
```kotlin
@Composable
fun PatientDetailScreen(
    patientId: Long,
    navController: NavController,
    vm: PatientDetailViewModel = hiltViewModel()
) {
    val patient    by vm.patient.collectAsStateWithLifecycle()
    val analytics  by vm.analytics.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope      = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            PatientDetailTopBar(
                patient = patient,
                onBack = navController::popBackStack,
                onEdit = { navController.navigate("patients/${patientId}/edit") },
                onNewVisit = { navController.navigate("visit/new?patientId=${patientId}") },
                onDelete = { showDeleteDialog = true }
            )
        }
    ) { padding ->
        patient?.let { p ->
            Column(Modifier.padding(padding)) {
                // Collapsible header card
                PatientHeaderCard(patient = p, analytics = analytics)

                // Quick stats strip
                QuickStatsStrip(analytics = analytics)

                // Tab row
                val tabTitles = listOf("Overview", "Visits", "Trends", "Media")
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding = 16.dp,
                    divider = { Divider(color = Slate200) }
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(title, style = MaterialTheme.typography.titleSmall) }
                        )
                    }
                }

                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                    when (page) {
                        0 -> OverviewTab(patient = p, analytics = analytics, navController = navController, patientId = patientId)
                        1 -> VisitsTab(vm = vm, navController = navController, patientId = patientId)
                        2 -> TrendsTab(analytics = analytics)
                        3 -> MediaTab(vm = vm)
                    }
                }
            }
        } ?: LoadingOverlay(isLoading = true)
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = "Delete Patient",
            message = "This will permanently delete the patient and all visits.",
            confirmLabel = "Delete",
            confirmColor = Rose500,
            onConfirm = { vm.deletePatient { navController.popBackStack() } },
            onDismiss = { showDeleteDialog = false }
        )
    }
}
```

### 7.2 PatientDetailTopBar
```kotlin
@Composable
fun PatientDetailTopBar(
    patient: Patient?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onNewVisit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
        },
        title = { Text(patient?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        actions = {
            IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "Edit") }
            IconButton(onClick = onNewVisit) { Icon(Icons.Rounded.Add, "New Visit") }
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
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

### 7.3 PatientHeaderCard
```kotlin
// ElevatedCard with collapsible animation (expand/collapse with chevron icon)
// Collapsed: Avatar + Name + key chips in one row
// Expanded: Full info grid
//
// Content when expanded:
//   Row: Large PatientAvatar(64.dp) | Column: Name (headlineMedium), chips row (Age/Gender/Blood Group)
//   Divider
//   Contact row: Icons.Phone + mobile (clickable → Intent.ACTION_DIAL)
//                Icons.Email + email (clickable → Intent.ACTION_SENDTO) if not null
//   Address row: Icons.LocationOn + address (clickable → Maps intent) if not null
//   Category chips: FlowRow of AssistChips for patient.category
```

### 7.4 QuickStatsStrip
```kotlin
// LazyRow of 5 small info pills:
// Total Visits | Last Visit | Avg/month (1 decimal) | Followup Rate (%) | Dominant Element
// Each pill: Column { Text(value, titleMedium, Indigo600), Text(label, labelSmall, Slate600) }
// Separated by vertical Dividers (24.dp tall, 1.dp wide)
```

### 7.5 OverviewTab
```kotlin
// LazyColumn with:
// 1. Upcoming followup alert card (Amber500 tint) — shown only if analytics.upcomingFollowup != null
//    Content: "Next Followup: {date}" with alarm icon, tap → go to Visits tab
// 2. Dominant Nadi & Element mini-card:
//    Row: NadiChip(analytics.dominantNadi) + ElementBadge(analytics.dominantElement)
// 3. Top Complaints card: numbered list of analytics.topComplaints
// 4. Medical history card: render patient.medicalHistory as HTML using
//    AndroidView wrapping a TextView with Html.fromHtml(), or show "No history recorded"
// 5. Recent Visits preview: last 3 visits from vm.visits as compact cards (date + complaint snippet)
//    with "See All" button → scrolls pager to Visits tab
```

### 7.6 VisitsTab
```kotlin
// LazyColumn with:
// 1. Search + filter row:
//    OutlinedTextField (query), Nadi filter dropdown, sort toggle button
// 2. For each visit in vm.filteredVisits:
//    ExpandableVisitCard(visit, isInitialVisit = visit == vm.visits.value.minByOrNull { it.dateTime })
//
// ExpandableVisitCard:
//   Collapsed header: date chip, "Initial Visit" badge if applicable, complaint snippet
//   Expanded body (animated with animateContentSize):
//     - Yogic params row: NadiChip + ElementBadge + Mandala chip + Side chip
//     - Doctor row: "Dr. Nadi: {doctorNadi}" + "Element: {before} → {after}"
//     - Temporal row: "{paksha.displayName()} · Tithi {tithi} · {tithiElement.displayName()}"
//     - Complaint: AndroidView(TextView) with Html.fromHtml(chiefComplaint)
//     - Prescription: same
//     - Disease categories: FlowRow of SuggestionChips
//     - Custom fields: if not empty, list as key: value pairs
//     - Media: LazyRow of async image thumbnails (80.dp squares, rounded corners)
//     - Followup: if set, "Followup: {date}" row with Amber500 alarm icon
//     - Action row: TextButton("Edit") → navController.navigate("visit/{id}/edit")
//                   TextButton("Delete", Rose500) → confirm then vm.deleteVisit(visit)
```

### 7.7 TrendsTab
```kotlin
// LazyColumn with:
//
// 1. Visits over 6 months (Vico ColumnChart):
//    - X-axis: last 6 month abbreviations
//    - Y-axis: visit count
//    - Compute from vm.visits grouped by month
//
// 2. Element Distribution (horizontal bar list using TrendsChart-style):
//    For each element in Element.values(), compute count from vm.visits:
//    Row: ElementBadge, LinearProgressIndicator(count/totalVisits), count text
//    Color each bar with the element's semantic color
//
// 3. Nadi Frequency (same horizontal bar style):
//    For each Nadi, compute count, use NadiIda/NadiPingala/NadiSushumna colors
//
// 4. Treatment Insights row:
//    StatCard: Followup Rate (%), Avg gap between visits (days), Total visits
```

### 7.8 MediaTab
```kotlin
// Collect all MediaEntry items from vm.visits.value.flatMap { it.mediaEntries }
// If empty: EmptyState(icon = Icons.Rounded.PhotoLibrary, message = "No media attached")
// Else: LazyVerticalStaggeredGrid(columns = StaggeredGridCells.Fixed(3)):
//   For each MediaEntry:
//     Column {
//       AsyncImage(model = entry.filePath, contentScale = ContentScale.Crop,
//                  modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp))
//                  .clickable { /* open full-screen viewer */ })
//       Text(entry.instant().formatDateOnly(), labelSmall, Slate400, textAlign = Center)
//     }
//
// Full-screen image viewer: use a Dialog with a zoomable AsyncImage
// (implement basic pinch-to-zoom using transformable modifier and rememberTransformableState)
```

---

## TASK 8 — PATIENTS NAV GRAPH ADDITIONS
### Update: `presentation/navigation/NavHost.kt`

Add these route entries to the existing NavHost (do not remove existing routes):

```kotlin
// Patient detail — pass patientId as Int arg
composable(
    route = "patients/{patientId}",
    arguments = listOf(navArgument("patientId") { type = NavType.LongType })
) { backStack ->
    PatientDetailScreen(
        patientId = backStack.arguments!!.getLong("patientId"),
        navController = navController
    )
}

// Patient new/edit form (placeholder — full implementation in Phase 3)
composable(route = "patients/new") {
    // Temporary: navigate to visit form with no patient pre-fill
    // Replace in Phase 3 with proper PatientFormScreen
    Text("Patient form — coming in Phase 3", Modifier.fillMaxSize().wrapContentSize())
}
```

---

## COMPLETION CHECKLIST

Verify before finishing:
- [ ] `DashboardScreen` compiles with no unresolved references
- [ ] `CountUpText` animates correctly (targets the exact Int value)
- [ ] `PatientListScreen` grid and list toggle animations work
- [ ] `SwipeToDismissBox` imports are from `androidx.compose.material3`
- [ ] `PatientDetailScreen` pager has exactly 4 pages (0–3)
- [ ] `PatientDetailViewModel` `SavedStateHandle` key is `"patientId"` matching NavHost arg name
- [ ] `ExpandableVisitCard` uses `animateContentSize()` from `androidx.compose.animation`
- [ ] All `Html.fromHtml()` calls use `HtmlCompat.FROM_HTML_MODE_COMPACT` flag
- [ ] `Extensions.kt` is imported wherever `timeAgo()`, `formatDisplay()`, `stripHtml()` are used
- [ ] No hardcoded strings for patient-facing labels — use the enum `displayName()` helpers
- [ ] `FlowRow` is imported from `androidx.compose.foundation.layout`
- [ ] All new ViewModels are annotated `@HiltViewModel` with `@Inject constructor`
