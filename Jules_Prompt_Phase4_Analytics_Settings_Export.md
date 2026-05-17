# Jules Prompt — SwaraPulse Phase 4
## Screens: Analytics · Settings · Export System (PDF / Excel / JSON)

> Feed this entire file to Jules as a single task.
> Phases 1–3 are complete and compiling. Do NOT modify any existing file unless
> explicitly told to UPDATE it. Write complete, working Kotlin — no stubs.

---

## CONTEXT & CONSTRAINTS

Package root: `com.swarapulse`
All previous entities, DAOs, repos, theme tokens, and components are available.
New dependencies — confirm in `build.gradle.kts` before proceeding:

```kotlin
// Excel export
implementation("org.apache.poi:poi-ooxml:5.2.5")

// PDF (built-in Android API — no extra dep needed)
// android.graphics.pdf.PdfDocument

// Vico charts
implementation("com.patrykandpatrick.vico:compose-m3:1.13.1")
implementation("com.patrykandpatrick.vico:core:1.13.1")

// File sharing (built-in FileProvider — already added in Phase 3)

// WorkManager (already added Phase 3)
```

Apache POI pulls in large transitive deps. Add to `android` block in `build.gradle.kts`:
```kotlin
packaging {
    resources {
        excludes += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE",
            "META-INF/LICENSE.txt",
            "META-INF/NOTICE",
            "META-INF/NOTICE.txt",
            "META-INF/*.kotlin_module"
        )
    }
}
```

---

## TASK 1 — ANALYTICS ENGINE (pure business logic)
### File: `domain/usecase/AnalyticsEngine.kt`

```kotlin
package com.swarapulse.domain.usecase

import com.swarapulse.data.db.entity.*
import com.swarapulse.presentation.util.stripHtml
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.datetime.*

// ── Output models ────────────────────────────────────────────────────────────

data class AnalyticsResult(
    // Key metrics
    val totalPatients: Int,
    val totalVisits: Int,
    val avgVisitsPerPatient: Float,
    val uniqueComplaintsCount: Int,

    // Correlation insights
    val nadiAlignmentPercent: Float,      // patient nadi displayName == doctorNadi (case-insensitive)
    val elementAlignmentPercent: Float,   // patient element displayName == doctorElementBefore
    val tithiElementCorrelationPercent: Float, // tithiElement == patientElement
    val mostCommonNadiPairing: String,    // "Ida → Pingala" style string

    // Distribution maps (count per enum value)
    val patientNadiDist: Map<Nadi, Int>,
    val patientElementDist: Map<Element, Int>,
    val mandalaDist: Map<Mandala, Int>,
    val patientSideDist: Map<PatientSide, Int>,
    val doctorNadiDist: Map<String, Int>,
    val doctorElementDist: Map<String, Int>,
    val tithiDist: Map<String, Int>,          // tithi "1"–"15"/"Other" → count
    val tithiElementDist: Map<Element, Int>,

    // Demographics
    val genderDist: Map<Gender, Int>,
    val ageGroupDist: Map<String, Int>,   // keys: "Infant","Child","Teen","Young Adult","Adult","Middle Age","Senior"

    // Complaints
    val topComplaints: List<Pair<String, Int>>,   // (complaint text, count), top 10

    // Timeline: visits per month for last 6 months
    val visitTimeline: List<Pair<String, Int>>,   // ("Jan 24" → count), oldest first

    // Raw visits for table
    val visits: List<Visit>,
    val patientMap: Map<Long, Patient>
)

// ── Engine ───────────────────────────────────────────────────────────────────

@Singleton
class AnalyticsEngine @Inject constructor() {

    fun compute(
        patients: List<Patient>,
        visits: List<Visit>,
        filterFromMs: Long?           // null = all time; else epoch ms lower bound
    ): AnalyticsResult {

        val filteredVisits = if (filterFromMs != null)
            visits.filter { it.dateTime.toEpochMilliseconds() >= filterFromMs }
        else visits

        val involvedPatientIds = filteredVisits.map { it.patientId }.toSet()
        val involvedPatients   = patients.filter { it.id in involvedPatientIds }
        val patientMap         = patients.associateBy { it.id }

        // ── Correlation ────────────────────────────────────────────────────
        val nadiAligned = filteredVisits.count {
            it.patientNadi.displayName().equals(it.doctorNadi.trim(), ignoreCase = true)
        }
        val elemAligned = filteredVisits.count {
            it.patientElement.displayName().equals(it.doctorElementBefore.trim(), ignoreCase = true)
        }
        val tithiElemCorr = filteredVisits.count {
            it.tithiElement == it.patientElement
        }
        val total = filteredVisits.size.coerceAtLeast(1)

        // Most common patient-doctor nadi pairing
        val pairingCounts = filteredVisits
            .groupingBy { "${it.patientNadi.displayName()} → ${it.doctorNadi}" }
            .eachCount()
        val mostCommonPairing = pairingCounts.maxByOrNull { it.value }?.key ?: "–"

        // ── Distributions ─────────────────────────────────────────────────
        val patientNadiDist    = filteredVisits.groupingBy { it.patientNadi }.eachCount()
        val patientElementDist = filteredVisits.groupingBy { it.patientElement }.eachCount()
        val mandalaDist        = filteredVisits.groupingBy { it.patientMandala }.eachCount()
        val patientSideDist    = filteredVisits.groupingBy { it.patientSide }.eachCount()
        val doctorNadiDist     = filteredVisits.groupingBy { it.doctorNadi.trim() }.eachCount()
        val doctorElementDist  = filteredVisits.groupingBy { it.doctorElementBefore.trim() }.eachCount()
        val tithiDist          = filteredVisits.groupingBy { it.tithi }.eachCount()
        val tithiElementDist   = filteredVisits.groupingBy { it.tithiElement }.eachCount()

        // ── Demographics ──────────────────────────────────────────────────
        val genderDist = involvedPatients.groupingBy { it.gender }.eachCount()
        val ageGroupDist = involvedPatients.groupingBy { ageGroup(it.age) }.eachCount()

        // ── Complaints ────────────────────────────────────────────────────
        val complaintTokens = filteredVisits
            .flatMap { v ->
                v.chiefComplaint.stripHtml()
                    .split(Regex("[,;.\\n]"))
                    .map { it.trim() }
                    .filter { it.length in 3..80 }
            }
        val complaintCounts = complaintTokens
            .groupingBy { it.lowercase() }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key.replaceFirstChar { c -> c.uppercase() } to it.value }

        // ── Timeline: visits per month, last 6 months ─────────────────────
        val now = Clock.System.now()
        val tz  = TimeZone.currentSystemDefault()
        val timeline = (5 downTo 0).map { monthsBack ->
            val target = now.minus(monthsBack * 30, DateTimeUnit.DAY, tz)
                            .toLocalDateTime(tz)
            val label  = "${target.month.name.take(3).replaceFirstChar { it.uppercase() }} ${target.year % 100}"
            val count  = filteredVisits.count { v ->
                val ldt = v.dateTime.toLocalDateTime(tz)
                ldt.year == target.year && ldt.month == target.month
            }
            label to count
        }

        // ── Unique complaints ─────────────────────────────────────────────
        val uniqueComplaints = filteredVisits
            .map { it.chiefComplaint.stripHtml().take(60).lowercase().trim() }
            .filter { it.isNotBlank() }
            .toSet().size

        return AnalyticsResult(
            totalPatients              = involvedPatients.size,
            totalVisits                = filteredVisits.size,
            avgVisitsPerPatient        = filteredVisits.size.toFloat() / involvedPatients.size.coerceAtLeast(1),
            uniqueComplaintsCount      = uniqueComplaints,
            nadiAlignmentPercent       = nadiAligned * 100f / total,
            elementAlignmentPercent    = elemAligned * 100f / total,
            tithiElementCorrelationPercent = tithiElemCorr * 100f / total,
            mostCommonNadiPairing      = mostCommonPairing,
            patientNadiDist            = patientNadiDist,
            patientElementDist         = patientElementDist,
            mandalaDist                = mandalaDist,
            patientSideDist            = patientSideDist,
            doctorNadiDist             = doctorNadiDist,
            doctorElementDist          = doctorElementDist,
            tithiDist                  = tithiDist,
            tithiElementDist           = tithiElementDist,
            genderDist                 = genderDist,
            ageGroupDist               = ageGroupDist,
            topComplaints              = complaintCounts,
            visitTimeline              = timeline,
            visits                     = filteredVisits,
            patientMap                 = patientMap
        )
    }

    private fun ageGroup(age: Int): String = when (age) {
        in 0..2   -> "Infant"
        in 3..12  -> "Child"
        in 13..18 -> "Teen"
        in 19..35 -> "Young Adult"
        in 36..50 -> "Adult"
        in 51..65 -> "Middle Age"
        else      -> "Senior"
    }
}
```

