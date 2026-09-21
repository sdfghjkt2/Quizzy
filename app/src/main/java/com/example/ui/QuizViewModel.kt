package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.DocumentProcessor
import com.example.ai.GeminiApiService
import com.example.data.*
import com.example.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface OcrState {
    object Idle : OcrState
    data class Processing(val message: String) : OcrState
    data class Success(val ocrResult: DocumentOcrResult, val analysis: DocumentAnalysis) : OcrState
    data class Error(val message: String) : OcrState
}

sealed interface QuizGenState {
    object Idle : QuizGenState
    data class Generating(val progressMessage: String) : QuizGenState
    data class Success(val quizId: Long) : QuizGenState
    data class Error(val message: String) : QuizGenState
}

class QuizViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: QuizRepository
    private val prefs = application.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)

    val allQuizzes: StateFlow<List<QuizEntity>>
    val allQuizzesWithQuestions: StateFlow<List<QuizWithQuestions>>

    private val _apiKey = MutableStateFlow(prefs.getString("custom_api_key", "") ?: "")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    fun updateApiKey(newKey: String) {
        _apiKey.value = newKey
        prefs.edit().putString("custom_api_key", newKey).apply()
        GeminiApiService.setApiKey(newKey)
    }

    private val _ocrState = MutableStateFlow<OcrState>(OcrState.Idle)
    val ocrState: StateFlow<OcrState> = _ocrState.asStateFlow()

    private val _quizGenState = MutableStateFlow<QuizGenState>(QuizGenState.Idle)
    val quizGenState: StateFlow<QuizGenState> = _quizGenState.asStateFlow()

    // Active Quiz playing state
    private val _activeQuizWithQuestions = MutableStateFlow<QuizWithQuestions?>(null)
    val activeQuizWithQuestions: StateFlow<QuizWithQuestions?> = _activeQuizWithQuestions.asStateFlow()

    private val _currentQuestionIndex = MutableStateFlow(0)
    val currentQuestionIndex: StateFlow<Int> = _currentQuestionIndex.asStateFlow()

    private val _evaluatingAnswer = MutableStateFlow(false)
    val evaluatingAnswer: StateFlow<Boolean> = _evaluatingAnswer.asStateFlow()

    // Popup for "Learn From Mistakes"
    private val _mistakeEvaluationResult = MutableStateFlow<AnswerEvaluationResult?>(null)
    val mistakeEvaluationResult: StateFlow<AnswerEvaluationResult?> = _mistakeEvaluationResult.asStateFlow()

    init {
        GeminiApiService.setApiKey(_apiKey.value)
        val db = AppDatabase.getInstance(application)
        repository = QuizRepository(db.quizDao())

        allQuizzes = repository.allQuizzes
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        allQuizzesWithQuestions = repository.allQuizzesWithQuestions
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
        // Load default sample document on first start
        loadSampleDocument("BIO")
    }

    fun loadSampleDocument(sampleKey: String) {
        viewModelScope.launch {
            _ocrState.value = OcrState.Processing("Analyzing sample study document...")
            try {
                val sampleOcr = DocumentProcessor.getSampleDocumentText(sampleKey)
                val (ocrText, analysis) = GeminiApiService.performOcrAndAnalysis(
                    rawText = sampleOcr.extractedText,
                    imageBase64List = null,
                    customUserPrompt = null
                )
                _ocrState.value = OcrState.Success(sampleOcr.copy(extractedText = ocrText), analysis)
            } catch (e: Exception) {
                val sampleOcr = DocumentProcessor.getSampleDocumentText(sampleKey)
                _ocrState.value = OcrState.Success(
                    sampleOcr,
                    DocumentAnalysis(
                        title = sampleOcr.fileName.replace(".pdf", "").replace("_", " "),
                        summary = sampleOcr.extractedText.take(250) + "...",
                        keyTopics = listOf("Core Concepts", "Study Guide", "Key Terms")
                    )
                )
            }
        }
    }

    fun processSelectedUri(uri: Uri, isPdf: Boolean) {
        viewModelScope.launch {
            _ocrState.value = OcrState.Processing("Performing OCR & AI Information Extraction...")
            try {
                val context = getApplication<Application>().applicationContext
                if (isPdf) {
                    val pageBitmaps = DocumentProcessor.processPdfUri(context, uri)
                    val base64List = pageBitmaps.map { DocumentProcessor.bitmapToBase64(it) }
                    val (extractedText, analysis) = GeminiApiService.performOcrAndAnalysis(
                        rawText = null,
                        imageBase64List = base64List,
                        customUserPrompt = null
                    )
                    _ocrState.value = OcrState.Success(
                        DocumentOcrResult(
                            fileName = uri.lastPathSegment ?: "Scanned_Document.pdf",
                            sourceType = "PDF",
                            extractedText = extractedText,
                            pageCount = pageBitmaps.size
                        ),
                        analysis
                    )
                } else {
                    val (bitmap, base64) = DocumentProcessor.processImageUri(context, uri)
                    val (extractedText, analysis) = GeminiApiService.performOcrAndAnalysis(
                        rawText = null,
                        imageBase64List = listOf(base64),
                        customUserPrompt = null
                    )
                    _ocrState.value = OcrState.Success(
                        DocumentOcrResult(
                            fileName = uri.lastPathSegment ?: "Photo_Scan.png",
                            sourceType = "IMAGE",
                            extractedText = extractedText,
                            pageCount = 1
                        ),
                        analysis
                    )
                }
            } catch (e: Exception) {
                _ocrState.value = OcrState.Error("OCR Processing Failed: ${e.message}")
            }
        }
    }

    fun generateQuiz(config: QuizGenerationConfig) {
        val currentState = _ocrState.value
        if (currentState !is OcrState.Success) {
            _quizGenState.value = QuizGenState.Error("Please upload or select a document first")
            return
        }

        viewModelScope.launch {
            _quizGenState.value = QuizGenState.Generating("AI is authoring custom interactive questions...")
            try {
                val generated = GeminiApiService.generateQuizFromDocument(
                    extractedText = currentState.ocrResult.extractedText,
                    config = config
                )

                val quizEntity = QuizEntity(
                    title = generated.title ?: "Quiz - ${currentState.ocrResult.fileName}",
                    sourceType = currentState.ocrResult.sourceType,
                    sourceFileName = currentState.ocrResult.fileName,
                    extractedText = currentState.ocrResult.extractedText,
                    analysisSummary = generated.summary ?: currentState.analysis.summary,
                    keyTopicsJson = generated.keyTopics?.joinToString(",") ?: "",
                    questionTypeMode = config.questionTypeMode.name,
                    scoringMode = config.scoringMode.name,
                    totalQuestions = generated.questions?.size ?: 0
                )

                val questionsList = generated.questions?.mapIndexed { index, gq ->
                    QuestionEntity(
                        quizId = 0,
                        questionIndex = index,
                        questionType = gq.questionType,
                        promptText = gq.promptText,
                        optionsJson = gq.options?.let { com.squareup.moshi.Moshi.Builder().build().adapter(List::class.java).toJson(it) },
                        correctAnswer = gq.correctAnswer,
                        topicName = gq.topicName,
                        explanation = gq.explanation
                    )
                } ?: emptyList()

                val newQuizId = repository.createQuizWithQuestions(quizEntity, questionsList)
                _quizGenState.value = QuizGenState.Success(newQuizId)
                loadQuizForPlaying(newQuizId)
            } catch (e: Exception) {
                _quizGenState.value = QuizGenState.Error("Failed to generate quiz: ${e.message}")
            }
        }
    }

    fun loadQuizForPlaying(quizId: Long) {
        viewModelScope.launch {
            val quizWithQuestions = repository.getQuizWithQuestionsById(quizId)
            _activeQuizWithQuestions.value = quizWithQuestions
            if (quizWithQuestions != null) {
                _currentQuestionIndex.value = quizWithQuestions.quiz.currentQuestionIndex.coerceAtMost(
                    (quizWithQuestions.questions.size - 1).coerceAtLeast(0)
                )
            }
        }
    }

    fun submitAnswer(question: QuestionEntity, userAnswerText: String) {
        val currentQuizWithQ = _activeQuizWithQuestions.value ?: return
        val scoringMode = try {
            ScoringMode.valueOf(currentQuizWithQ.quiz.scoringMode)
        } catch (e: Exception) {
            ScoringMode.FORGIVING
        }

        viewModelScope.launch {
            _evaluatingAnswer.value = true
            try {
                val evalResult = GeminiApiService.evaluateAnswer(
                    questionType = question.questionType,
                    promptText = question.promptText,
                    correctAnswer = question.correctAnswer,
                    userAnswer = userAnswerText,
                    topicName = question.topicName,
                    explanation = question.explanation,
                    scoringMode = scoringMode
                )

                val updatedQuestion = question.copy(
                    userAnswer = userAnswerText,
                    isAnswered = true,
                    isCorrect = evalResult.isCorrect,
                    scoreEarned = evalResult.scoreEarned,
                    feedbackMessage = "${evalResult.whyWrong}\n\nKey Concept: ${evalResult.exactTopic}\nTips: ${evalResult.learningTip}"
                )

                repository.updateQuestion(updatedQuestion)
                _mistakeEvaluationResult.value = evalResult

                // Reload active state & calculate scores
                val fresh = repository.getQuizWithQuestionsById(currentQuizWithQ.quiz.id)
                _activeQuizWithQuestions.value = fresh

                if (fresh != null) {
                    val answeredCount = fresh.questions.count { it.isAnswered }
                    val totalPointsEarned = fresh.questions.mapNotNull { it.scoreEarned }.sum()
                    val totalQuestions = fresh.questions.size
                    val currentScorePct = if (totalQuestions > 0) (totalPointsEarned / totalQuestions) * 100f else 0f
                    val isAllAnswered = answeredCount >= totalQuestions

                    val updatedQuiz = fresh.quiz.copy(
                        lastAttemptAt = System.currentTimeMillis(),
                        lastScore = currentScorePct,
                        bestScore = maxOf(currentScorePct, fresh.quiz.bestScore ?: 0f),
                        isCompleted = isAllAnswered,
                        currentQuestionIndex = _currentQuestionIndex.value
                    )
                    repository.updateQuiz(updatedQuiz)
                }

            } catch (e: Exception) {
                // Local fallback evaluation
                val isExactMatch = userAnswerText.trim().equals(question.correctAnswer.trim(), ignoreCase = true)
                val fallbackEval = AnswerEvaluationResult(
                    isCorrect = isExactMatch,
                    scoreEarned = if (isExactMatch) 1f else 0.5f,
                    whyWrong = if (isExactMatch) "Correct!" else "Check key concept details.",
                    correctAnswerExplanation = "Expected: ${question.correctAnswer}. ${question.explanation}",
                    exactTopic = question.topicName,
                    learningTip = "Review the chapter notes for ${question.topicName}"
                )
                _mistakeEvaluationResult.value = fallbackEval
            } finally {
                _evaluatingAnswer.value = false
            }
        }
    }

    fun dismissMistakeDialog() {
        _mistakeEvaluationResult.value = null
    }

    fun goToNextQuestion() {
        val quiz = _activeQuizWithQuestions.value ?: return
        if (_currentQuestionIndex.value < quiz.questions.size - 1) {
            _currentQuestionIndex.value += 1
            viewModelScope.launch {
                repository.updateQuiz(quiz.quiz.copy(currentQuestionIndex = _currentQuestionIndex.value))
            }
        }
    }

    fun goToPreviousQuestion() {
        if (_currentQuestionIndex.value > 0) {
            _currentQuestionIndex.value -= 1
        }
    }

    fun resetAndReviseQuiz(quizId: Long) {
        viewModelScope.launch {
            repository.resetQuizProgress(quizId)
            loadQuizForPlaying(quizId)
        }
    }

    fun deleteQuiz(quizId: Long) {
        viewModelScope.launch {
            repository.deleteQuiz(quizId)
            if (_activeQuizWithQuestions.value?.quiz?.id == quizId) {
                _activeQuizWithQuestions.value = null
            }
        }
    }

    fun clearQuizGenState() {
        _quizGenState.value = QuizGenState.Idle
    }
}
