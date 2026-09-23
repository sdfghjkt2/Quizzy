package com.example.ai

import com.example.BuildConfig
import com.example.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiApiService {

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
    private val MODEL_CANDIDATES_FLASH = listOf(
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-3.5-flash-lite",
        "gemini-2.5-flash-lite",
        "gemini-flash"
    )
    private val MODEL_CANDIDATES_PRO = listOf(
        "gemini-2.5-pro",
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-3.5-flash-lite",
        "gemini-2.5-flash-lite"
    )

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val generatedQuizAdapter = moshi.adapter(GeneratedQuizResponse::class.java)
    private val answerEvaluationAdapter = moshi.adapter(AnswerEvaluationResult::class.java)

    private var customApiKey: String? = null

    fun setApiKey(key: String?) {
        customApiKey = key?.trim()
    }

    fun getApiKey(): String {
        return if (!customApiKey.isNullOrBlank()) customApiKey!! else BuildConfig.GEMINI_API_KEY
    }

    /**
     * Performs OCR and document analysis on raw text or uploaded Image/PDF page Base64 streams
     */
    suspend fun performOcrAndAnalysis(
        rawText: String?,
        imageBase64List: List<String>?,
        customUserPrompt: String?
    ): Pair<String, DocumentAnalysis> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()

        val contentsArray = JSONArray()
        val partsArray = JSONArray()

        val systemInstructionText = """
            You are an advanced OCR and Document Understanding AI.
            Your task is to:
            1. Perform full, precise OCR to transcribe every single line of readable text from the provided image(s) or document text.
            2. Clean up formatting, organize headers, bullet points, equations, or tables clearly.
            3. Provide a structured analysis containing: Title, Executive Summary, Key Topics list, and Extracted Core Concepts.
            
            Return a structured JSON with two top-level keys:
            "extractedText": <full transcribed text with line breaks>,
            "analysis": {
               "title": <short title>,
               "summary": <concise summary>,
               "keyTopics": [<list of main topics>],
               "extractedConcepts": [
                  {"topic": <concept name>, "description": <key detail>}
               ]
            }
        """.trimIndent()

        // Build prompt content
        if (!rawText.isNullOrEmpty()) {
            partsArray.put(JSONObject().put("text", "DOCUMENT TEXT CONTENT:\n$rawText"))
        }

        imageBase64List?.forEach { base64Data ->
            val inlineDataObj = JSONObject()
                .put("mimeType", "image/jpeg")
                .put("data", base64Data)
            partsArray.put(JSONObject().put("inlineData", inlineDataObj))
        }

        val promptIntro = if (!customUserPrompt.isNullOrBlank()) {
            "User Specific Prompt Focus: $customUserPrompt\n\nPlease transcribe and analyze the provided document/image:"
        } else {
            "Please perform OCR and analyze this document:"
        }
        partsArray.put(JSONObject().put("text", promptIntro))

        val contentObj = JSONObject().put("parts", partsArray)
        contentsArray.put(contentObj)

        val requestJson = JSONObject()
            .put("contents", contentsArray)
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstructionText))))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))

        val responseString = executeWithModelFallback(MODEL_CANDIDATES_FLASH, requestJson.toString())
        val parsedText = extractCandidateText(responseString)

        try {
            val jsonRoot = JSONObject(parsedText)
            val extractedText = jsonRoot.optString("extractedText", rawText ?: "Transcribed OCR text")
            val analysisObj = jsonRoot.optJSONObject("analysis")

            val title = analysisObj?.optString("title", "Document Analysis") ?: "Document Analysis"
            val summary = analysisObj?.optString("summary", "Summary of document key points.") ?: "Summary of document."
            val keyTopics = mutableListOf<String>()
            val keyTopicsArray = analysisObj?.optJSONArray("keyTopics")
            if (keyTopicsArray != null) {
                for (i in 0 until keyTopicsArray.length()) {
                    keyTopics.add(keyTopicsArray.getString(i))
                }
            }
            if (keyTopics.isEmpty()) keyTopics.add("Core Concepts")

            val concepts = mutableListOf<ConceptDetail>()
            val conceptsArray = analysisObj?.optJSONArray("extractedConcepts")
            if (conceptsArray != null) {
                for (i in 0 until conceptsArray.length()) {
                    val cObj = conceptsArray.getJSONObject(i)
                    concepts.add(ConceptDetail(cObj.optString("topic"), cObj.optString("description")))
                }
            }

            Pair(extractedText, DocumentAnalysis(title, summary, keyTopics, concepts))
        } catch (e: Exception) {
            val fallbackText = rawText ?: "OCR text extracted successfully."
            Pair(fallbackText, DocumentAnalysis("Analyzed Document", fallbackText.take(200), listOf("Main Topic"), emptyList()))
        }
    }

    /**
     * Generates custom interactive quiz questions based on extracted text, user prompt, question modes & scoring modes.
     */
    suspend fun generateQuizFromDocument(
        extractedText: String,
        config: QuizGenerationConfig
    ): GeneratedQuizResponse = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()

        val questionTypeInstruction = when (config.questionTypeMode) {
            QuestionTypeMode.OBJECTIVE -> "Create ONLY OBJECTIVE multiple-choice questions (questionType = 'OBJECTIVE') with 4 options (A, B, C, D) in the 'options' array, and 'correctAnswer' matching the exact option text."
            QuestionTypeMode.SUBJECTIVE -> "Create ONLY SUBJECTIVE short-answer questions (questionType = 'SUBJECTIVE') with 'options' set to null, requiring the user to write a short conceptual answer. 'correctAnswer' should be the ideal sample answer key."
            QuestionTypeMode.BOTH -> "Create a mix of both OBJECTIVE multiple choice questions and SUBJECTIVE short answer questions. Set 'questionType' to either 'OBJECTIVE' or 'SUBJECTIVE'."
        }

        val scoringModeNote = when (config.scoringMode) {
            ScoringMode.DISCRETE -> "Note: This quiz uses DISCRETE (Strict) scoring. Ensure objective options are distantly distinct and subjective answer key specifies precise required keywords."
            ScoringMode.FORGIVING -> "Note: This quiz uses FORGIVING (Partial Credit) scoring. Craft questions that evaluate core understanding with explanatory context."
        }

        val systemPrompt = """
            You are an expert Educational Quiz Author & Assessment Specialist.
            Create an interactive quiz with exactly ${config.questionCount} questions based on the provided document text.
            
            Guidelines:
            - $questionTypeInstruction
            - $scoringModeNote
            - Ensure questions cover key topics and test real understanding, not just rote memorization.
            - Include the exact sub-topic/concept name in 'topicName' for each question (e.g., "Mitochondrial Respiration", "Binary Search Complexity", "Causes of WWI").
            - Provide a detailed explanation in 'explanation' explaining WHY the correct answer is right.
            ${if (config.customFocusPrompt.isNotBlank()) "User Custom Prompt Focus: ${config.customFocusPrompt}" else ""}
            
            Return JSON in this EXACT schema:
            {
              "title": "<Catchy Quiz Title>",
              "summary": "<Short quiz description>",
              "keyTopics": ["<Topic 1>", "<Topic 2>"],
              "questions": [
                {
                  "questionType": "OBJECTIVE" | "SUBJECTIVE",
                  "promptText": "<Question statement>",
                  "options": ["Option A", "Option B", "Option C", "Option D"] or null for subjective,
                  "correctAnswer": "<Correct option or ideal short answer>",
                  "topicName": "<Exact topic/concept name>",
                  "explanation": "<Comprehensive explanation>"
                }
              ]
            }
        """.trimIndent()

        val requestPrompt = "DOCUMENT TEXT:\n$extractedText"

        val contentsArray = JSONArray().put(
            JSONObject().put("parts", JSONArray().put(JSONObject().put("text", requestPrompt)))
        )

        val requestJson = JSONObject()
            .put("contents", contentsArray)
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))

        val responseString = executeWithModelFallback(MODEL_CANDIDATES_PRO, requestJson.toString())
        val parsedJsonText = extractCandidateText(responseString)

        try {
            generatedQuizAdapter.fromJson(parsedJsonText)
                ?: GeneratedQuizResponse(title = "Generated Quiz", questions = emptyList())
        } catch (e: Exception) {
            // Fallback manual parsing if schema differs slightly
            parseFallbackQuizJson(parsedJsonText)
        }
    }

    /**
     * Evaluates user answer, generating mistake explanations, correct answer popup details, and discrete vs forgiving scores.
     */
    suspend fun evaluateAnswer(
        questionType: String,
        promptText: String,
        correctAnswer: String,
        userAnswer: String,
        topicName: String,
        explanation: String,
        scoringMode: ScoringMode
    ): AnswerEvaluationResult = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()

        val evaluationRules = if (scoringMode == ScoringMode.DISCRETE) {
            """
            MODE: DISCRETE (Strict Binary Scoring)
            - For OBJECTIVE questions: Must match the correct option.
            - For SUBJECTIVE questions: Strictly check if ALL essential key facts/terms are present.
            - Score: Exactly 1.0 if completely correct, 0.0 if missing key elements.
            """.trimIndent()
        } else {
            """
            MODE: FORGIVING (Partial Credit & Lenient Evaluation)
            - Accept partial conceptual understanding, minor typos, or alternative wording.
            - Score: Floating value between 0.0 and 1.0 (e.g. 1.0 for full match, 0.8 for minor gap, 0.5 for partial credit, 0.0 for completely wrong).
            """.trimIndent()
        }

        val systemPrompt = """
            You are an AI Tutor and Educational Assessor evaluating a student's answer.
            
            $evaluationRules
            
            Question: "$promptText"
            Topic: "$topicName"
            Reference Answer: "$correctAnswer"
            Student's Answer: "$userAnswer"
            Reference Explanation: "$explanation"
            
            Analyze the student's answer and respond with JSON matching this EXACT schema:
            {
               "isCorrect": boolean (true if score >= 0.7, false otherwise),
               "scoreEarned": float (0.0 to 1.0),
               "whyWrong": "<Detailed friendly popup explanation of why the user's answer was incorrect/incomplete, pointing out specific mistakes or missing points>",
               "correctAnswerExplanation": "<Clear statement of the exact correct answer and why it is right>",
               "exactTopic": "<Exact topic/concept area where user got it wrong or needs revision, e.g. '$topicName'>",
               "learningTip": "<Actionable study advice or key takeaway to remember>"
            }
        """.trimIndent()

        val contentsArray = JSONArray().put(
            JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "Please grade the student's response.")))
        )

        val requestJson = JSONObject()
            .put("contents", contentsArray)
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))

        val responseString = executeWithModelFallback(MODEL_CANDIDATES_PRO, requestJson.toString())
        val parsedJsonText = extractCandidateText(responseString)

        try {
            answerEvaluationAdapter.fromJson(parsedJsonText) ?: AnswerEvaluationResult(
                isCorrect = userAnswer.trim().equals(correctAnswer.trim(), ignoreCase = true),
                scoreEarned = if (userAnswer.trim().equals(correctAnswer.trim(), ignoreCase = true)) 1f else 0f,
                whyWrong = "Answer did not match expected solution.",
                correctAnswerExplanation = "Correct answer: $correctAnswer. $explanation",
                exactTopic = topicName,
                learningTip = "Review $topicName key concepts."
            )
        } catch (e: Exception) {
            val isExact = userAnswer.trim().equals(correctAnswer.trim(), ignoreCase = true)
            AnswerEvaluationResult(
                isCorrect = isExact,
                scoreEarned = if (isExact) 1f else 0f,
                whyWrong = if (isExact) "Great job!" else "Your response did not match the expected answer.",
                correctAnswerExplanation = "Correct answer is: $correctAnswer. $explanation",
                exactTopic = topicName,
                learningTip = "Focus study efforts on $topicName."
            )
        }
    }

    private fun executeWithModelFallback(modelCandidates: List<String>, jsonBody: String): String {
        val apiKey = getApiKey()
        var lastException: Exception? = null
        for (model in modelCandidates) {
            val endpoint = "$BASE_URL$model:generateContent?key=$apiKey"
            try {
                val body = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(endpoint)
                    .post(body)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val responseText = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    return responseText
                } else {
                    val code = response.code
                    if (code == 429 || code >= 500 || responseText.contains("RESOURCE_EXHAUSTED", ignoreCase = true) || responseText.contains("overloaded", ignoreCase = true)) {
                        lastException = IllegalStateException("Model $model rate limited/error ($code): $responseText")
                        continue
                    } else {
                        throw IllegalStateException("Gemini API Error $code: $responseText")
                    }
                }
            } catch (e: Exception) {
                lastException = e
                continue
            }
        }
        throw lastException ?: IllegalStateException("All Gemini fallback models exhausted.")
    }

    private fun extractCandidateText(rawResponseJson: String): String {
        val root = JSONObject(rawResponseJson)
        val candidates = root.optJSONArray("candidates")
        val candidate = candidates?.optJSONObject(0)
        val content = candidate?.optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        val text = parts?.optJSONObject(0)?.optString("text") ?: ""
        return text.trim()
    }

    private fun parseFallbackQuizJson(jsonText: String): GeneratedQuizResponse {
        val questions = mutableListOf<GeneratedQuestion>()
        try {
            val root = JSONObject(jsonText)
            val title = root.optString("title", "Document Quiz")
            val summary = root.optString("summary", "Generated Quiz")
            val qArray = root.optJSONArray("questions")
            if (qArray != null) {
                for (i in 0 until qArray.length()) {
                    val qObj = qArray.getJSONObject(i)
                    val optionsList = mutableListOf<String>()
                    val optsArray = qObj.optJSONArray("options")
                    if (optsArray != null) {
                        for (j in 0 until optsArray.length()) {
                            optionsList.add(optsArray.getString(j))
                        }
                    }
                    questions.add(
                        GeneratedQuestion(
                            questionType = qObj.optString("questionType", "OBJECTIVE"),
                            promptText = qObj.optString("promptText", "Question ${i + 1}"),
                            options = if (optionsList.isNotEmpty()) optionsList else null,
                            correctAnswer = qObj.optString("correctAnswer", ""),
                            topicName = qObj.optString("topicName", "General Topic"),
                            explanation = qObj.optString("explanation", "")
                        )
                    )
                }
            }
            return GeneratedQuizResponse(title = title, summary = summary, questions = questions)
        } catch (e: Exception) {
            return GeneratedQuizResponse(title = "Quizzy", questions = emptyList())
        }
    }
}