---

## TASK 2 — ANALYTICS VIEWMODEL
### File: `presentation/analytics/AnalyticsViewModel.kt`

```kotlin
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val patientRepo: PatientRepository,
    private val visitRepo: VisitRepository,
    private val engine: AnalyticsEngine,
    @IoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    enum class TimeRange(val label: String, val daysBack: Int?) {
        ALL("All Time", null),
        DAYS_30("30 Days", 30),
        DAYS_90("90 Days", 90),
        MONTHS_6("6 Months", 180),
        YEAR_1("1 Year", 365)
    }

    private val _timeRange = MutableStateFlow(TimeRange.ALL)
    val timeRange: StateFlow<TimeRange> = _timeRange.asStateFlow()

    sealed class UiState {
        object Loading : UiState()
        data class Success(val result: AnalyticsResult) : UiState()
        object Empty : UiState()
    }

    val uiState: StateFlow<UiState> = combine(
        patientRepo.getAllPatients(),
        _timeRange
    ) { patients, range ->
        // Fetch all visits once (visits don't need to be a Flow here — combine handles reactivity)
        patients to range
    }
        .flatMapLatest { (patients, range) ->
            kotlinx.coroutines.flow.flow {
                emit(UiState.Loading)
                val allVisits = withContext(io) { visitRepo.getAllVisitsOnce() }
                val fromMs = range.daysBack?.let {
                    Clock.System.now()
                        .minus(it, DateTimeUnit.DAY, TimeZone.currentSystemDefault())
                        .toEpochMilliseconds()
                }
                val result = withContext(io) { engine.compute(patients, allVisits, fromMs) }
                emit(if (result.totalVisits == 0) UiState.Empty else UiState.Success(result))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    fun setTimeRange(range: TimeRange) { _timeRange.value = range }
}
```

### UPDATE: `di/DatabaseModule.kt`

Add `AnalyticsEngine` singleton binding:
```kotlin
@Provides @Singleton
fun provideAnalyticsEngine(): AnalyticsEngine = AnalyticsEngine()
```

---

## TASK 3 — ANALYTICS SCREEN
### File: `presentation/analytics/AnalyticsScreen.kt`

Implement the full analytics screen as a `LazyColumn`. All chart sections
use the `HorizontalBarSection` composable defined below.

### 3.1 Screen scaffold
```kotlin
@Composable
fun AnalyticsScreen(
    navController: NavController,
    vm: AnalyticsViewModel = hiltViewModel()
) {
    val uiState   by vm.uiState.collectAsStateWithLifecycle()
    val timeRange by vm.timeRange.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analytics", style = MaterialTheme.typography.headlineMedium,
                               fontFamily = CormorantGaramond) }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
                                           top = padding.calculateTopPadding() + 8.dp,
                                           bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Time range filter chips
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AnalyticsViewModel.TimeRange.values()) { range ->
                        FilterChip(
                            selected = timeRange == range,
                            onClick  = { vm.setTimeRange(range) },
                            label    = { Text(range.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            when (val state = uiState) {
                is AnalyticsViewModel.UiState.Loading -> item {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Indigo600)
                    }
                }
                is AnalyticsViewModel.UiState.Empty -> item {
                    EmptyState(icon = Icons.Rounded.BarChart,
                               message = "No visit data for this time range",
                               modifier = Modifier.fillMaxWidth().height(300.dp))
                }
                is AnalyticsViewModel.UiState.Success -> {
                    val r = state.result
                    analyticsContent(r)
                }
            }
        }
    }
}
```

### 3.2 `analyticsContent` — extension on `LazyListScope`
```kotlin
private fun LazyListScope.analyticsContent(r: AnalyticsResult) {

    // ── Key metrics ───────────────────────────────────────────────────────
    item {
        SectionHeader("Key Metrics", Icons.Rounded.Insights)
    }
    item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item { StatCard("Patients",    r.totalPatients.toString(),    Icons.Rounded.People,        Indigo600,  Modifier.width(130.dp)) }
            item { StatCard("Visits",      r.totalVisits.toString(),      Icons.Rounded.Assignment,    Purple600,  Modifier.width(130.dp)) }
            item { StatCard("Avg/Patient", "%.1f".format(r.avgVisitsPerPatient), Icons.Rounded.TrendingUp, Cyan500, Modifier.width(130.dp)) }
            item { StatCard("Complaints",  r.uniqueComplaintsCount.toString(), Icons.Rounded.Healing, Rose500,    Modifier.width(130.dp)) }
        }
    }

    // ── Correlation insights ──────────────────────────────────────────────
    item { SectionHeader("Correlation Insights", Icons.Rounded.Analytics) }
    item { CorrelationInsightsCard(r) }

    // ── Patient characteristics ───────────────────────────────────────────
    item { SectionHeader("Patient Characteristics", Icons.Rounded.Person) }
    item {
        AnalyticsTwoColumnGrid {
            HorizontalBarSection(
                title = "Patient Nadi",
                data  = r.patientNadiDist.entries
                    .sortedByDescending { it.value }
                    .map { (nadi, count) ->
                        BarEntry(nadi.displayName(), count, r.totalVisits,
                                 nadiColor(nadi))
                    }
            )
            HorizontalBarSection(
                title = "Patient Element",
                data  = r.patientElementDist.entries
                    .sortedByDescending { it.value }
                    .map { (el, count) ->
                        BarEntry(el.displayName(), count, r.totalVisits, elementColor(el))
                    }
            )
            HorizontalBarSection(
                title = "Mandala",
                data  = r.mandalaDist.entries
                    .sortedByDescending { it.value }
                    .map { (m, count) ->
                        BarEntry(m.name.lowercase().replaceFirstChar { it.uppercase() },
                                 count, r.totalVisits,
                                 if (m == Mandala.IDA) NadiIda else if (m == Mandala.PINGALA) NadiPingala else Slate400)
                    }
            )
            HorizontalBarSection(
                title = "Sitting Position",
                data  = r.patientSideDist.entries
                    .sortedByDescending { it.value }
                    .map { (side, count) ->
                        BarEntry(side.name.lowercase().replaceFirstChar { it.uppercase() },
                                 count, r.totalVisits, Indigo600)
                    }
            )
        }
    }

    // ── Doctor & temporal factors ─────────────────────────────────────────
    item { SectionHeader("Doctor & Temporal Factors", Icons.Rounded.MedicalServices) }
    item {
        AnalyticsTwoColumnGrid {
            HorizontalBarSection(
                title = "Doctor Nadi",
                data  = r.doctorNadiDist.entries
                    .sortedByDescending { it.value }.take(6)
                    .map { (k, v) -> BarEntry(k.ifBlank { "–" }, v, r.totalVisits, Emerald500) }
            )
            HorizontalBarSection(
                title = "Doctor Element",
                data  = r.doctorElementDist.entries
                    .sortedByDescending { it.value }.take(6)
                    .map { (k, v) -> BarEntry(k.ifBlank { "–" }, v, r.totalVisits, Cyan500) }
            )
            HorizontalBarSection(
                title = "Tithi Distribution",
                data  = r.tithiDist.entries
                    .sortedByDescending { it.value }.take(8)
                    .map { (k, v) -> BarEntry(k, v, r.totalVisits, Amber500) }
            )
            HorizontalBarSection(
                title = "Tithi Element",
                data  = r.tithiElementDist.entries
                    .sortedByDescending { it.value }
                    .map { (el, count) -> BarEntry(el.displayName(), count, r.totalVisits, elementColor(el)) }
            )
        }
    }

    // ── Demographics ──────────────────────────────────────────────────────
    item { SectionHeader("Demographics", Icons.Rounded.Groups) }
    item {
        AnalyticsTwoColumnGrid {
            HorizontalBarSection(
                title = "Gender",
                data  = r.genderDist.entries
                    .sortedByDescending { it.value }
                    .map { (g, count) ->
                        BarEntry(g.name.lowercase().replaceFirstChar { it.uppercase() },
                                 count, r.totalPatients,
                                 if (g == Gender.MALE) Blue400 else if (g == Gender.FEMALE) Rose400 else Slate400)
                    }
            )
            HorizontalBarSection(
                title = "Age Groups",
                data  = listOf("Infant","Child","Teen","Young Adult","Adult","Middle Age","Senior")
                    .mapNotNull { label ->
                        val count = r.ageGroupDist[label] ?: return@mapNotNull null
                        BarEntry(label, count, r.totalPatients, Purple600)
                    }
            )
        }
    }

    // ── Visit timeline (Vico ColumnChart) ─────────────────────────────────
    item { SectionHeader("Visit Timeline (6 months)", Icons.Rounded.Timeline) }
    item { VisitTimelineChart(r.visitTimeline) }

    // ── Top complaints ────────────────────────────────────────────────────
    item { SectionHeader("Top Chief Complaints", Icons.Rounded.Healing) }
    item { TopComplaintsSection(r.topComplaints, r.totalVisits) }

    // ── Visit records table ────────────────────────────────────────────────
    item { SectionHeader("Visit Records", Icons.Rounded.TableChart) }
    item { VisitRecordsTable(visits = r.visits, patientMap = r.patientMap) }
}
```

