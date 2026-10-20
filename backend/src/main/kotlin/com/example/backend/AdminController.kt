package com.example.backend

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

data class NewCategoryRequest(val name: String? = null)
data class FreeRequest(val free: Boolean)
data class ImageResponse(val url: String)

/** Müəllim (admin) paneli - yalnız admin token-i ilə əlçatandır (bax: SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val categories: CategoryRepository,
    private val questions: QuestionRepository,
    private val purchases: PurchaseRepository,
    private val users: UserRepository,
    private val results: ResultRepository,
    private val props: AppProperties
) {

    @GetMapping("/results")
    fun results(): List<ResultDto> = results.findRecent(RESULTS_LIMIT)

    @GetMapping("/categories/{number}/questions")
    fun questions(@PathVariable number: Int): List<QuestionDto> = questions.findByCategory(number)

    @PostMapping("/categories")
    fun createCategory(@RequestBody req: NewCategoryRequest): CategoryDto = categories.create(cleanName(req.name))

    /** Boş ad göndərilərsə, sınaq yenidən "sınaq N" kimi göstərilir. */
    @PatchMapping("/categories/{number}")
    fun renameCategory(@PathVariable number: Int, @RequestBody req: NewCategoryRequest): CategoryDto =
        categories.rename(number, cleanName(req.name)) ?: throw notFound("Sınaq tapılmadı.")

    @PutMapping("/categories/{number}/free")
    fun setCategoryFree(@PathVariable number: Int, @RequestBody req: FreeRequest): CategoryDto =
        categories.setFree(number, req.free) ?: throw notFound("Sınaq tapılmadı.")

    private fun cleanName(raw: String?): String? {
        val name = raw?.trim()?.takeIf { it.isNotEmpty() }
        if (name != null && name.length > 100) throw badRequest("Sınağın adı çox uzundur.")
        return name
    }

    @DeleteMapping("/categories/{number}")
    fun deleteCategory(@PathVariable number: Int): ResponseEntity<Unit> {
        if (categories.delete(number) == 0) throw notFound("Sınaq tapılmadı.")
        return ResponseEntity.noContent().build()
    }

    /** Həm tək sual, həm də toplu yükləmə üçün istifadə olunur. */
    @PostMapping("/categories/{number}/questions")
    fun addQuestions(@PathVariable number: Int, @RequestBody body: List<NewQuestion>): List<QuestionDto> {
        if (!categories.exists(number)) throw notFound("Sınaq tapılmadı.")
        if (body.isEmpty()) throw badRequest("Heç bir sual göndərilməyib.")
        if (body.size > 500) throw badRequest("Bir dəfəyə ən çox 500 sual əlavə etmək olar.")
        val cleaned = body.mapIndexed { i, q -> validate(q, i + 1) }
        return questions.insertAll(number, cleaned)
    }

    @PutMapping("/questions/{id}")
    fun updateQuestion(@PathVariable id: Long, @RequestBody body: NewQuestion): QuestionDto =
        questions.update(id, validate(body, 1)) ?: throw notFound("Sual tapılmadı.")

    @DeleteMapping("/questions/{id}")
    fun deleteQuestion(@PathVariable id: Long): ResponseEntity<Unit> {
        if (questions.delete(id) == 0) throw notFound("Sual tapılmadı.")
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/images")
    fun uploadImage(@RequestParam("file") file: MultipartFile): ImageResponse {
        val extension = ALLOWED_IMAGE_TYPES[file.contentType]
            ?: throw badRequest("Yalnız JPG, PNG, WEBP və ya GIF şəkil yükləmək olar.")
        if (file.isEmpty) throw badRequest("Şəkil boşdur.")
        val fileName = "${UUID.randomUUID()}.$extension"
        file.transferTo(props.uploadPath.resolve(fileName))
        return ImageResponse("/uploads/$fileName")
    }

    @PostMapping("/unlock")
    fun unlock(@RequestBody req: PhoneRequest): ResponseEntity<Unit> {
        purchases.unlock(requirePhone(req.phone))
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/reset-device")
    fun resetDevice(@RequestBody req: PhoneRequest): ResponseEntity<Unit> {
        if (users.updateDevice(requirePhone(req.phone), "") == 0) throw notFound("Bu nömrə ilə hesab tapılmadı.")
        return ResponseEntity.noContent().build()
    }

    private fun validate(q: NewQuestion, position: Int): NewQuestion {
        val prefix = "Sual №$position:"
        val text = q.text.trim()
        val options = q.options.map { it.trim() }
        if (text.isEmpty()) throw badRequest("$prefix mətn boş ola bilməz.")
        if (text.length > 2000) throw badRequest("$prefix mətn çox uzundur.")
        if (options.size !in 2..6) throw badRequest("$prefix 2-6 arası variant olmalıdır.")
        if (options.any { it.isEmpty() || it.length > 500 }) throw badRequest("$prefix variantlar boş və ya çox uzun ola bilməz.")
        if (q.correctAnswerIndex !in options.indices) throw badRequest("$prefix düzgün cavab seçilməyib.")
        if (q.imageUrl != null && !q.imageUrl.matches(UPLOAD_URL)) throw badRequest("$prefix şəkil ünvanı yanlışdır.")
        return q.copy(text = text, options = options)
    }

    private companion object {
        const val RESULTS_LIMIT = 300
        val ALLOWED_IMAGE_TYPES = mapOf(
            "image/jpeg" to "jpg",
            "image/png" to "png",
            "image/webp" to "webp",
            "image/gif" to "gif"
        )
        val UPLOAD_URL = Regex("^/uploads/[0-9a-f-]{36}\\.(jpg|png|webp|gif)$")
    }
}
