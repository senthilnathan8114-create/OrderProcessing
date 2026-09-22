package com.hackathon.order.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderProcessingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Order Processing API")
                        .description("High-throughput order processing platform with pessimistic locking, retry logic, and dead-letter queue.")
                        .version("1.0.0")
                        .contact(new Contact().name("Hackathon Team"))
                        .license(new License().name("MIT")));
    }
}