### 3.3 `CorrelationInsightsCard`
```kotlin
@Composable
fun CorrelationInsightsCard(r: AnalyticsResult) {
    GradientCard(gradient = GradientPrimary) {
        Text("Alignment & Correlations",
             style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(12.dp))
        InsightRow("Nadi Alignment",    r.nadiAlignmentPercent,    Icons.Rounded.CompareArrows)
        InsightRow("Element Alignment", r.elementAlignmentPercent, Icons.Rounded.Layers)
        InsightRow("Tithi–Element Correlation", r.tithiElementCorrelationPercent, Icons.Rounded.NightsStay)
        Spacer(Modifier.height(8.dp))
        Divider(color = Color.White.copy(alpha = 0.2f))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.StarHalf, null, tint = Color.White.copy(0.8f),
                 modifier = Modifier.size(16.dp))
            Text("Top Nadi Pairing: ${r.mostCommonNadiPairing}",
                 style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.9f))
        }
    }
}

@Composable
private fun InsightRow(label: String, percent: Float, icon: ImageVector) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.85f))
                Text("%.0f%%".format(percent), style = MaterialTheme.typography.labelSmall,
                     color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (percent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f)
            )
        }
    }
    Spacer(Modifier.height(10.dp))
}
```

### 3.4 `HorizontalBarSection` + `BarEntry`
```kotlin
data class BarEntry(val label: String, val count: Int, val total: Int, val color: Color)

@Composable
fun HorizontalBarSection(title: String, data: List<BarEntry>, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth(),
         shape = RoundedCornerShape(12.dp),
         colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
         elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = Slate600)
            if (data.isEmpty()) {
                Text("No data", style = MaterialTheme.typography.bodySmall, color = Slate400)
            } else {
                data.forEach { entry ->
                    val fraction = if (entry.total > 0) entry.count.toFloat() / entry.total else 0f
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(entry.label, style = MaterialTheme.typography.bodySmall,
                                 maxLines = 1, overflow = TextOverflow.Ellipsis,
                                 modifier = Modifier.weight(1f))
                            Text("${entry.count} (%.0f%%)".format(fraction * 100),
                                 style = MaterialTheme.typography.labelSmall, color = Slate400)
                        }
                        // Animated progress bar
                        val animatedFraction by animateFloatAsState(
                            targetValue = fraction,
                            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                            label = "bar_${entry.label}"
                        )
                        LinearProgressIndicator(
                            progress = { animatedFraction },
                            modifier = Modifier.fillMaxWidth().height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = entry.color,
                            trackColor = entry.color.copy(alpha = 0.12f)
                        )
                    }
                }
            }
        }
    }
}
```

### 3.5 `AnalyticsTwoColumnGrid`
```kotlin
// Renders children in a 2-column grid inside a Column
// Takes a vararg of @Composable lambdas and pairs them up
@Composable
fun AnalyticsTwoColumnGrid(content: @Composable () -> Unit) {
    // Use SubcomposeLayout trick: wrap content items in a Column that lays them out 2-per-row
    // Simpler approach: accept List<@Composable () -> Unit> via a builder DSL
    // Implementation: use a custom layout or just a FlowRow with fixed 50% width per cell
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        maxItemsInEachRow = 2
    ) {
        content()
    }
}
// Each child inside AnalyticsTwoColumnGrid must use Modifier.weight(1f) or
// Modifier.fillMaxWidth(0.47f) to fit in 2 columns.
// Update all HorizontalBarSection calls inside AnalyticsTwoColumnGrid to pass
// modifier = Modifier.weight(1f)
```

### 3.6 `VisitTimelineChart` (Vico ColumnChart)
```kotlin
@Composable
fun VisitTimelineChart(timeline: List<Pair<String, Int>>) {
    val modelProducer = remember { CartesianChartModelProducer.build() }

    LaunchedEffect(timeline) {
        modelProducer.tryRunTransaction {
            columnSeries { series(timeline.map { it.second.toFloat() }) }
        }
    }

    Card(shape = RoundedCornerShape(12.dp),
         colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
         elevation = CardDefaults.cardElevation(1.dp),
         modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberColumnCartesianLayer(
                        columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                            rememberLineComponent(
                                color = Indigo600,
                                thickness = 16.dp,
                                shape = Shape.rounded(allPercent = 4)
                            )
                        )
                    ),
                    startAxis = rememberStartAxis(),
                    bottomAxis = rememberBottomAxis(
                        valueFormatter = { x, _, _ ->
                            timeline.getOrNull(x.toInt())?.first ?: ""
                        }
                    )
                ),
                modelProducer = modelProducer,
                modifier = Modifier.fillMaxWidth().height(180.dp)
            )
        }
    }
}
```

### 3.7 `TopComplaintsSection`
```kotlin
@Composable
fun TopComplaintsSection(complaints: List<Pair<String, Int>>, totalVisits: Int) {
    Card(shape = RoundedCornerShape(12.dp),
         colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
         elevation = CardDefaults.cardElevation(1.dp),
         modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (complaints.isEmpty()) {
                Text("No complaint data yet.", style = MaterialTheme.typography.bodySmall, color = Slate400)
            } else {
                complaints.forEachIndexed { index, (complaint, count) ->
                    val fraction = if (totalVisits > 0) count.toFloat() / totalVisits else 0f
                    val medalColor = when (index) {
                        0 -> Color(0xFFFFD700)   // gold
                        1 -> Color(0xFFC0C0C0)   // silver
                        2 -> Color(0xFFCD7F32)   // bronze
                        else -> Slate400
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Rank badge
                        Surface(shape = CircleShape, color = medalColor.copy(alpha = 0.15f),
                                modifier = Modifier.size(28.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("${index + 1}", style = MaterialTheme.typography.labelSmall,
                                     color = medalColor, fontWeight = FontWeight.Bold)
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(complaint, style = MaterialTheme.typography.bodySmall,
                                     maxLines = 1, overflow = TextOverflow.Ellipsis,
                                     modifier = Modifier.weight(1f))
                                Text("$count (%.0f%%)".format(fraction * 100),
                                     style = MaterialTheme.typography.labelSmall, color = Slate400)
                            }
                            val animFraction by animateFloatAsState(fraction,
                                animationSpec = tween(700, easing = FastOutSlowInEasing),
                                label = "complaint_$index")
                            LinearProgressIndicator(
                                progress = { animFraction },
                                modifier = Modifier.fillMaxWidth().height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = Rose500,
                                trackColor = Rose500.copy(alpha = 0.1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
```

