package com.example.backend

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class ResultRequest(val categoryNumber: Int, val score: Int, val total: Int)

/** Şagird tərəfi: sınaqların siyahısı, suallar və nəticənin yadda saxlanması. */
@RestController
@RequestMapping("/api")
class QuizController(
    private val categories: CategoryRepository,
    private val questions: QuestionRepository,
    private val purchases: PurchaseRepository,
    private val results: ResultRepository,
    private val currentUser: CurrentUser
) {

    @GetMapping("/categories")
    fun categories(): List<CategoryDto> = categories.findAll()

    @GetMapping("/categories/{number}/questions")
    fun questions(@PathVariable number: Int, @AuthenticationPrincipal jwt: Jwt): List<QuestionDto> {
        val user = currentUser.load(jwt)
        val category = categories.find(number) ?: throw notFound("Sınaq tapılmadı.")
        // Ödənişli sınaqlar server tərəfində də yoxlanılır - tətbiqi "sındırmaqla" açmaq olmasın
        if (!category.free && !purchases.isUnlocked(user.phone)) {
            throw ApiException(HttpStatus.FORBIDDEN, "Bu sınaq ödənişlidir.")
        }
        return questions.findByCategory(number)
    }

    @PostMapping("/results")
    fun saveResult(@RequestBody req: ResultRequest, @AuthenticationPrincipal jwt: Jwt): ResponseEntity<Unit> {
        val user = currentUser.load(jwt)
        if (req.total <= 0 || req.score !in 0..req.total) throw badRequest("Nəticə yanlışdır.")
        results.insert(user.id, req.categoryNumber, req.score, req.total)
        return ResponseEntity.noContent().build()
    }
}
