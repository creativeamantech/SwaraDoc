package com.swarapulse.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swarapulse.data.db.entity.Appointment
import com.swarapulse.data.db.entity.Visit
import com.swarapulse.data.repository.AppointmentRepository
import com.swarapulse.data.repository.PatientRepository
import com.swarapulse.data.repository.VisitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val patientCount: Int = 0,
    val visitCount: Int = 0,
    val monthVisits: Int = 0,
    val upcomingFollowups: List<Visit> = emptyList(),
    val todaysAppointments: List<Appointment> = emptyList(),
    val recentVisits: List<Visit> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    private val visitRepository: VisitRepository,
    private val appointmentRepository: AppointmentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
    }

    private fun loadDashboardData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            launch {
                patientRepository.getAllPatients().collect { patients ->
                    _uiState.update { it.copy(patientCount = patients.size) }
                }
            }

            launch {
                visitRepository.getVisitCount().collect { count ->
                    _uiState.update { it.copy(visitCount = count) }
                }
            }

            launch {
                visitRepository.getRecentVisits().collect { visits ->
                    _uiState.update { it.copy(recentVisits = visits) }
                }
            }

            launch {
                appointmentRepository.getAllAppointments().collect { appointments ->
                    _uiState.update { it.copy(todaysAppointments = appointments) } // Needs proper date filtering
                }
            }

            // upcomingFollowups and monthVisits logic to be added

            _uiState.update { it.copy(isLoading = false) }
        }
    }
}