### 3.8 `VisitRecordsTable`
```kotlin
@Composable
fun VisitRecordsTable(visits: List<Visit>, patientMap: Map<Long, Patient>) {
    val columns = listOf("Date", "Patient", "P.Nadi", "P.Element", "Mandala",
                         "Dr.Nadi", "Dr.Element", "Tithi", "Match")
    Card(shape = RoundedCornerShape(12.dp),
         colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
         elevation = CardDefaults.cardElevation(1.dp),
         modifier = Modifier.fillMaxWidth()) {
        Column {
            // Header row
            Row(Modifier.background(Indigo600.copy(alpha = 0.08f))
                        .horizontalScroll(rememberScrollState())) {
                columns.forEach { col ->
                    Text(col, style = MaterialTheme.typography.labelSmall,
                         color = Indigo600, fontWeight = FontWeight.SemiBold,
                         modifier = Modifier.width(90.dp).padding(8.dp))
                }
            }
            Divider()
            // Data rows (max 50 for performance)
            val displayVisits = visits.sortedByDescending { it.dateTime }.take(50)
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                itemsIndexed(displayVisits) { index, visit ->
                    val patient = patientMap[visit.patientId]
                    val nadiMatch = visit.patientNadi.displayName()
                        .equals(visit.doctorNadi.trim(), ignoreCase = true)
                    val elemMatch = visit.patientElement.displayName()
                        .equals(visit.doctorElementBefore.trim(), ignoreCase = true)
                    val anyMatch  = nadiMatch || elemMatch

                    Row(
                        Modifier
                            .background(if (index % 2 == 0) Color.Transparent
                                        else Slate100.copy(alpha = 0.5f))
                            .horizontalScroll(rememberScrollState())
                    ) {
                        TableCell(visit.dateTime.formatDateOnly())
                        TableCell(patient?.name ?: "–")
                        TableCell(visit.patientNadi.displayName(),
                                  color = if (nadiMatch) Emerald500 else Color.Unspecified)
                        TableCell(visit.patientElement.displayName(),
                                  color = if (elemMatch) Emerald500 else Color.Unspecified)
                        TableCell(visit.patientMandala.name.lowercase()
                                      .replaceFirstChar { it.uppercase() })
                        TableCell(visit.doctorNadi.ifBlank { "–" })
                        TableCell(visit.doctorElementBefore.ifBlank { "–" })
                        TableCell(visit.tithi)
                        // Match indicator
                        Box(Modifier.width(90.dp).padding(8.dp),
                            contentAlignment = Alignment.Center) {
                            Icon(
                                if (anyMatch) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                null,
                                tint = if (anyMatch) Emerald500 else Slate400,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Divider(color = Slate200)
                }
            }
            if (visits.size > 50) {
                Text("Showing 50 of ${visits.size} records",
                     style = MaterialTheme.typography.labelSmall, color = Slate400,
                     modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
private fun TableCell(text: String, color: Color = Color.Unspecified) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color,
         maxLines = 1, overflow = TextOverflow.Ellipsis,
         modifier = Modifier.width(90.dp).padding(horizontal = 8.dp, vertical = 6.dp))
}
```

### 3.9 Color helper functions (add at bottom of `AnalyticsScreen.kt`)
```kotlin
private fun nadiColor(nadi: Nadi): Color = when (nadi) {
    Nadi.IDA       -> NadiIda
    Nadi.PINGALA   -> NadiPingala
    Nadi.SUSHUMNA  -> NadiSushumna
    else           -> Slate400
}

private fun elementColor(element: Element): Color = when (element) {
    Element.AIR   -> ElementAir
    Element.FIRE  -> ElementFire
    Element.SPACE -> ElementSpace
    Element.EARTH -> ElementEarth
    Element.WATER -> ElementWater
    else          -> Slate400
}
```

---

## TASK 4 — EXPORT SYSTEM

### File: `data/export/ExcelExporter.kt`

