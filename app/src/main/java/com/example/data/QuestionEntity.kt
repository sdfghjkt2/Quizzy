package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = QuizEntity::class,
            parentColumns = ["id"],
            childColumns = ["quizId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("quizId")]
)
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val quizId: Long,
    val questionIndex: Int,
    val questionType: String, // "OBJECTIVE" or "SUBJECTIVE"
    val promptText: String,
    val optionsJson: String? = null, // JSON array string for objective choices e.g. ["A", "B", "C", "D"]
    val correctAnswer: String,
    val topicName: String,
    val explanation: String,
    val userAnswer: String? = null,
    val isAnswered: Boolean = false,
    val isCorrect: Boolean? = null,
    val scoreEarned: Float? = null, // e.g. 0.0 to 1.0
    val feedbackMessage: String? = null // AI mistake explanation & learning breakdown
)
