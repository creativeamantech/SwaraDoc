package com.swarapulse.presentation.visit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swarapulse.data.db.entity.DraftVisit
import com.swarapulse.data.repository.DraftVisitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Serializable
data class VisitFormState(
    val patientId: Long? = null,
    val complaint: String = "",
    val prescription: String = ""
    // Add other fields from the 7 steps
)

@HiltViewModel
class VisitViewModel @Inject constructor(
    private val draftVisitRepository: DraftVisitRepository
) : ViewModel() {

    private val _formState = MutableStateFlow(VisitFormState())
    val formState: StateFlow<VisitFormState> = _formState.asStateFlow()

    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private var autoSaveJob: Job? = null

    init {
        loadDraft()
        startAutoSave()
    }

    private fun loadDraft() {
        viewModelScope.launch {
            draftVisitRepository.getDraftVisit().collect { draft ->
                draft?.let {
                    try {
                        val state = Json.decodeFromString<VisitFormState>(it.draftDataJson)
                        _formState.value = state
                    } catch (e: Exception) {
                        // Handle invalid json
                    }
                }
            }
        }
    }

    private fun startAutoSave() {
        autoSaveJob = viewModelScope.launch {
            while (isActive) {
                delay(30000) // 30 seconds
                saveDraft()
            }
        }
    }

    private suspend fun saveDraft() {
        val state = _formState.value
        val json = Json.encodeToString(state)
        draftVisitRepository.saveDraft(
            DraftVisit(
                patientId = state.patientId,
                draftDataJson = json
            )
        )
    }

    fun updateFormState(update: (VisitFormState) -> VisitFormState) {
        _formState.value = update(_formState.value)
    }

    fun nextStep() {
        if (validateCurrentStep()) {
            _currentStep.value = minOf(_currentStep.value + 1, 6)
        }
    }

    fun previousStep() {
        _currentStep.value = maxOf(_currentStep.value - 1, 0)
    }

    private fun validateCurrentStep(): Boolean {
        // Implement validation logic per step
        return true
    }

    fun submitForm() {
        viewModelScope.launch {
            // Validate all
            // Save Visit
            // clearDrafts
            draftVisitRepository.clearDrafts()
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoSaveJob?.cancel()
    }
}
