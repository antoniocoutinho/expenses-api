package com.expensesapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI expensesAppOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Expenses App API")
                        .description("Backend API for tracking personal income and expenses.")
                        .version("v0.1.0"));
    }
}
