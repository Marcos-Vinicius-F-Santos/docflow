package com.dockflow.dockflow.document.adapter.in.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Allows the separately hosted portfolio frontend to call the public API. */
@Configuration(proxyBeanMethods = false)
public class WebCorsConfiguration implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebCorsConfiguration(
        @Value("${docflow.web.cors.allowed-origins:http://localhost:4200}") String[] allowedOrigins
    ) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOrigins(allowedOrigins)
            .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .exposedHeaders(HttpHeaders.CONTENT_DISPOSITION, HttpHeaders.CONTENT_TYPE)
            .allowCredentials(false)
            .maxAge(3600);
    }
}
