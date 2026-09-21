package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quizzes")
data class QuizEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val sourceType: String, // "IMAGE", "PDF", "TEXT", "SAMPLE"
    val sourceFileName: String? = null,
    val extractedText: String,
    val analysisSummary: String,
    val keyTopicsJson: String = "[]", // JSON array of topic strings
    val questionTypeMode: String, // "OBJECTIVE", "SUBJECTIVE", "BOTH"
    val scoringMode: String, // "DISCRETE", "FORGIVING"
    val totalQuestions: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAttemptAt: Long? = null,
    val bestScore: Float? = null,
    val lastScore: Float? = null,
    val isCompleted: Boolean = false,
    val currentQuestionIndex: Int = 0
)