```kotlin
package com.swarapulse.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.swarapulse.data.db.entity.*
import com.swarapulse.presentation.util.formatDateOnly
import com.swarapulse.presentation.util.formatDisplay
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExcelExporter @Inject constructor(
    private val context: Context
) {
    // ── Export single patient ────────────────────────────────────────────────
    fun exportPatient(patient: Patient, visits: List<Visit>): File {
        val wb = XSSFWorkbook()

        // ── Sheet 1: Patient Info ─────────────────────────────────────────
        val infoSheet = wb.createSheet("Patient Info")
        val headerStyle = wb.createCellStyle().apply {
            fillForegroundColor = IndexedColors.INDIGO.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            setFont(wb.createFont().apply {
                bold = true
                color = IndexedColors.WHITE.index
            })
        }
        val infoRows = listOf(
            "Name"              to patient.name,
            "Age"               to patient.age.toString(),
            "Gender"            to patient.gender.name,
            "Mobile"            to patient.mobile,
            "Email"             to (patient.email ?: "–"),
            "Address"           to (patient.address ?: "–"),
            "Occupation"        to (patient.occupation ?: "–"),
            "Blood Group"       to (patient.bloodGroup ?: "–"),
            "Categories"        to patient.category.joinToString(", "),
            "Active"            to if (patient.isActive) "Yes" else "No",
            "Provisional"       to if (patient.isProvisional) "Yes" else "No",
            "Emergency Contact" to "${patient.emergencyContactName ?: ""} ${patient.emergencyContact ?: ""}".trim(),
            "Last Visit"        to (patient.lastVisitDate?.formatDisplay() ?: "–"),
            "Created"           to patient.createdAt.formatDisplay()
        )
        infoRows.forEachIndexed { i, (key, value) ->
            val row = infoSheet.createRow(i)
            row.createCell(0).apply { setCellValue(key); cellStyle = headerStyle }
            row.createCell(1).setCellValue(value)
        }
        infoSheet.setColumnWidth(0, 6000)
        infoSheet.setColumnWidth(1, 10000)

        // ── Sheet 2: Visits ───────────────────────────────────────────────
        val visitSheet = wb.createSheet("Visits")
        val visitHeaders = listOf(
            "Date/Time", "Mandala", "Patient Nadi", "Patient Element", "Side",
            "Doctor Nadi", "Dr. Element Before", "Dr. Element After",
            "Paksha", "Tithi", "Tithi Element",
            "Chief Complaint", "Prescription", "Disease Categories",
            "Followup Date", "Custom Fields"
        )
        val hRow = visitSheet.createRow(0)
        visitHeaders.forEachIndexed { i, h ->
            hRow.createCell(i).apply { setCellValue(h); cellStyle = headerStyle }
        }
        visits.sortedBy { it.dateTime }.forEachIndexed { rowIdx, v ->
            val row = visitSheet.createRow(rowIdx + 1)
            listOf(
                v.dateTime.formatDisplay(),
                v.patientMandala.name,
                v.patientNadi.displayName(),
                v.patientElement.displayName(),
                v.patientSide.name,
                v.doctorNadi,
                v.doctorElementBefore,
                v.doctorElementAfter ?: "",
                v.paksha.displayName(),
                v.tithi,
                v.tithiElement.displayName(),
                v.chiefComplaint.stripHtml(),
                v.prescription.stripHtml(),
                v.diseaseCategories.joinToString(", "),
                v.followupDate?.formatDisplay() ?: "",
                v.customFields.joinToString("; ") { "${it.label}: ${it.value}" }
            ).forEachIndexed { col, value -> row.createCell(col).setCellValue(value) }
        }
        visitSheet.trackAllColumnsForAutoSizing()
        visitHeaders.indices.forEach { visitSheet.autoSizeColumn(it) }

        // ── Sheet 3: Visit Analytics Summary ─────────────────────────────
        val analyticsSheet = wb.createSheet("Analytics")
        val nadiCounts    = visits.groupingBy { it.patientNadi.displayName() }.eachCount()
        val elementCounts = visits.groupingBy { it.patientElement.displayName() }.eachCount()
        analyticsSheet.createRow(0).createCell(0).apply { setCellValue("Visit Analytics Summary"); cellStyle = headerStyle }
        analyticsSheet.createRow(1).createCell(0).setCellValue("Total Visits: ${visits.size}")
        analyticsSheet.createRow(2).createCell(0).apply { setCellValue("Nadi Counts"); cellStyle = headerStyle }
        var rowN = 3
        nadiCounts.forEach { (k, v) ->
            val r = analyticsSheet.createRow(rowN++)
            r.createCell(0).setCellValue(k); r.createCell(1).setCellValue(v.toDouble())
        }
        analyticsSheet.createRow(rowN++).createCell(0).apply { setCellValue("Element Counts"); cellStyle = headerStyle }
        elementCounts.forEach { (k, v) ->
            val r = analyticsSheet.createRow(rowN++)
            r.createCell(0).setCellValue(k); r.createCell(1).setCellValue(v.toDouble())
        }

        return writeAndShare(wb, "patient_${patient.name.replace(" ","_")}_${System.currentTimeMillis()}.xlsx")
    }

    // ── Export all patients ──────────────────────────────────────────────────
    fun exportAllPatients(patients: List<Patient>, visits: List<Visit>): File {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("All Patients")
        val headerStyle = wb.createCellStyle().apply {
            fillForegroundColor = IndexedColors.INDIGO.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            setFont(wb.createFont().apply { bold = true; color = IndexedColors.WHITE.index })
        }
        val headers = listOf("Name","Age","Gender","Mobile","Email","Blood Group",
                             "Categories","Active","Provisional","Last Visit","Total Visits","Created")
        val hRow = sheet.createRow(0)
        headers.forEachIndexed { i, h -> hRow.createCell(i).apply { setCellValue(h); cellStyle = headerStyle } }

        val visitCountByPatient = visits.groupBy { it.patientId }.mapValues { it.value.size }
        patients.forEachIndexed { rowIdx, p ->
            val row = sheet.createRow(rowIdx + 1)
            listOf(
                p.name, p.age.toString(), p.gender.name, p.mobile,
                p.email ?: "", p.bloodGroup ?: "",
                p.category.joinToString(", "),
                if (p.isActive) "Yes" else "No",
                if (p.isProvisional) "Yes" else "No",
                p.lastVisitDate?.formatDisplay() ?: "–",
                (visitCountByPatient[p.id] ?: 0).toString(),
                p.createdAt.formatDisplay()
            ).forEachIndexed { col, value -> row.createCell(col).setCellValue(value) }
        }
        sheet.trackAllColumnsForAutoSizing()
        headers.indices.forEach { sheet.autoSizeColumn(it) }

        return writeAndShare(wb, "swarapulse_all_patients_${System.currentTimeMillis()}.xlsx")
    }

    // ── Helper: write workbook to cache dir and return File ────────────────
    private fun writeAndShare(wb: XSSFWorkbook, fileName: String): File {
        val dir  = File(context.cacheDir, "exports").also { it.mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { wb.write(it) }
        wb.close()
        return file
    }

    // ── Helper: get shareable URI and start share intent ──────────────────
    fun shareFile(context: Context, file: File, mimeType: String = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export via…").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}

// Extension already in Extensions.kt — import it here
private fun String.stripHtml() = replace(Regex("<[^>]*>"), "").trim()
```

### File: `data/export/PdfExporter.kt`

```kotlin
package com.swarapulse.data.export

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.swarapulse.data.db.entity.*
import com.swarapulse.presentation.util.formatDateOnly
import com.swarapulse.presentation.util.formatDisplay
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfExporter @Inject constructor(
    private val context: Context
) {
    private val pageWidth  = 595   // A4 points width
    private val pageHeight = 842   // A4 points height
    private val margin     = 40f

    // Paint styles
    private val titlePaint = Paint().apply {
        textSize = 20f; color = Color.parseColor("#4F46E5"); typeface = Typeface.DEFAULT_BOLD
    }
    private val headingPaint = Paint().apply {
        textSize = 13f; color = Color.parseColor("#1E293B"); typeface = Typeface.DEFAULT_BOLD
    }
    private val bodyPaint = Paint().apply {
        textSize = 10f; color = Color.parseColor("#334155")
    }
    private val labelPaint = Paint().apply {
        textSize = 9f; color = Color.parseColor("#94A3B8"); typeface = Typeface.DEFAULT_BOLD
    }
    private val accentLinePaint = Paint().apply {
        color = Color.parseColor("#4F46E5"); strokeWidth = 1.5f; style = Paint.Style.STROKE
    }

    fun exportPatientReport(patient: Patient, visits: List<Visit>): File {
        val doc = PdfDocument()
        var pageNum = 1
        var page    = startPage(doc, pageNum++)
        var canvas  = page.canvas
        var y       = margin

        // ── Header ───────────────────────────────────────────────────────
        canvas.drawText("SwaraPulse — Patient Report", margin, y + 20f, titlePaint)
        y += 36f
        canvas.drawLine(margin, y, pageWidth - margin, y, accentLinePaint)
        y += 16f

        // ── Patient info block ────────────────────────────────────────────
        y = drawField(canvas, y, "Name",    patient.name)
        y = drawField(canvas, y, "Age",     "${patient.age} · ${patient.gender.name}")
        y = drawField(canvas, y, "Mobile",  patient.mobile)
        patient.email?.let  { y = drawField(canvas, y, "Email", it) }
        patient.address?.let { y = drawField(canvas, y, "Address", it) }
        patient.bloodGroup?.let { y = drawField(canvas, y, "Blood Group", it) }
        if (patient.category.isNotEmpty())
            y = drawField(canvas, y, "Categories", patient.category.joinToString(", "))
        patient.medicalHistory?.let {
            y = drawWrappedField(canvas, y, "Medical History", it.replace(Regex("<[^>]*>"), "").trim())
        }
        y += 12f
        canvas.drawLine(margin, y, pageWidth - margin, y, accentLinePaint)
        y += 16f

        // ── Visits ────────────────────────────────────────────────────────
        canvas.drawText("Visit Records (${visits.size} total)", margin, y, headingPaint)
        y += 18f

        visits.sortedByDescending { it.dateTime }.forEach { visit ->
            // Check page overflow
            if (y > pageHeight - margin - 120f) {
                doc.finishPage(page)
                page   = startPage(doc, pageNum++)
                canvas = page.canvas
                y      = margin + 20f
            }
            // Visit date header
            canvas.drawText(visit.dateTime.formatDisplay(), margin, y, headingPaint)
            y += 14f

            y = drawField(canvas, y, "Nadi",    visit.patientNadi.displayName())
            y = drawField(canvas, y, "Element", visit.patientElement.displayName())
            y = drawField(canvas, y, "Mandala", visit.patientMandala.name)
            y = drawField(canvas, y, "Dr. Nadi", visit.doctorNadi)
            y = drawField(canvas, y, "Paksha/Tithi", "${visit.paksha.displayName()} / ${visit.tithi}")
            if (visit.chiefComplaint.isNotBlank())
                y = drawWrappedField(canvas, y, "Chief Complaint",
                    visit.chiefComplaint.replace(Regex("<[^>]*>"), "").trim())
            if (visit.prescription.isNotBlank())
                y = drawWrappedField(canvas, y, "Prescription",
                    visit.prescription.replace(Regex("<[^>]*>"), "").trim())
            if (visit.diseaseCategories.isNotEmpty())
                y = drawField(canvas, y, "Categories", visit.diseaseCategories.joinToString(", "))
            visit.followupDate?.let { y = drawField(canvas, y, "Followup", it.formatDisplay()) }

            // Separator
            y += 4f
            val sepPaint = Paint().apply { color = Color.parseColor("#E2E8F0"); strokeWidth = 0.5f }
            canvas.drawLine(margin, y, pageWidth - margin, y, sepPaint)
            y += 10f
        }

        doc.finishPage(page)

        // Write file
        val dir  = File(context.cacheDir, "exports").also { it.mkdirs() }
        val file = File(dir, "patient_${patient.name.replace(" ","_")}_report.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share PDF").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun startPage(doc: PdfDocument, num: Int): PdfDocument.Page =
        doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, num).create())

    private fun drawField(canvas: Canvas, y: Float, label: String, value: String): Float {
        canvas.drawText(label.uppercase(), margin, y, labelPaint)
        canvas.drawText(value, margin + 110f, y, bodyPaint)
        return y + 14f
    }

    private fun drawWrappedField(canvas: Canvas, startY: Float, label: String, value: String): Float {
        canvas.drawText(label.uppercase(), margin, startY, labelPaint)
        var y = startY + 12f
        val maxWidth = pageWidth - margin * 2 - 10f
        val words = value.split(" ")
        var line  = ""
        words.forEach { word ->
            val test = if (line.isEmpty()) word else "$line $word"
            if (bodyPaint.measureText(test) > maxWidth) {
                canvas.drawText(line, margin, y, bodyPaint)
                y   += 12f
                line = word
            } else {
                line = test
            }
        }
        if (line.isNotBlank()) { canvas.drawText(line, margin, y, bodyPaint); y += 12f }
        return y + 4f
    }
}
```

