package com.qfion.challenge.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AppConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // CloudFront forwards its own origin Host header, so Spring sees browser POSTs
        // from the public site as CORS requests even though they are same-origin to the user.
        registry.addMapping("/api/**")
                .allowedOrigins(
                        "https://challenge.dev.codereport.com",
                        "http://localhost:4200",
                        "http://127.0.0.1:4200",
                        "http://localhost:3000")
                .allowedMethods("GET", "POST", "OPTIONS");
    }
}
