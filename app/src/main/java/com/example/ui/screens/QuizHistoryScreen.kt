package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.data.QuizEntity
import com.example.ui.QuizViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizHistoryScreen(
    viewModel: QuizViewModel,
    onOpenQuiz: (Long) -> Unit
) {
    val allQuizzes by viewModel.allQuizzes.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val filteredQuizzes = remember(allQuizzes, searchQuery) {
        if (searchQuery.isBlank()) allQuizzes else {
            allQuizzes.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.sourceFileName?.contains(searchQuery, ignoreCase = true) == true ||
                it.keyTopicsJson.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val completedCount = allQuizzes.count { it.isCompleted }
    val avgScore = if (allQuizzes.isNotEmpty()) {
        allQuizzes.mapNotNull { it.lastScore }.let { if (it.isNotEmpty()) it.average().toFloat() else 0f }
    } else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary Header Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text("Total Quizzes", style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = "${allQuizzes.size}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text("Avg Score", style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = "${avgScore.toInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text("Completed", style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = "$completedCount",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search quiz history by title or topic...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, null)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        if (filteredQuizzes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HistoryEdu,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = if (searchQuery.isBlank()) "No quiz history yet. Scan a document to create your first quiz!" else "No matching quizzes found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredQuizzes, key = { it.id }) { quiz ->
                    QuizHistoryCard(
                        quiz = quiz,
                        onResume = {
                            viewModel.loadQuizForPlaying(quiz.id)
                            onOpenQuiz(quiz.id)
                        },
                        onRevise = {
                            viewModel.resetAndReviseQuiz(quiz.id)
                            onOpenQuiz(quiz.id)
                        },
                        onDelete = { viewModel.deleteQuiz(quiz.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun QuizHistoryCard(
    quiz: QuizEntity,
    onResume: () -> Unit,
    onRevise: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val formattedDate = remember(quiz.createdAt) { dateFormat.format(Date(quiz.createdAt)) }

    val statusColor = when {
        quiz.isCompleted -> Color(0xFF10B981)
        (quiz.lastScore ?: 0f) > 0 -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.outline
    }

    val statusLabel = when {
        quiz.isCompleted -> "Completed"
        (quiz.lastScore ?: 0f) > 0 -> "In Progress"
        else -> "New"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("quiz_history_item_${quiz.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (quiz.sourceType == "PDF") Icons.Default.PictureAsPdf else Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = quiz.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }

            Text(
                text = quiz.analysisSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Badge(containerColor = statusColor) {
                    Text(
                        text = statusLabel,
                        color = Color.White,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                AssistChip(
                    onClick = {},
                    label = { Text("${quiz.totalQuestions} Qs • ${quiz.scoringMode}", fontSize = 11.sp) }
                )

                Spacer(modifier = Modifier.weight(1f))

                if (quiz.lastScore != null) {
                    Text(
                        text = "${quiz.lastScore?.toInt()}%",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onRevise,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Revise / Retry", fontSize = 12.sp)
                }

                Button(
                    onClick = onResume,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (quiz.isCompleted) "Review" else "Resume Quiz", fontSize = 12.sp)
                }
            }
        }
    }
}
