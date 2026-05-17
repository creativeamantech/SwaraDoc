package com.swarapulse.presentation.appointments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swarapulse.data.db.entity.Appointment
import com.swarapulse.data.repository.AppointmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

data class AppointmentsUiState(
    val appointments: List<Appointment> = emptyList(),
    val todayAppointments: List<Appointment> = emptyList(),
    val upcomingAppointments: List<Appointment> = emptyList(),
    val pastAppointments: List<Appointment> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class AppointmentsViewModel @Inject constructor(
    private val appointmentRepository: AppointmentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppointmentsUiState())
    val uiState: StateFlow<AppointmentsUiState> = _uiState.asStateFlow()

    init {
        loadAppointments()
    }

    private fun loadAppointments() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            appointmentRepository.getAllAppointments().collect { allApps ->
                val now = Clock.System.now()
                val todayStart = now.toLocalDateTime(TimeZone.currentSystemDefault()).date

                val today = mutableListOf<Appointment>()
                val upcoming = mutableListOf<Appointment>()
                val past = mutableListOf<Appointment>()

                for (app in allApps) {
                    val appDate = app.dateTime.toLocalDateTime(TimeZone.currentSystemDefault()).date
                    if (appDate < todayStart) {
                        past.add(app)
                    } else if (appDate == todayStart) {
                        today.add(app)
                    } else {
                        upcoming.add(app)
                    }
                }

                _uiState.value = AppointmentsUiState(
                    appointments = allApps,
                    todayAppointments = today,
                    upcomingAppointments = upcoming,
                    pastAppointments = past,
                    isLoading = false
                )
            }
        }
    }
}
