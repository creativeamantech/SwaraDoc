package com.swarapulse.presentation.visit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VisitFormScreen(
    onNavigateBack: () -> Unit,
    viewModel: VisitViewModel = hiltViewModel()
) {
    val currentStep by viewModel.currentStep.collectAsState()
    val formState by viewModel.formState.collectAsState()

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { 7 }
    )
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(currentStep) {
        if (pagerState.currentPage != currentStep) {
            pagerState.animateScrollToPage(currentStep)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (currentStep != pagerState.currentPage) {
             // In real app we might want to validate before allowing swipe
             // For now we just sync state
             // viewModel.setStep(pagerState.currentPage)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Visit") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = { viewModel.previousStep() },
                        enabled = currentStep > 0
                    ) {
                        Text("Back")
                    }
                    Button(
                        onClick = {
                            if (currentStep < 6) {
                                viewModel.nextStep()
                            } else {
                                viewModel.submitForm()
                                onNavigateBack()
                            }
                        }
                    ) {
                        Text(if (currentStep < 6) "Next" else "Submit")
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LinearProgressIndicator(
                progress = { (currentStep + 1) / 7f },
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = false // Disable swiping to force using Next/Back for validation
            ) { page ->
                when (page) {
                    0 -> Step1PatientInfo()
                    1 -> Step2MedicalHistory()
                    2 -> Step3YogicEval()
                    3 -> Step4DoctorAssessment()
                    4 -> Step5ClinicalNotes()
                    5 -> Step6MediaCustom()
                    6 -> Step7Timing()
                }
            }
        }
    }
}

@Composable
fun Step1PatientInfo() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text("Step 1: Patient Info")
    }
}

@Composable
fun Step2MedicalHistory() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text("Step 2: Medical History")
    }
}

@Composable
fun Step3YogicEval() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text("Step 3: Yogic Evaluation")
    }
}

@Composable
fun Step4DoctorAssessment() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text("Step 4: Doctor Assessment")
    }
}

@Composable
fun Step5ClinicalNotes() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text("Step 5: Clinical Notes")
    }
}

@Composable
fun Step6MediaCustom() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text("Step 6: Media & Custom Fields")
    }
}

@Composable
fun Step7Timing() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text("Step 7: Timing & Followup")
    }
}