### File: `data/export/JsonBackupManager.kt`

```kotlin
package com.swarapulse.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.swarapulse.data.db.entity.*
import com.swarapulse.data.repository.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class DatabaseBackup(
    val version: Int = 1,
    val exportedAt: Long = Clock.System.now().toEpochMilliseconds(),
    val patients: List<Patient> = emptyList(),
    val visits: List<Visit> = emptyList(),
    val appointments: List<Appointment> = emptyList()
)

@Singleton
class JsonBackupManager @Inject constructor(
    private val context: Context,
    private val patientRepo: PatientRepository,
    private val visitRepo: VisitRepository,
    private val appointmentRepo: AppointmentRepository,
    @com.swarapulse.di.IoDispatcher private val io: CoroutineDispatcher
) {
    private val json = Json {
        prettyPrint          = true
        ignoreUnknownKeys    = true
        encodeDefaults       = true
    }

    // ── Export ────────────────────────────────────────────────────────────
    suspend fun exportToJson(): File = withContext(io) {
        val patients     = patientRepo.getAllPatients().let { flow ->
            // Collect once
            var result = emptyList<Patient>()
            kotlinx.coroutines.flow.first(flow).let { result = it }
            result
        }
        val visits       = visitRepo.getAllVisitsOnce()
        val appointments = appointmentRepo.getAllAppointmentsOnce()

        val backup = DatabaseBackup(
            patients     = patients,
            visits       = visits,
            appointments = appointments
        )
        val jsonStr = json.encodeToString(DatabaseBackup.serializer(), backup)

        val dir  = File(context.cacheDir, "exports").also { it.mkdirs() }
        val file = File(dir, "swarapulse_backup_${System.currentTimeMillis()}.json")
        file.writeText(jsonStr)
        file
    }

    // ── Restore ───────────────────────────────────────────────────────────
    suspend fun restoreFromJson(uri: Uri): RestoreResult = withContext(io) {
        try {
            val jsonStr = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.readText()
                ?: return@withContext RestoreResult.Error("Could not read file")

            val backup = json.decodeFromString(DatabaseBackup.serializer(), jsonStr)

            // Clear existing data
            val existingPatients = patientRepo.getAllPatientsOnce()
            existingPatients.forEach { patientRepo.deletePatient(it) }

            // Re-insert
            backup.patients.forEach     { patientRepo.insertPatient(it) }
            backup.visits.forEach       { visitRepo.insertVisit(it) }
            backup.appointments.forEach { appointmentRepo.insertAppointment(it) }

            RestoreResult.Success(
                patientCount     = backup.patients.size,
                visitCount       = backup.visits.size,
                appointmentCount = backup.appointments.size
            )
        } catch (e: Exception) {
            RestoreResult.Error("Restore failed: ${e.message}")
        }
    }

    fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Save Backup").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    sealed class RestoreResult {
        data class Success(val patientCount: Int, val visitCount: Int, val appointmentCount: Int) : RestoreResult()
        data class Error(val message: String) : RestoreResult()
    }
}
```

### UPDATE: `data/repository/AppointmentRepository.kt`

Add:
```kotlin
suspend fun getAllAppointmentsOnce(): List<Appointment> =
    withContext(ioDispatcher) { dao.getAllAppointmentsOnce() }
```

### UPDATE: `data/db/dao/AppointmentDao.kt`

Add:
```kotlin
@Query("SELECT * FROM appointments")
suspend fun getAllAppointmentsOnce(): List<Appointment>
```

### UPDATE: `data/repository/PatientRepository.kt`

Add:
```kotlin
suspend fun getAllPatientsOnce(): List<Patient> =
    withContext(ioDispatcher) { dao.getAllPatientsOnce() }
```

### UPDATE: `data/db/dao/PatientDao.kt`

Add:
```kotlin
@Query("SELECT * FROM patients")
suspend fun getAllPatientsOnce(): List<Patient>
```

### UPDATE: `di/DatabaseModule.kt`

Provide export classes:
```kotlin
@Provides @Singleton
fun providePdfExporter(@ApplicationContext ctx: Context): PdfExporter = PdfExporter(ctx)

@Provides @Singleton
fun provideExcelExporter(@ApplicationContext ctx: Context): ExcelExporter = ExcelExporter(ctx)

@Provides @Singleton
fun provideJsonBackupManager(
    @ApplicationContext ctx: Context,
    patientRepo: PatientRepository,
    visitRepo: VisitRepository,
    appointmentRepo: AppointmentRepository,
    @IoDispatcher io: CoroutineDispatcher
): JsonBackupManager = JsonBackupManager(ctx, patientRepo, visitRepo, appointmentRepo, io)
```

---

## TASK 5 — SETTINGS VIEWMODEL
### File: `presentation/settings/SettingsViewModel.kt`

