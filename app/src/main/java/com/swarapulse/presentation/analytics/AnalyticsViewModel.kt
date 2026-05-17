package com.swarapulse.presentation.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swarapulse.data.db.entity.Visit
import com.swarapulse.data.repository.VisitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import javax.inject.Inject

enum class TimeFilter { D30, D90, M6, Y1, ALL }

data class AnalyticsUiState(
    val selectedFilter: TimeFilter = TimeFilter.D30,
    val visits: List<Visit> = emptyList(),
    val nadiAlignmentPercentage: Float = 0f,
    val elementAlignmentPercentage: Float = 0f,
    val topComplaints: List<Pair<String, Int>> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val visitRepository: VisitRepository
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow(TimeFilter.D30)

    // In a real app we might fetch all and filter in memory, or query DB with dates.
    // Here we will fetch recent for simplicity of the scaffold.
    private val _allVisits = MutableStateFlow<List<Visit>>(emptyList())

    val uiState: StateFlow<AnalyticsUiState> = combine(
        _selectedFilter, _allVisits
    ) { filter, allVisits ->
        val filteredVisits = filterVisits(allVisits, filter)
        val (nadiAlign, elemAlign) = computeCorrelations(filteredVisits)
        val complaints = extractTopComplaints(filteredVisits)

        AnalyticsUiState(
            selectedFilter = filter,
            visits = filteredVisits,
            nadiAlignmentPercentage = nadiAlign,
            elementAlignmentPercentage = elemAlign,
            topComplaints = complaints,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AnalyticsUiState()
    )

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            // Usually we'd need a specific query for this or to get all
            visitRepository.getRecentVisits().collect { visits ->
                _allVisits.value = visits
            }
        }
    }

    fun setFilter(filter: TimeFilter) {
        _selectedFilter.value = filter
    }

    private fun filterVisits(visits: List<Visit>, filter: TimeFilter): List<Visit> {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        return when (filter) {
            TimeFilter.ALL -> visits
            else -> visits.filter {
                val visitDate = it.dateTime.toLocalDateTime(TimeZone.currentSystemDefault()).date
                val cutoffDate = when (filter) {
                    TimeFilter.D30 -> now.minus(30, DateTimeUnit.DAY)
                    TimeFilter.D90 -> now.minus(90, DateTimeUnit.DAY)
                    TimeFilter.M6 -> now.minus(6, DateTimeUnit.MONTH)
                    TimeFilter.Y1 -> now.minus(1, DateTimeUnit.YEAR)
                    TimeFilter.ALL -> now // Unreachable due to above
                }
                visitDate >= cutoffDate
            }
        }
    }

    private fun computeCorrelations(visits: List<Visit>): Pair<Float, Float> {
        if (visits.isEmpty()) return 0f to 0f

        var nadiMatches = 0
        var elementMatches = 0

        visits.forEach {
            if (it.patientNadi.name == it.doctorNadi) nadiMatches++
            if (it.patientElement.name == it.doctorElementBefore) elementMatches++
        }

        return Pair(
            (nadiMatches.toFloat() / visits.size) * 100,
            (elementMatches.toFloat() / visits.size) * 100
        )
    }

    private fun extractTopComplaints(visits: List<Visit>): List<Pair<String, Int>> {
        val wordCounts = mutableMapOf<String, Int>()

        visits.forEach { visit ->
            // In real app we parse HTML, here we just split by space for scaffold
            val words = visit.chiefComplaint.split("\\s+".toRegex()).filter { it.isNotBlank() }
            words.forEach { word ->
                val cleanWord = word.lowercase().trim('.', ',', '!', '?')
                if (cleanWord.length > 3) {
                    wordCounts[cleanWord] = wordCounts.getOrDefault(cleanWord, 0) + 1
                }
            }
        }

        return wordCounts.entries.sortedByDescending { it.value }.take(10).map { it.toPair() }
    }
}
