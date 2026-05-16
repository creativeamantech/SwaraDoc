package com.swarapulse.presentation.patients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swarapulse.data.db.entity.Patient
import com.swarapulse.data.repository.PatientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PatientListUiState(
    val patients: List<Patient> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val isGridView: Boolean = false
)

@HiltViewModel
class PatientListViewModel @Inject constructor(
    private val patientRepository: PatientRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _isGridView = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<PatientListUiState> = combine(
        _searchQuery.flatMapLatest { query ->
            if (query.isBlank()) {
                patientRepository.getAllPatients()
            } else {
                patientRepository.searchPatients(query)
            }
        },
        _searchQuery,
        _isGridView
    ) { patients, query, isGrid ->
        PatientListUiState(
            patients = patients,
            isLoading = false,
            searchQuery = query,
            isGridView = isGrid
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PatientListUiState()
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun toggleViewMode() {
        _isGridView.value = !_isGridView.value
    }

    fun deletePatient(patient: Patient) {
        viewModelScope.launch {
            patientRepository.deletePatient(patient)
        }
    }
}
