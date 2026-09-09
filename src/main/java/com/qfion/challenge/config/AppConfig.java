package com.qfion.challenge.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@org.springframework.scheduling.annotation.EnableScheduling
public class AppConfig implements WebMvcConfigurer {

    // Run admin CORS before authentication, including error responses and preflights.
    @Bean
    public FilterRegistrationBean<CorsFilter> adminCorsFilter() {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(java.util.List.of("https://challenge.dev.codereport.com",
                "http://localhost:4200", "http://127.0.0.1:4200", "http://localhost:3000"));
        cors.setAllowedMethods(java.util.List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(java.util.List.of("Authorization", "Content-Type"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/admin/**", cors);
        var registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setUrlPatterns(java.util.List.of("/api/v1/admin/*"));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

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
