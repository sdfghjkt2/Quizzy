package com.example.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

data class DocumentOcrResult(
    val fileName: String,
    val sourceType: String, // "IMAGE", "PDF", "TEXT", "SAMPLE"
    val extractedText: String,
    val pageCount: Int = 1,
    val previewBitmapBase64: String? = null
)

data class DocumentAnalysis(
    val title: String,
    val summary: String,
    val keyTopics: List<String>,
    val extractedConcepts: List<ConceptDetail> = emptyList()
)

data class ConceptDetail(
    val topic: String,
    val description: String
)

enum class QuestionTypeMode(val label: String, val description: String) {
    BOTH("Both (Objective & Subjective)", "Mix of Multiple Choice and Short Answer questions"),
    OBJECTIVE("Objective Only", "Multiple choice questions with 4 choices"),
    SUBJECTIVE("Subjective Only", "Open-ended short answer questions requiring written explanations")
}

enum class ScoringMode(val label: String, val badge: String, val description: String) {
    DISCRETE(
        label = "Discrete (Strict)",
        badge = "Strict Grading",
        description = "Requires exact key terms & precise answers. No partial points for incomplete answers."
    ),
    FORGIVING(
        label = "Forgiving (Lenient & Partial Credit)",
        badge = "Partial Credit",
        description = "Gives partial points for partial understanding and ignores minor spelling or syntax variations."
    )
}

data class QuizGenerationConfig(
    val questionTypeMode: QuestionTypeMode = QuestionTypeMode.BOTH,
    val scoringMode: ScoringMode = ScoringMode.FORGIVING,
    val questionCount: Int = 5,
    val customFocusPrompt: String = ""
)

@JsonClass(generateAdapter = true)
data class GeneratedQuizResponse(
    @Json(name = "title") val title: String? = "Generated Document Quiz",
    @Json(name = "summary") val summary: String? = "",
    @Json(name = "keyTopics") val keyTopics: List<String>? = emptyList(),
    @Json(name = "questions") val questions: List<GeneratedQuestion>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class GeneratedQuestion(
    @Json(name = "questionType") val questionType: String = "OBJECTIVE", // "OBJECTIVE" or "SUBJECTIVE"
    @Json(name = "promptText") val promptText: String = "",
    @Json(name = "options") val options: List<String>? = null,
    @Json(name = "correctAnswer") val correctAnswer: String = "",
    @Json(name = "topicName") val topicName: String = "General Knowledge",
    @Json(name = "explanation") val explanation: String = ""
)

@JsonClass(generateAdapter = true)
data class AnswerEvaluationResult(
    @Json(name = "isCorrect") val isCorrect: Boolean = false,
    @Json(name = "scoreEarned") val scoreEarned: Float = 0f, // 0.0 to 1.0
    @Json(name = "whyWrong") val whyWrong: String = "",
    @Json(name = "correctAnswerExplanation") val correctAnswerExplanation: String = "",
    @Json(name = "exactTopic") val exactTopic: String = "",
    @Json(name = "learningTip") val learningTip: String = ""
)
