package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.QuestionEntity
import com.example.model.ScoringMode
import com.example.ui.QuizViewModel
import com.example.ui.components.MistakeExplanationDialog
import com.squareup.moshi.Moshi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveQuizScreen(
    viewModel: QuizViewModel,
    onFinishQuiz: () -> Unit
) {
    val quizWithQuestions by viewModel.activeQuizWithQuestions.collectAsState()
    val currentIndex by viewModel.currentQuestionIndex.collectAsState()
    val isEvaluating by viewModel.evaluatingAnswer.collectAsState()
    val mistakeResult by viewModel.mistakeEvaluationResult.collectAsState()

    val quizData = quizWithQuestions?.quiz
    val questions = quizWithQuestions?.questions ?: emptyList()

    if (quizData == null || questions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator()
                Text("Loading Interactive Quiz...")
            }
        }
        return
    }

    val isQuizFinished = quizData.isCompleted || (questions.all { it.isAnswered })

    // Active question
    val currentQuestion = questions.getOrNull(currentIndex)
    var selectedOption by remember(currentIndex) { mutableStateOf<String?>(currentQuestion?.userAnswer) }
    var subjectiveAnswerInput by remember(currentIndex) { mutableStateOf(currentQuestion?.userAnswer ?: "") }

    val scoringMode = try {
        ScoringMode.valueOf(quizData.scoringMode)
    } catch (e: Exception) {
        ScoringMode.FORGIVING
    }

    // Mistake Explanation Popup Dialog Trigger
    mistakeResult?.let { result ->
        MistakeExplanationDialog(
            result = result,
            scoringModeLabel = scoringMode.label,
            onDismiss = {
                viewModel.dismissMistakeDialog()
                if (currentIndex < questions.size - 1) {
                    viewModel.goToNextQuestion()
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = quizData.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "Question ${currentIndex + 1} of ${questions.size} • ${scoringMode.badge}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onFinishQuiz) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = "${(quizData.lastScore ?: 0f).toInt()}% Score",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Progress Bar
            val progress = (currentIndex + 1).toFloat() / questions.size
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )

            if (isQuizFinished && currentIndex == questions.size - 1 && currentQuestion?.isAnswered == true) {
                // Final Results Summary Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(56.dp)
                        )

                        Text(
                            text = "Quiz Completed!",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        val finalScore = quizData.lastScore ?: 0f
                        Text(
                            text = "${finalScore.toInt()}% Final Score",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = "Graded using ${scoringMode.label}. Your scores and mistake insights are saved in quiz history.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.resetAndReviseQuiz(quizData.id) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("retake_quiz_button")
                            ) {
                                Icon(Icons.Default.Refresh, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Retake / Revise")
                            }

                            Button(
                                onClick = onFinishQuiz,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("finish_quiz_button")
                            ) {
                                Text("Done")
                            }
                        }
                    }
                }
            }

            if (currentQuestion != null) {
                // Question Header Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = true,
                                onClick = {},
                                label = { Text(currentQuestion.topicName, fontSize = 11.sp) },
                                leadingIcon = { Icon(Icons.Default.Topic, null, Modifier.size(14.dp)) }
                            )

                            Badge(
                                containerColor = if (currentQuestion.questionType == "OBJECTIVE") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
                            ) {
                                Text(
                                    text = currentQuestion.questionType,
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = currentQuestion.promptText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Interactive Answer Area: Objective vs Subjective
                if (currentQuestion.questionType == "OBJECTIVE") {
                    val options = parseOptionsJson(currentQuestion.optionsJson)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        options.forEachIndexed { optIdx, optionText ->
                            val isSelected = selectedOption == optionText
                            val isCorrectAnswer = currentQuestion.isAnswered && optionText == currentQuestion.correctAnswer
                            val isUserWrongSelection = currentQuestion.isAnswered && isSelected && !isCorrectAnswer

                            val optionBorderColor = when {
                                isCorrectAnswer -> Color(0xFF10B981)
                                isUserWrongSelection -> Color(0xFFEF4444)
                                isSelected -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.outlineVariant
                            }

                            val optionBgColor = when {
                                isCorrectAnswer -> Color(0xFF10B981).copy(alpha = 0.15f)
                                isUserWrongSelection -> Color(0xFFEF4444).copy(alpha = 0.15f)
                                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else -> MaterialTheme.colorScheme.surface
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = !currentQuestion.isAnswered) {
                                        selectedOption = optionText
                                    }
                                    .border(2.dp, optionBorderColor, RoundedCornerShape(12.dp))
                                    .testTag("mcq_option_$optIdx"),
                                colors = CardDefaults.cardColors(containerColor = optionBgColor)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { if (!currentQuestion.isAnswered) selectedOption = optionText },
                                        enabled = !currentQuestion.isAnswered
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = optionText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (currentQuestion.isAnswered) {
                                        if (isCorrectAnswer) {
                                            Icon(Icons.Default.Check, null, tint = Color(0xFF10B981))
                                        } else if (isUserWrongSelection) {
                                            Icon(Icons.Default.Close, null, tint = Color(0xFFEF4444))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Submit Objective Answer Button
                    if (!currentQuestion.isAnswered) {
                        Button(
                            onClick = {
                                selectedOption?.let {
                                    viewModel.submitAnswer(currentQuestion, it)
                                }
                            },
                            enabled = selectedOption != null && !isEvaluating,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("submit_mcq_answer_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isEvaluating) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Grading Answer...")
                            } else {
                                Icon(Icons.Default.Send, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Submit Objective Answer", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                } else {
                    // Subjective Question Input
                    OutlinedTextField(
                        value = subjectiveAnswerInput,
                        onValueChange = { subjectiveAnswerInput = it },
                        enabled = !currentQuestion.isAnswered,
                        label = { Text("Your Written Explanation / Short Answer") },
                        placeholder = { Text("Write your detailed response here...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (!currentQuestion.isAnswered) {
                        Button(
                            onClick = {
                                if (subjectiveAnswerInput.isNotBlank()) {
                                    viewModel.submitAnswer(currentQuestion, subjectiveAnswerInput)
                                }
                            },
                            enabled = subjectiveAnswerInput.isNotBlank() && !isEvaluating,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("submit_subjective_answer_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isEvaluating) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Evaluating Written Response...")
                            } else {
                                Icon(Icons.Default.AutoAwesome, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Grade Written Answer with AI", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // If question was already answered, show previous feedback card & button to view mistake popup
                if (currentQuestion.isAnswered) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (currentQuestion.isCorrect == true) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFFEF4444).copy(alpha = 0.12f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (currentQuestion.isCorrect == true) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                    contentDescription = null,
                                    tint = if (currentQuestion.isCorrect == true) Color(0xFF10B981) else Color(0xFFEF4444)
                                )
                                Text(
                                    text = if (currentQuestion.isCorrect == true) "Correct Answer!" else "Incorrect / Needs Revision",
                                    fontWeight = FontWeight.Bold,
                                    color = if (currentQuestion.isCorrect == true) Color(0xFF10B981) else Color(0xFFEF4444)
                                )
                            }
                            Text(
                                text = "Solution: ${currentQuestion.correctAnswer}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = currentQuestion.explanation,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // Question Navigation Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = { viewModel.goToPreviousQuestion() },
                        enabled = currentIndex > 0,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("prev_question_button")
                    ) {
                        Icon(Icons.Default.NavigateBefore, null)
                        Text("Previous")
                    }

                    Button(
                        onClick = { viewModel.goToNextQuestion() },
                        enabled = currentIndex < questions.size - 1,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("next_question_button")
                    ) {
                        Text("Next")
                        Icon(Icons.Default.NavigateNext, null)
                    }
                }
            }
        }
    }
}

private fun parseOptionsJson(jsonString: String?): List<String> {
    if (jsonString.isNullOrBlank()) return emptyList()
    return try {
        val adapter = Moshi.Builder().build().adapter(List::class.java)
        @Suppress("UNCHECKED_CAST")
        (adapter.fromJson(jsonString) as? List<String>) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}
