package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.QuizViewModel
import com.example.ui.screens.DocumentLibraryScreen
import com.example.ui.screens.InteractiveQuizScreen
import com.example.ui.screens.OcrQuizCreatorScreen
import com.example.ui.screens.QuizHistoryScreen
import com.example.ui.theme.MyApplicationTheme

enum class NavigationTab(val label: String, val icon: ImageVector) {
    CREATE("Create Quiz", Icons.Default.AddCircleOutline),
    QUIZ("Active Quiz", Icons.Default.Quiz),
    HISTORY("History", Icons.Default.History),
    LIBRARY("OCR Library", Icons.Default.Folder)
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: QuizViewModel = viewModel()
                var selectedTab by remember { mutableStateOf(NavigationTab.CREATE) }

                val activeQuizWithQuestions by viewModel.activeQuizWithQuestions.collectAsState()
                val currentApiKey by viewModel.apiKey.collectAsState()

                var showApiKeyDialog by remember { mutableStateOf(false) }
                var apiKeyInput by remember { mutableStateOf("") }

                if (showApiKeyDialog) {
                    AlertDialog(
                        onDismissRequest = { showApiKeyDialog = false },
                        title = { Text("Gemini API Key Settings", fontWeight = FontWeight.Bold) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Enter your custom Gemini API Key. Leave blank to use the built-in system key.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                OutlinedTextField(
                                    value = apiKeyInput,
                                    onValueChange = { apiKeyInput = it },
                                    label = { Text("API Key") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("api_key_input_field"),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.updateApiKey(apiKeyInput)
                                    showApiKeyDialog = false
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("save_api_key_button")
                            ) {
                                Text("Save Key", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            OutlinedButton(
                                onClick = { showApiKeyDialog = false },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "Quizzy",
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = {
                                        apiKeyInput = currentApiKey
                                        showApiKeyDialog = true
                                    },
                                    modifier = Modifier.testTag("settings_api_key_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VpnKey,
                                        contentDescription = "API Key Settings",
                                        tint = if (currentApiKey.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier
                                .navigationBarsPadding()
                                .testTag("bottom_navigation_bar")
                        ) {
                            NavigationTab.values().forEach { tab ->
                                val isSelected = selectedTab == tab
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { selectedTab = tab },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag("nav_item_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (selectedTab) {
                            NavigationTab.CREATE -> {
                                OcrQuizCreatorScreen(
                                    viewModel = viewModel,
                                    onQuizCreated = { quizId ->
                                        selectedTab = NavigationTab.QUIZ
                                    }
                                )
                            }
                            NavigationTab.QUIZ -> {
                                if (activeQuizWithQuestions != null) {
                                    InteractiveQuizScreen(
                                        viewModel = viewModel,
                                        onFinishQuiz = {
                                            selectedTab = NavigationTab.HISTORY
                                        }
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = androidx.compose.ui.Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Quiz,
                                                contentDescription = null,
                                                modifier = Modifier.size(56.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "No active quiz selected",
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Button(
                                                onClick = { selectedTab = NavigationTab.CREATE },
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                            ) {
                                                Text("Create New Quiz from OCR")
                                            }
                                        }
                                    }
                                }
                            }
                            NavigationTab.HISTORY -> {
                                QuizHistoryScreen(
                                    viewModel = viewModel,
                                    onOpenQuiz = { quizId ->
                                        selectedTab = NavigationTab.QUIZ
                                    }
                                )
                            }
                            NavigationTab.LIBRARY -> {
                                DocumentLibraryScreen(
                                    viewModel = viewModel,
                                    onCreateQuizFromDocument = { docTitle ->
                                        selectedTab = NavigationTab.CREATE
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
