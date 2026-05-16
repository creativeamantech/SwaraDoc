package com.swarapulse.presentation.patients

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swarapulse.data.db.entity.Patient
import com.swarapulse.data.db.entity.Visit
import com.swarapulse.data.repository.PatientRepository
import com.swarapulse.data.repository.VisitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PatientDetailUiState(
    val patient: Patient? = null,
    val visits: List<Visit> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class PatientDetailViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    private val visitRepository: VisitRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val patientId: Long = savedStateHandle.get<Long>("patientId") ?: -1L

    private val _uiState = MutableStateFlow(PatientDetailUiState())
    val uiState: StateFlow<PatientDetailUiState> = _uiState.asStateFlow()

    init {
        if (patientId != -1L) {
            loadPatientDetails()
        }
    }

    private fun loadPatientDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            combine(
                patientRepository.getPatientById(patientId),
                visitRepository.getVisitsForPatient(patientId)
            ) { patient, visits ->
                PatientDetailUiState(
                    patient = patient,
                    visits = visits,
                    isLoading = false
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }
}
