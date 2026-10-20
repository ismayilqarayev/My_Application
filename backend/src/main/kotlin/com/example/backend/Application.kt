package com.example.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.file.Files
import java.nio.file.Path

@SpringBootApplication
@ConfigurationPropertiesScan
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val jwtSecret: String,
    val tokenValidityDays: Long,
    val adminPassword: String,
    val uploadDir: String
) {
    val uploadPath: Path get() = Path.of(uploadDir).toAbsolutePath().normalize()
}

/** 1-3-cü sınaqlar pulsuzdur, qalanları ödənişdən sonra açılır. */
const val FREE_CATEGORY_COUNT = 3

/**
 * Yüklənmiş sual şəkillərini "/uploads/<fayl>" ünvanında, brauzer admin panelini
 * isə "/admin" ünvanında (resources/static/admin/) paylayır.
 */
@Configuration
class WebConfig(private val props: AppProperties) : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        Files.createDirectories(props.uploadPath)
        registry.addResourceHandler("/uploads/**")
            .addResourceLocations(props.uploadPath.toUri().toString())
    }

    override fun addViewControllers(registry: ViewControllerRegistry) {
        registry.addRedirectViewController("/admin", "/admin/")
        registry.addViewController("/admin/").setViewName("forward:/admin/index.html")
    }
}
