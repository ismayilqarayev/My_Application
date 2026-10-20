package com.example.backend

import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@Configuration
class SecurityConfig(private val props: AppProperties) {

    private val key: SecretKey by lazy {
        require(props.jwtSecret.length >= 32) { "app.jwt-secret (JWT_SECRET) ən azı 32 simvol olmalıdır" }
        SecretKeySpec(props.jwtSecret.toByteArray(), "HmacSHA256")
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val authorities = JwtGrantedAuthoritiesConverter().apply {
            setAuthoritiesClaimName("role")
            setAuthorityPrefix("ROLE_")
        }
        val converter = JwtAuthenticationConverter().apply { setJwtGrantedAuthoritiesConverter(authorities) }

        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.POST, "/api/auth/check-phone", "/api/auth/register", "/api/auth/login", "/api/auth/admin").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/health", "/api/categories", "/uploads/**").permitAll()
                    .requestMatchers("/error").permitAll()
                    .requestMatchers("/api/admin/**").hasRole(Role.ADMIN.name)
                    .anyRequest().hasRole(Role.STUDENT.name)
            }
            .oauth2ResourceServer { it.jwt { jwt -> jwt.jwtAuthenticationConverter(converter) } }
        return http.build()
    }

    @Bean
    fun jwtEncoder(): JwtEncoder = NimbusJwtEncoder(ImmutableSecret<SecurityContext>(key))

    @Bean
    fun jwtDecoder(): JwtDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build()

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}

enum class Role { STUDENT, ADMIN }

@Component
class TokenService(private val encoder: JwtEncoder, private val props: AppProperties) {

    fun studentToken(userId: Long, deviceId: String): String =
        issue(userId.toString(), Role.STUDENT, Duration.ofDays(props.tokenValidityDays), mapOf(DEVICE_CLAIM to deviceId))

    // Admin sessiyası qısa ömürlüdür və tətbiqdə yadda saxlanılmır
    fun adminToken(): String = issue("admin", Role.ADMIN, Duration.ofHours(12), emptyMap())

    private fun issue(subject: String, role: Role, validity: Duration, extra: Map<String, String>): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .subject(subject)
            .issuedAt(now)
            .expiresAt(now.plus(validity))
            .claim("role", role.name)
            .apply { extra.forEach { (k, v) -> claim(k, v) } }
            .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
    }

    companion object {
        const val DEVICE_CLAIM = "device"
    }
}

val Jwt.userId: Long get() = requireNotNull(subject).toLong()
val Jwt.deviceId: String get() = getClaimAsString(TokenService.DEVICE_CLAIM) ?: ""

/**
 * Parolun "kobud güc" (brute force) ilə tapılmasının qarşısını alır: eyni IP-dən
 * və ya eyni hesaba 15 dəqiqədə 10 uğursuz cəhddən sonra giriş müvəqqəti bağlanır.
 * Yaddaşda saxlanılır - server yenidən başlayanda sıfırlanır (tək server üçün kifayətdir).
 */
@Component
class LoginRateLimiter {
    private val failures = ConcurrentHashMap<String, MutableList<Instant>>()

    fun check(vararg keys: String) {
        val cutoff = Instant.now().minus(WINDOW)
        val blocked = keys.any { key ->
            val list = failures[key] ?: return@any false
            synchronized(list) {
                list.removeIf { it.isBefore(cutoff) }
                list.size >= MAX_FAILURES
            }
        }
        if (blocked) throw ApiException(HttpStatus.TOO_MANY_REQUESTS, "Çox sayda uğursuz cəhd. 15 dəqiqə sonra yenidən yoxlayın.")
    }

    fun recordFailure(vararg keys: String) {
        val now = Instant.now()
        keys.forEach { key ->
            val list = failures.computeIfAbsent(key) { mutableListOf() }
            synchronized(list) { list.add(now) }
        }
        if (failures.size > 10_000) {
            val cutoff = now.minus(WINDOW)
            failures.entries.removeIf { (_, list) -> synchronized(list) { list.all { it.isBefore(cutoff) } } }
        }
    }

    fun reset(vararg keys: String) = keys.forEach { failures.remove(it) }

    private companion object {
        val WINDOW: Duration = Duration.ofMinutes(15)
        const val MAX_FAILURES = 10
    }
}
