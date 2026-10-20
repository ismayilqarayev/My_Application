package com.example.myapplication

import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import java.io.IOException
import java.util.concurrent.TimeUnit

// ==========================================================================
// BACKEND (Spring Boot) İLƏ ƏLAQƏ
// Əvvəllər tətbiq birbaşa Firebase-ə qoşulurdu. İndi bütün məlumatlar öz
// serverimizdəki REST API vasitəsilə gəlir (bax: backend/ qovluğu).
// "Retrofit" - aşağıdakı interfeysdəki funksiyaları avtomatik HTTP sorğularına çevirir.
// ==========================================================================

data class PhoneRequest(val phone: String)
data class PhoneCheckResponse(val registered: Boolean)
data class RegisterRequest(val phone: String, val firstName: String, val lastName: String, val password: String, val deviceId: String)
data class LoginRequest(val phone: String, val password: String, val deviceId: String)
data class AdminLoginRequest(val password: String)
data class MeResponse(val phone: String, val firstName: String, val lastName: String, val unlocked: Boolean)
data class AuthResponse(val token: String, val user: MeResponse)
data class TokenResponse(val token: String)
data class ResultRequest(val categoryNumber: Int, val score: Int, val total: Int)
data class NewCategoryRequest(val name: String?)
data class NewQuestion(val text: String, val options: List<String>, val correctAnswerIndex: Int, val imageUrl: String? = null)
data class ImageResponse(val url: String)
private data class ErrorResponse(val message: String?)

interface ApiService {
    @POST("api/auth/check-phone")
    suspend fun checkPhone(@Body body: PhoneRequest): PhoneCheckResponse

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("api/auth/admin")
    suspend fun adminLogin(@Body body: AdminLoginRequest): TokenResponse

    @GET("api/me")
    suspend fun me(): MeResponse

    @GET("api/categories")
    suspend fun categories(): List<Category>

    @GET("api/categories/{number}/questions")
    suspend fun questions(@Path("number") number: Int): List<Question>

    @POST("api/results")
    suspend fun saveResult(@Body body: ResultRequest)

    // ---- Yalnız admin token-i ilə ----

    @GET("api/admin/categories/{number}/questions")
    suspend fun adminQuestions(@Path("number") number: Int): List<Question>

    @POST("api/admin/categories")
    suspend fun createCategory(@Body body: NewCategoryRequest): Category

    @DELETE("api/admin/categories/{number}")
    suspend fun deleteCategory(@Path("number") number: Int)

    @POST("api/admin/categories/{number}/questions")
    suspend fun addQuestions(@Path("number") number: Int, @Body body: List<NewQuestion>): List<Question>

    @DELETE("api/admin/questions/{id}")
    suspend fun deleteQuestion(@Path("id") id: Long)

    @Multipart
    @POST("api/admin/images")
    suspend fun uploadImage(@Part file: MultipartBody.Part): ImageResponse

    @POST("api/admin/unlock")
    suspend fun unlock(@Body body: PhoneRequest)

    @POST("api/admin/reset-device")
    suspend fun resetDevice(@Body body: PhoneRequest)
}

/**
 * Hər sorğuya uyğun "Authorization: Bearer <token>" başlığını əlavə edir:
 * "/api/admin/..." sorğularına admin token-i, qalanlarına şagird token-i.
 */
class ApiClient(
    private val studentToken: () -> String?,
    private val adminToken: () -> String?
) {
    val baseUrl: String = BuildConfig.API_BASE_URL.trimEnd('/') + "/"

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request()
        val token = if (request.url.encodedPath.startsWith("/api/admin/")) adminToken() else studentToken()
        chain.proceed(
            if (token == null) request
            else request.newBuilder().header("Authorization", "Bearer $token").build()
        )
    }

    val service: ApiService = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(
            OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)

    /** Server şəkil üçün "/uploads/..." kimi nisbi yol qaytarır - Coil üçün tam ünvana çeviririk. */
    fun absoluteUrl(path: String?): String? = path?.let { if (it.startsWith("http")) it else baseUrl.trimEnd('/') + it }
}

/** HTTP status kodu (şəbəkə xətasında null). */
val Throwable.httpCode: Int? get() = (this as? HttpException)?.code()

/** Serverin göndərdiyi Azərbaycan dilli xəta mesajını, yoxdursa ümumi mesajı qaytarır. */
fun Throwable.userMessage(fallback: String): String = when (this) {
    is HttpException -> runCatching {
        Gson().fromJson(response()?.errorBody()?.string(), ErrorResponse::class.java)?.message
    }.getOrNull() ?: fallback
    is IOException -> "Serverə qoşulmaq mümkün olmadı. İnternet bağlantınızı yoxlayın."
    else -> fallback
}