```kotlin
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val authManager: AuthManager,
    private val jsonBackup: JsonBackupManager,
    private val excelExporter: ExcelExporter,
    private val patientRepo: PatientRepository,
    private val visitRepo: VisitRepository,
    @IoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    // Profile state
    val displayName: StateFlow<String> = settings.displayName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val title: StateFlow<String> = settings.title
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val clinicName: StateFlow<String> = settings.clinicName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // Security state
    val biometricEnabled: StateFlow<Boolean> = settings.biometricEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val darkMode: StateFlow<Boolean> = settings.darkMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // One-off events
    sealed class UiEvent {
        data class ShowSnackbar(val message: String) : UiEvent()
        data class ShareFile(val file: java.io.File) : UiEvent()
        object RestoreSuccess : UiEvent()
        object LoggedOut : UiEvent()
    }
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // PIN change state
    private val _pinChangeStep = MutableStateFlow(0)  // 0=idle,1=old,2=new,3=confirm
    val pinChangeStep: StateFlow<Int> = _pinChangeStep.asStateFlow()

    // ── Profile ────────────────────────────────────────────────────────────
    fun saveProfile(name: String, title: String, clinic: String) {
        viewModelScope.launch {
            settings.setDisplayName(name)
            settings.setTitle(title)
            settings.setClinicName(clinic)
            _events.emit(UiEvent.ShowSnackbar("Profile saved"))
        }
    }

    // ── Security ────────────────────────────────────────────────────────────
    fun toggleBiometric(enabled: Boolean) {
        viewModelScope.launch { settings.setBiometricEnabled(enabled) }
    }

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch { settings.setDarkMode(enabled) }
    }

    fun changePin(oldPin: String, newPin: String, confirmPin: String) {
        viewModelScope.launch(io) {
            val valid = authManager.verifyPin(oldPin)
            if (!valid) { _events.emit(UiEvent.ShowSnackbar("Current PIN is incorrect")); return@launch }
            if (newPin != confirmPin) { _events.emit(UiEvent.ShowSnackbar("New PINs do not match")); return@launch }
            if (newPin.length < 4)   { _events.emit(UiEvent.ShowSnackbar("PIN must be at least 4 digits")); return@launch }
            authManager.setupPin(newPin)
            _pinChangeStep.value = 0
            _events.emit(UiEvent.ShowSnackbar("PIN updated successfully"))
        }
    }

    // ── Backup ────────────────────────────────────────────────────────────
    fun exportJson() {
        viewModelScope.launch(io) {
            _isLoading.value = true
            try {
                val file = jsonBackup.exportToJson()
                _events.emit(UiEvent.ShareFile(file))
            } catch (e: Exception) {
                _events.emit(UiEvent.ShowSnackbar("Backup failed: ${e.message}"))
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun exportExcel() {
        viewModelScope.launch(io) {
            _isLoading.value = true
            try {
                val patients = patientRepo.getAllPatientsOnce()
                val visits   = visitRepo.getAllVisitsOnce()
                val file     = excelExporter.exportAllPatients(patients, visits)
                _events.emit(UiEvent.ShareFile(file))
            } catch (e: Exception) {
                _events.emit(UiEvent.ShowSnackbar("Excel export failed: ${e.message}"))
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun restoreFromJson(uri: android.net.Uri) {
        viewModelScope.launch(io) {
            _isLoading.value = true
            try {
                when (val result = jsonBackup.restoreFromJson(uri)) {
                    is JsonBackupManager.RestoreResult.Success ->
                        _events.emit(UiEvent.ShowSnackbar(
                            "Restored ${result.patientCount} patients, ${result.visitCount} visits"))
                    is JsonBackupManager.RestoreResult.Error  ->
                        _events.emit(UiEvent.ShowSnackbar(result.message))
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authManager.logout()
            _events.emit(UiEvent.LoggedOut)
        }
    }
}
```

---

## TASK 6 — SETTINGS SCREEN
### File: `presentation/settings/SettingsScreen.kt`

```kotlin
@Composable
fun SettingsScreen(
    navController: NavController,
    vm: SettingsViewModel = hiltViewModel()
) {
    val displayName     by vm.displayName.collectAsStateWithLifecycle()
    val title           by vm.title.collectAsStateWithLifecycle()
    val clinicName      by vm.clinicName.collectAsStateWithLifecycle()
    val biometricEnabled by vm.biometricEnabled.collectAsStateWithLifecycle()
    val darkMode        by vm.darkMode.collectAsStateWithLifecycle()
    val isLoading       by vm.isLoading.collectAsStateWithLifecycle()
    val snackbarHost    = remember { SnackbarHostState() }
    val context         = LocalContext.current
    val scope           = rememberCoroutineScope()

    // Editable local copies
    var nameInput   by remember(displayName)   { mutableStateOf(displayName) }
    var titleInput  by remember(title)         { mutableStateOf(title) }
    var clinicInput by remember(clinicName)    { mutableStateOf(clinicName) }

    // PIN change state
    var showPinDialog by remember { mutableStateOf(false) }

    // Restore file picker
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.restoreFromJson(it) } }

    // Confirm dialogs
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showLogoutConfirm  by remember { mutableStateOf(false) }
    var pendingRestoreUri  by remember { mutableStateOf<android.net.Uri?>(null) }

    // Handle events
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is SettingsViewModel.UiEvent.ShowSnackbar ->
                    scope.launch { snackbarHost.showSnackbar(event.message) }
                is SettingsViewModel.UiEvent.ShareFile    ->
                    jsonBackup_shareFile(context, event.file)   // call jsonBackup.shareFile or excelExporter.shareFile
                is SettingsViewModel.UiEvent.LoggedOut    ->
                    navController.navigate("auth") { popUpTo(0) { inclusive = true } }
                else -> {}
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(title = { Text("Settings", style = MaterialTheme.typography.headlineMedium,
                                     fontFamily = CormorantGaramond) })
        }
    ) { padding ->
        Box {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp, bottom = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                // ── Profile ────────────────────────────────────────────────
                item {
                    SettingsSection(
                        title = "Profile",
                        icon  = Icons.Rounded.Person,
                        color = Indigo600
                    ) {
                        OutlinedTextField(nameInput,   { nameInput = it },   label = { Text("Display Name") },  modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
                        OutlinedTextField(titleInput,  { titleInput = it },  label = { Text("Professional Title") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
                        OutlinedTextField(clinicInput, { clinicInput = it }, label = { Text("Clinic Name") },    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
                        Button(
                            onClick = { vm.saveProfile(nameInput, titleInput, clinicInput) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
                        ) { Text("Save Profile") }
                    }
                }

                // ── Security ───────────────────────────────────────────────
                item {
                    SettingsSection("Security", Icons.Rounded.Lock, Purple600) {
                        SettingsToggleRow("Biometric Authentication", biometricEnabled,
                                          Icons.Rounded.Fingerprint, { vm.toggleBiometric(it) })
                        SettingsToggleRow("Dark Mode", darkMode,
                                          Icons.Rounded.DarkMode, { vm.toggleDarkMode(it) })
                        Divider()
                        SettingsActionRow("Change PIN", Icons.Rounded.Password, Indigo600) {
                            showPinDialog = true
                        }
                    }
                }

                // ── Backup & Restore ────────────────────────────────────────
                item {
                    SettingsSection("Backup & Restore", Icons.Rounded.CloudUpload, Emerald500) {
                        Text("All data is stored locally on this device. " +
                             "Export a backup before clearing or reinstalling the app.",
                             style = MaterialTheme.typography.bodySmall, color = Slate600)
                        Spacer(Modifier.height(4.dp))
                        SettingsActionRow("Export as JSON",  Icons.Rounded.DataObject,  Emerald500) { vm.exportJson() }
                        SettingsActionRow("Export as Excel", Icons.Rounded.TableChart,  Cyan500)    { vm.exportExcel() }
                        Divider()
                        SettingsActionRow("Restore from JSON", Icons.Rounded.RestorePage, Amber500) {
                            showRestoreConfirm = true
                        }
                    }
                }

                // ── About ──────────────────────────────────────────────────
                item {
                    SettingsSection("About", Icons.Rounded.Info, Slate600) {
                        SettingsInfoRow("Version", "1.0.0")
                        SettingsInfoRow("Database", "SQLite via Room")
                        SettingsInfoRow("Theme", "Material 3 + Cormorant Garamond")
                    }
                }

                // ── Danger zone ────────────────────────────────────────────
                item {
                    SettingsSection("Account", Icons.Rounded.ExitToApp, Rose500) {
                        SettingsActionRow("Logout", Icons.Rounded.Logout, Rose500) {
                            showLogoutConfirm = true
                        }
                    }
                }
            }

            LoadingOverlay(isLoading = isLoading)
        }
    }

    // PIN change dialog
    if (showPinDialog) {
        ChangePinDialog(
            onConfirm = { old, new_, confirm -> vm.changePin(old, new_, confirm) },
            onDismiss = { showPinDialog = false }
        )
    }

    // Restore confirm
    if (showRestoreConfirm) {
        ConfirmDialog(
            title = "Restore Database",
            message = "This will OVERWRITE all current data with the backup file. This cannot be undone.",
            confirmLabel = "Choose File",
            confirmColor = Amber500,
            onConfirm = {
                showRestoreConfirm = false
                restoreLauncher.launch(arrayOf("application/json"))
            },
            onDismiss = { showRestoreConfirm = false }
        )
    }

    // Logout confirm
    if (showLogoutConfirm) {
        ConfirmDialog(
            title = "Logout",
            message = "You will need your PIN or biometric to log back in.",
            confirmLabel = "Logout",
            confirmColor = Rose500,
            onConfirm = { vm.logout() },
            onDismiss = { showLogoutConfirm = false }
        )
    }
}

// Helper: share file from event (inject the right exporter based on file extension)
private fun jsonBackup_shareFile(context: Context, file: java.io.File) {
    val mimeType = if (file.extension == "json") "application/json"
                   else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}
```

