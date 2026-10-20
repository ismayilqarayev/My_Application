package com.example.backend

import jakarta.servlet.http.HttpServletRequest
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest

data class PhoneRequest(val phone: String)
data class PhoneCheckResponse(val registered: Boolean)
data class RegisterRequest(val phone: String, val firstName: String, val lastName: String, val password: String, val deviceId: String)
data class LoginRequest(val phone: String, val password: String, val deviceId: String)
data class AdminLoginRequest(val password: String)
data class MeResponse(val phone: String, val firstName: String, val lastName: String, val unlocked: Boolean)
data class AuthResponse(val token: String, val user: MeResponse)
data class TokenResponse(val token: String)

/**
 * Telefon nömrəsini verilənlər bazasında saxlanılan sabit formata gətirir:
 * yalnız rəqəmlər, ölkə kodu (994) daxil. "0501234567" və "501234567" hər ikisi
 * "994501234567" olur. Yanlış nömrədə null qaytarır.
 */
fun normalizePhone(raw: String): String? {
    var digits = raw.filter { it.isDigit() }
    if (digits.length == 10 && digits.startsWith("0")) digits = digits.drop(1)
    if (digits.length == 9) digits = "994$digits"
    return digits.takeIf { it.length in 10..15 }
}

fun requirePhone(raw: String): String =
    normalizePhone(raw) ?: throw badRequest("Zəhmət olmasa düzgün telefon nömrəsi daxil edin.")

/** Token-dəki şagirdi bazadan yükləyir və hesabın hələ də bu cihaza bağlı olduğunu yoxlayır. */
@Component
class CurrentUser(private val users: UserRepository) {
    fun load(jwt: Jwt): UserRow {
        val user = users.findById(jwt.userId) ?: throw ApiException(HttpStatus.UNAUTHORIZED, "Hesab tapılmadı.")
        if (user.deviceId != jwt.deviceId) {
            throw ApiException(HttpStatus.UNAUTHORIZED, "Bu hesab başqa bir telefona bağlanıb. Yenidən daxil olun.")
        }
        return user
    }
}

@RestController
@RequestMapping("/api")
class AuthController(
    private val users: UserRepository,
    private val purchases: PurchaseRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokens: TokenService,
    private val rateLimiter: LoginRateLimiter,
    private val currentUser: CurrentUser,
    private val props: AppProperties
) {

    @GetMapping("/health")
    fun health() = mapOf("status" to "ok")

    @PostMapping("/auth/check-phone")
    fun checkPhone(@RequestBody req: PhoneRequest) =
        PhoneCheckResponse(users.findByPhone(requirePhone(req.phone)) != null)

    @PostMapping("/auth/register")
    fun register(@RequestBody req: RegisterRequest): AuthResponse {
        val phone = requirePhone(req.phone)
        val firstName = req.firstName.trim()
        val lastName = req.lastName.trim()
        if (firstName.isEmpty() || lastName.isEmpty()) throw badRequest("Ad və soyad boş ola bilməz.")
        if (firstName.length > 100 || lastName.length > 100) throw badRequest("Ad və ya soyad çox uzundur.")
        if (req.password.length !in 4..100) throw badRequest("Parol ən azı 4 simvol olmalıdır.")
        if (req.deviceId.isBlank() || req.deviceId.length > 100) throw badRequest("Cihaz məlumatı yanlışdır.")

        val user = try {
            users.create(phone, firstName, lastName, passwordEncoder.encode(req.password)!!, req.deviceId)
        } catch (_: DuplicateKeyException) {
            throw ApiException(HttpStatus.CONFLICT, "Bu nömrə artıq qeydiyyatdan keçib.")
        }
        return AuthResponse(tokens.studentToken(user.id, user.deviceId), toMe(user))
    }

    @PostMapping("/auth/login")
    fun login(@RequestBody req: LoginRequest, request: HttpServletRequest): AuthResponse {
        val phone = requirePhone(req.phone)
        val keys = arrayOf("ip:${request.remoteAddr}", "phone:$phone")
        rateLimiter.check(*keys)

        val user = users.findByPhone(phone)
        if (user == null || !passwordEncoder.matches(req.password, user.passwordHash)) {
            rateLimiter.recordFailure(*keys)
            throw ApiException(HttpStatus.UNAUTHORIZED, "Giriş uğursuz oldu: nömrə və ya parol yanlışdır.")
        }
        rateLimiter.reset("phone:$phone")

        if (req.deviceId.isBlank()) throw badRequest("Cihaz məlumatı yanlışdır.")
        if (user.deviceId.isNotBlank() && user.deviceId != req.deviceId) {
            throw ApiException(
                HttpStatus.FORBIDDEN,
                "Bu hesab artıq başqa bir telefonda istifadə olunur. Əgər bu sizin öz telefonunuzdursa, müəllimlə əlaqə saxlayıb hesabı sıfırlatdırın."
            )
        }
        if (user.deviceId.isBlank()) users.updateDevice(phone, req.deviceId)

        return AuthResponse(tokens.studentToken(user.id, req.deviceId), toMe(user))
    }

    @PostMapping("/auth/admin")
    fun adminLogin(@RequestBody req: AdminLoginRequest, request: HttpServletRequest): TokenResponse {
        if (props.adminPassword.isBlank()) throw ApiException(HttpStatus.FORBIDDEN, "Admin girişi serverdə aktiv deyil.")
        val key = "admin-ip:${request.remoteAddr}"
        rateLimiter.check(key)
        val ok = MessageDigest.isEqual(req.password.toByteArray(), props.adminPassword.toByteArray())
        if (!ok) {
            rateLimiter.recordFailure(key)
            throw ApiException(HttpStatus.UNAUTHORIZED, "Yanlış parol!")
        }
        rateLimiter.reset(key)
        return TokenResponse(tokens.adminToken())
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal jwt: Jwt): MeResponse = toMe(currentUser.load(jwt))

    private fun toMe(user: UserRow) = MeResponse(user.phone, user.firstName, user.lastName, purchases.isUnlocked(user.phone))
}
