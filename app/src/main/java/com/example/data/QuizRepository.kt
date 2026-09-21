package com.example.data

import kotlinx.coroutines.flow.Flow

class QuizRepository(private val quizDao: QuizDao) {

    val allQuizzes: Flow<List<QuizEntity>> = quizDao.getAllQuizzes()
    val allQuizzesWithQuestions: Flow<List<QuizWithQuestions>> = quizDao.getAllQuizzesWithQuestions()

    suspend fun getQuizWithQuestionsById(quizId: Long): QuizWithQuestions? {
        return quizDao.getQuizWithQuestionsById(quizId)
    }

    suspend fun createQuizWithQuestions(
        quiz: QuizEntity,
        questions: List<QuestionEntity>
    ): Long {
        val quizId = quizDao.insertQuiz(quiz)
        val questionsWithQuizId = questions.map { it.copy(quizId = quizId) }
        quizDao.insertQuestions(questionsWithQuizId)
        return quizId
    }

    suspend fun updateQuiz(quiz: QuizEntity) {
        quizDao.updateQuiz(quiz)
    }

    suspend fun updateQuestion(question: QuestionEntity) {
        quizDao.updateQuestion(question)
    }

    suspend fun deleteQuiz(quizId: Long) {
        quizDao.deleteQuizById(quizId)
    }

    suspend fun resetQuizProgress(quizId: Long) {
        val quizWithQuestions = quizDao.getQuizWithQuestionsById(quizId) ?: return
        val resetQuestions = quizWithQuestions.questions.map {
            it.copy(
                userAnswer = null,
                isAnswered = false,
                isCorrect = null,
                scoreEarned = null,
                feedbackMessage = null
            )
        }
        quizDao.insertQuestions(resetQuestions)
        val updatedQuiz = quizWithQuestions.quiz.copy(
            isCompleted = false,
            currentQuestionIndex = 0,
            lastAttemptAt = System.currentTimeMillis()
        )
        quizDao.updateQuiz(updatedQuiz)
    }
}
