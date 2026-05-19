package com.swarapulse.presentation.patients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swarapulse.data.db.entity.Patient
import com.swarapulse.data.repository.PatientRepository
import com.swarapulse.domain.model.Gender
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddPatientUiState(
    val name: String = "",
    val age: String = "",
    val gender: Gender = Gender.MALE,
    val mobile: String = "",
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class AddPatientViewModel @Inject constructor(
    private val patientRepository: PatientRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddPatientUiState())
    val uiState: StateFlow<AddPatientUiState> = _uiState.asStateFlow()

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun updateAge(age: String) {
        if (age.isEmpty() || age.all { it.isDigit() }) {
            _uiState.value = _uiState.value.copy(age = age)
        }
    }

    fun updateGender(gender: Gender) {
        _uiState.value = _uiState.value.copy(gender = gender)
    }

    fun updateMobile(mobile: String) {
        _uiState.value = _uiState.value.copy(mobile = mobile)
    }

    fun savePatient() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Name cannot be empty")
            return
        }
        val ageInt = state.age.toIntOrNull()
        if (ageInt == null || ageInt <= 0) {
            _uiState.value = state.copy(errorMessage = "Invalid age")
            return
        }
        if (state.mobile.isBlank()) {
             _uiState.value = state.copy(errorMessage = "Mobile number cannot be empty")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            try {
                val patient = Patient(
                    name = state.name,
                    age = ageInt,
                    gender = state.gender,
                    mobile = state.mobile
                )
                patientRepository.insertPatient(patient)
                _uiState.value = _uiState.value.copy(isSaving = false, saveSuccess = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = e.message ?: "Failed to save patient"
                )
            }
        }
    }
}
