package com.swarapulse.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swarapulse.data.datastore.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject

enum class AuthState { INITIAL, SETUP_PIN, CONFIRM_PIN, ENTER_PIN, SUCCESS }

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _authState = MutableStateFlow(AuthState.INITIAL)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _enteredPin = MutableStateFlow("")
    val enteredPin: StateFlow<String> = _enteredPin.asStateFlow()

    private var setupPinTemp = ""

    init {
        checkPinStatus()
    }

    private fun checkPinStatus() {
        viewModelScope.launch {
            val pinHash = settingsDataStore.pinHashFlow.first()
            if (pinHash == null) {
                _authState.value = AuthState.SETUP_PIN
            } else {
                _authState.value = AuthState.ENTER_PIN
            }
        }
    }

    fun enterDigit(digit: Int) {
        val currentPin = _enteredPin.value
        if (currentPin.length < 6) {
            _enteredPin.value = currentPin + digit
            if (_enteredPin.value.length == 6) {
                processCompletePin()
            }
        }
    }

    fun deleteDigit() {
        val currentPin = _enteredPin.value
        if (currentPin.isNotEmpty()) {
            _enteredPin.value = currentPin.dropLast(1)
        }
    }

    private fun processCompletePin() {
        val pin = _enteredPin.value
        viewModelScope.launch {
            when (_authState.value) {
                AuthState.SETUP_PIN -> {
                    setupPinTemp = pin
                    _enteredPin.value = ""
                    _authState.value = AuthState.CONFIRM_PIN
                }
                AuthState.CONFIRM_PIN -> {
                    if (pin == setupPinTemp) {
                        settingsDataStore.savePin(pin)
                        _authState.value = AuthState.SUCCESS
                    } else {
                        // Handle mismatch error
                        _enteredPin.value = ""
                        _authState.value = AuthState.SETUP_PIN
                    }
                }
                AuthState.ENTER_PIN -> {
                    val savedHash = settingsDataStore.pinHashFlow.first()
                    if (hashPin(pin) == savedHash) {
                        _authState.value = AuthState.SUCCESS
                    } else {
                        // Handle incorrect PIN error
                        _enteredPin.value = ""
                    }
                }
                else -> {}
            }
        }
    }

    private fun hashPin(pin: String): String {
        val bytes = pin.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    fun onBiometricSuccess() {
        _authState.value = AuthState.SUCCESS
    }
}
