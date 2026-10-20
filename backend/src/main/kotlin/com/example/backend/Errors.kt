package com.example.backend

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException

/** Müştəriyə (Android tətbiqinə) göstəriləcək mesajla birlikdə atılan xəta. */
class ApiException(val status: HttpStatus, override val message: String) : RuntimeException(message)

data class ErrorResponse(val message: String)

@RestControllerAdvice
class ErrorHandler {
    @ExceptionHandler(ApiException::class)
    fun handleApi(e: ApiException) = ResponseEntity.status(e.status).body(ErrorResponse(e.message))

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable() = ResponseEntity.badRequest().body(ErrorResponse("Sorğunun formatı yanlışdır."))

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleTooLarge() = ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
        .body(ErrorResponse("Şəkil 5 MB-dan böyük ola bilməz."))
}

fun badRequest(message: String) = ApiException(HttpStatus.BAD_REQUEST, message)
fun notFound(message: String) = ApiException(HttpStatus.NOT_FOUND, message)