### Settings helper composables (add at bottom of `SettingsScreen.kt`):
```kotlin
@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    color: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(shape = RoundedCornerShape(12.dp),
         colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
         elevation = CardDefaults.cardElevation(1.dp)) {
        Column {
            // Section header bar
            Row(
                Modifier.fillMaxWidth()
                    .background(color.copy(alpha = 0.08f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = color)
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(label: String, checked: Boolean,
                               icon: ImageVector, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, modifier = Modifier.size(18.dp), tint = Slate600)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun SettingsActionRow(label: String, icon: ImageVector,
                               color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp), tint = color)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = color,
             modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.ChevronRight, null, tint = Slate400, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Slate600)
        Text(value,  style = MaterialTheme.typography.bodySmall, color = Slate400)
    }
}

@Composable
private fun ChangePinDialog(onConfirm: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var oldPin     by remember { mutableStateOf("") }
    var newPin     by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(oldPin,     { oldPin = it },     label = { Text("Current PIN") },
                                  visualTransformation = PasswordVisualTransformation(),
                                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                  modifier = Modifier.fillMaxWidth())
                OutlinedTextField(newPin,     { newPin = it },     label = { Text("New PIN") },
                                  visualTransformation = PasswordVisualTransformation(),
                                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                  modifier = Modifier.fillMaxWidth())
                OutlinedTextField(confirmPin, { confirmPin = it }, label = { Text("Confirm New PIN") },
                                  visualTransformation = PasswordVisualTransformation(),
                                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                  modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(oldPin, newPin, confirmPin) },
                   enabled = oldPin.isNotBlank() && newPin.isNotBlank() && confirmPin.isNotBlank()) {
                Text("Update PIN")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
```

---

## TASK 7 — NOTIFICATION WORKERS

### File: `worker/FollowupReminderWorker.kt`

```kotlin
@HiltWorker
class FollowupReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val visitRepo: VisitRepository,
    private val patientRepo: PatientRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val now       = Clock.System.now()
        val in48Hours = now.plus(48, DateTimeUnit.HOUR).toEpochMilliseconds()
        val fromMs    = now.toEpochMilliseconds()

        val upcomingVisits = visitRepo.getFollowupsBetweenOnce(fromMs, in48Hours)
        if (upcomingVisits.isEmpty()) return Result.success()

        val notificationManager = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as android.app.NotificationManager

        // Create channel
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID, "Followup Reminders",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Reminds about upcoming patient followups" }
            notificationManager.createNotificationChannel(channel)
        }

        upcomingVisits.forEach { visit ->
            val patient = patientRepo.getPatientByIdOnce(visit.patientId) ?: return@forEach
            val daysUntil = ((visit.followupDate!!.toEpochMilliseconds() - fromMs) / 86_400_000L)
            val whenText  = if (daysUntil == 0L) "today" else "in $daysUntil day(s)"

            val deepLinkIntent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("swarapulse://patients/${patient.id}"),
                applicationContext,
                MainActivity::class.java
            )
            val pendingIntent = android.app.PendingIntent.getActivity(
                applicationContext, visit.id.toInt(), deepLinkIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Followup Due: ${patient.name}")
                .setContentText("Scheduled $whenText")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(visit.id.toInt(), notification)
        }

        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "followup_reminders"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<FollowupReminderWorker>(1, java.util.concurrent.TimeUnit.DAYS)
                .setInitialDelay(calculateDelayUntil7AM(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "followup_reminder", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        private fun calculateDelayUntil7AM(): Long {
            val cal = java.util.Calendar.getInstance()
            val now = cal.timeInMillis
            cal.set(java.util.Calendar.HOUR_OF_DAY, 7)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            if (cal.timeInMillis <= now) cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            return cal.timeInMillis - now
        }
    }
}
```

### UPDATE: `data/repository/VisitRepository.kt`

Add:
```kotlin
suspend fun getFollowupsBetweenOnce(from: Long, to: Long): List<Visit> =
    withContext(ioDispatcher) { dao.getFollowupsBetweenOnce(from, to) }
```

### UPDATE: `data/db/dao/VisitDao.kt`

Add:
```kotlin
@Query("SELECT * FROM visits WHERE followupDate BETWEEN :from AND :to")
suspend fun getFollowupsBetweenOnce(from: Long, to: Long): List<Visit>
```

### UPDATE: `SwaraPulseApp.kt`

```kotlin
@HiltAndroidApp
class SwaraPulseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Schedule daily followup reminder worker
        FollowupReminderWorker.schedule(this)
    }
}
```

---

## TASK 8 — UPDATE NAVHOST FOR NEW SCREENS
### UPDATE: `presentation/navigation/NavHost.kt`

Add:
```kotlin
composable("analytics") {
    AnalyticsScreen(navController = navController)
}

composable("settings") {
    SettingsScreen(navController = navController)
}
```

---

## COMPLETION CHECKLIST

- [ ] Apache POI `packaging { resources { excludes += ... } }` block added to `build.gradle.kts`
- [ ] `AnalyticsEngine` is `@Singleton` and injected into `AnalyticsViewModel` via Hilt
- [ ] `CartesianChartModelProducer` and Vico imports resolve — use `com.patrykandpatrick.vico.compose.cartesian.*`
- [ ] `VisitTimelineChart` `LaunchedEffect` key is `timeline` so chart re-renders on time range change
- [ ] `ExcelExporter` uses `IndexedColors.INDIGO` — if that constant doesn't exist in POI 5.x, use a custom `XSSFColor` with RGB `(79, 70, 229)` instead
- [ ] `JsonBackupManager.exportToJson()` uses `flow.first()` correctly — import `kotlinx.coroutines.flow.first`
- [ ] `DatabaseBackup` and all nested types are `@Serializable` — `Patient`, `Visit`, `Appointment` must also be annotated (add `@Serializable` to each entity if not already done)
- [ ] `FollowupReminderWorker` annotated with `@HiltWorker` and `@AssistedInject` — add `HiltWorkerFactory` to `SwaraPulseApp` via `Configuration.Provider`
- [ ] `HiltWorkerFactory` wired in `SwaraPulseApp`:
  ```kotlin
  @HiltAndroidApp
  class SwaraPulseApp : Application(), Configuration.Provider {
      @Inject lateinit var workerFactory: HiltWorkerFactory
      override val workManagerConfiguration get() =
          Configuration.Builder().setWorkerFactory(workerFactory).build()
  }
  ```
- [ ] `AndroidManifest.xml` has `android:name=".SwaraPulseApp"` and WorkManager `tools:node="remove"` for default initializer:
  ```xml
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
  ```
- [ ] All new screens (`AnalyticsScreen`, `SettingsScreen`) added to `NavHost`
- [ ] `SettingsScreen` event handler for `ShareFile` calls `jsonBackup_shareFile(context, event.file)` which is defined in the same file
- [ ] `VisitRecordsTable` nested `LazyColumn` inside outer `LazyColumn` — wrap the inner one in a fixed `height(400.dp)` to avoid infinite constraints crash
