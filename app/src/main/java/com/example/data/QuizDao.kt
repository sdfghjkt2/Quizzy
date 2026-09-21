package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class QuizWithQuestions(
    @Embedded val quiz: QuizEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "quizId"
    )
    val questions: List<QuestionEntity>
)

@Dao
interface QuizDao {

    @Query("SELECT * FROM quizzes ORDER BY createdAt DESC")
    fun getAllQuizzes(): Flow<List<QuizEntity>>

    @Transaction
    @Query("SELECT * FROM quizzes ORDER BY createdAt DESC")
    fun getAllQuizzesWithQuestions(): Flow<List<QuizWithQuestions>>

    @Transaction
    @Query("SELECT * FROM quizzes WHERE id = :quizId")
    suspend fun getQuizWithQuestionsById(quizId: Long): QuizWithQuestions?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuiz(quiz: QuizEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<QuestionEntity>)

    @Update
    suspend fun updateQuiz(quiz: QuizEntity)

    @Update
    suspend fun updateQuestion(question: QuestionEntity)

    @Query("DELETE FROM quizzes WHERE id = :quizId")
    suspend fun deleteQuizById(quizId: Long)

    @Query("DELETE FROM questions WHERE quizId = :quizId")
    suspend fun deleteQuestionsForQuiz(quizId: Long)
}
