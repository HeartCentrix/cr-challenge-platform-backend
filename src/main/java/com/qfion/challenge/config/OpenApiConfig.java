package com.qfion.challenge.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI challengePlatformOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Challenge Platform API")
                        .description("API for coding questions, code execution, submissions, statistics, and the leaderboard.")
                        .version("v1")
                        .contact(new Contact().name("Code Report"))
                        .license(new License().name("Proprietary")))
                .servers(List.of(new Server()
                        .url("/")
                        .description("Current environment")));
    }
}
