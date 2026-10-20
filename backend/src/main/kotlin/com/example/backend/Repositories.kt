package com.example.backend

import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

data class UserRow(
    val id: Long,
    val phone: String,
    val firstName: String,
    val lastName: String,
    val passwordHash: String,
    val deviceId: String
)

data class CategoryDto(val number: Int, val name: String?)

data class QuestionDto(
    val id: Long,
    val text: String,
    val options: List<String>,
    val correctAnswerIndex: Int,
    val imageUrl: String?
)

data class NewQuestion(
    val text: String,
    val options: List<String>,
    val correctAnswerIndex: Int,
    val imageUrl: String? = null
)

@Repository
class UserRepository(private val jdbc: JdbcClient) {
    private val mapper = RowMapper { rs, _ ->
        UserRow(
            id = rs.getLong("id"),
            phone = rs.getString("phone"),
            firstName = rs.getString("first_name"),
            lastName = rs.getString("last_name"),
            passwordHash = rs.getString("password_hash"),
            deviceId = rs.getString("device_id")
        )
    }

    fun findByPhone(phone: String): UserRow? =
        jdbc.sql("SELECT * FROM users WHERE phone = :phone").param("phone", phone).query(mapper).optional().orElse(null)

    fun findById(id: Long): UserRow? =
        jdbc.sql("SELECT * FROM users WHERE id = :id").param("id", id).query(mapper).optional().orElse(null)

    fun create(phone: String, firstName: String, lastName: String, passwordHash: String, deviceId: String): UserRow =
        jdbc.sql(
            """
            INSERT INTO users (phone, first_name, last_name, password_hash, device_id)
            VALUES (:phone, :firstName, :lastName, :hash, :deviceId)
            RETURNING *
            """
        )
            .param("phone", phone)
            .param("firstName", firstName)
            .param("lastName", lastName)
            .param("hash", passwordHash)
            .param("deviceId", deviceId)
            .query(mapper).single()

    /** Dəyişdirilən sətirlərin sayını qaytarır (0 = belə hesab yoxdur). */
    fun updateDevice(phone: String, deviceId: String): Int =
        jdbc.sql("UPDATE users SET device_id = :deviceId WHERE phone = :phone")
            .param("deviceId", deviceId)
            .param("phone", phone)
            .update()
}

@Repository
class PurchaseRepository(private val jdbc: JdbcClient) {
    fun isUnlocked(phone: String): Boolean =
        jdbc.sql("SELECT unlocked FROM purchases WHERE phone = :phone").param("phone", phone)
            .query(Boolean::class.java).optional().orElse(false)

    fun unlock(phone: String) {
        jdbc.sql(
            """
            INSERT INTO purchases (phone, unlocked, updated_at) VALUES (:phone, TRUE, now())
            ON CONFLICT (phone) DO UPDATE SET unlocked = TRUE, updated_at = now()
            """
        ).param("phone", phone).update()
    }
}

@Repository
class CategoryRepository(private val jdbc: JdbcClient) {
    private val mapper = RowMapper { rs, _ -> CategoryDto(rs.getInt("number"), rs.getString("name")) }

    fun findAll(): List<CategoryDto> = jdbc.sql("SELECT * FROM categories ORDER BY number").query(mapper).list()

    fun exists(number: Int): Boolean =
        jdbc.sql("SELECT count(*) FROM categories WHERE number = :n").param("n", number).query(Int::class.java).single() > 0

    /**
     * Ən kiçik boş nömrəni götürür: silinmiş sınağın nömrəsi varsa onu yenidən
     * istifadə edir, yoxdursa sonuncudan sonrakı nömrəni yaradır.
     */
    fun create(name: String?): CategoryDto =
        jdbc.sql(
            """
            INSERT INTO categories (number, name)
            SELECT min(n), CAST(:name AS VARCHAR)
            FROM generate_series(1, (SELECT coalesce(max(number), 0) + 1 FROM categories)) AS n
            WHERE n NOT IN (SELECT number FROM categories)
            RETURNING *
            """
        ).param("name", name).query(mapper).single()

    fun delete(number: Int): Int = jdbc.sql("DELETE FROM categories WHERE number = :n").param("n", number).update()
}

@Repository
class QuestionRepository(private val jdbc: JdbcClient) {
    private val mapper = RowMapper { rs, _ ->
        QuestionDto(
            id = rs.getLong("id"),
            text = rs.getString("text"),
            options = (rs.getArray("options").array as Array<*>).map { it as String },
            correctAnswerIndex = rs.getInt("correct_answer_index"),
            imageUrl = rs.getString("image_url")
        )
    }

    fun findByCategory(number: Int): List<QuestionDto> =
        jdbc.sql("SELECT * FROM questions WHERE category_number = :n ORDER BY id").param("n", number).query(mapper).list()

    @Transactional
    fun insertAll(categoryNumber: Int, questions: List<NewQuestion>): List<QuestionDto> = questions.map { q ->
        jdbc.sql(
            """
            INSERT INTO questions (category_number, text, options, correct_answer_index, image_url)
            VALUES (:n, :text, :options, :correct, :imageUrl)
            RETURNING *
            """
        )
            .param("n", categoryNumber)
            .param("text", q.text)
            .param("options", q.options.toTypedArray())
            .param("correct", q.correctAnswerIndex)
            .param("imageUrl", q.imageUrl)
            .query(mapper).single()
    }

    fun delete(id: Long): Int = jdbc.sql("DELETE FROM questions WHERE id = :id").param("id", id).update()
}

data class ResultDto(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val phone: String,
    val categoryNumber: Int,
    val categoryName: String?,
    val score: Int,
    val total: Int,
    val createdAt: Long   // epoch millisaniyə
)

@Repository
class ResultRepository(private val jdbc: JdbcClient) {
    /** Ən son nəticələr, yenidən köhnəyə. */
    fun findRecent(limit: Int): List<ResultDto> =
        jdbc.sql(
            """
            SELECT r.id, u.first_name, u.last_name, u.phone, r.category_number, c.name AS category_name,
                   r.score, r.total, r.created_at
            FROM results r
            JOIN users u ON u.id = r.user_id
            LEFT JOIN categories c ON c.number = r.category_number
            ORDER BY r.created_at DESC
            LIMIT :limit
            """
        ).param("limit", limit).query { rs, _ ->
            ResultDto(
                id = rs.getLong("id"),
                firstName = rs.getString("first_name"),
                lastName = rs.getString("last_name"),
                phone = rs.getString("phone"),
                categoryNumber = rs.getInt("category_number"),
                categoryName = rs.getString("category_name"),
                score = rs.getInt("score"),
                total = rs.getInt("total"),
                createdAt = rs.getTimestamp("created_at").time
            )
        }.list()

    fun insert(userId: Long, categoryNumber: Int, score: Int, total: Int) {
        jdbc.sql("INSERT INTO results (user_id, category_number, score, total) VALUES (:u, :c, :s, :t)")
            .param("u", userId)
            .param("c", categoryNumber)
            .param("s", score)
            .param("t", total)
            .update()
    }
}
