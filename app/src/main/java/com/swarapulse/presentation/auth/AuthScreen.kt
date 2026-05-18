package com.swarapulse.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun AuthScreen(
    onNavigateToDashboard: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsState()
    val enteredPin by viewModel.enteredPin.collectAsState()

    LaunchedEffect(authState) {
        if (authState == AuthState.SUCCESS) {
            onNavigateToDashboard()
        }
    }

    if (authState == AuthState.INITIAL) {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (authState != AuthState.SUCCESS) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val titleText = when (authState) {
                AuthState.SETUP_PIN -> "Setup PIN"
                AuthState.CONFIRM_PIN -> "Confirm PIN"
                AuthState.ENTER_PIN -> "Enter PIN"
                else -> ""
            }

            Text(
                text = titleText,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(32.dp))

            PinDots(pinLength = enteredPin.length)

            Spacer(modifier = Modifier.height(48.dp))

            PinKeypad(
                onDigitClick = { viewModel.enterDigit(it) },
                onDeleteClick = { viewModel.deleteDigit() }
            )
        }
    }
}

@Composable
fun PinDots(pinLength: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until 6) {
            val color = if (i < pinLength) MaterialTheme.colorScheme.primary else Color.LightGray
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
fun PinKeypad(
    onDigitClick: (Int) -> Unit,
    onDeleteClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        for (row in 0 until 3) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                for (col in 0 until 3) {
                    val digit = row * 3 + col + 1
                    KeypadButton(text = digit.toString()) {
                        onDigitClick(digit)
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Box(modifier = Modifier.size(64.dp)) // Spacer
            KeypadButton(text = "0") {
                onDigitClick(0)
            }
            KeypadButton(text = "DEL") {
                onDeleteClick()
            }
        }
    }
}

@Composable
fun KeypadButton(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Text(
            text = text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
