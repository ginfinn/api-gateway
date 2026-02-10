package com.bank.apigateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Программная конфигурация маршрутов (альтернатива application.yml).
 * Маршруты можно дублировать/переопределять в YAML.
 */
@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // Пример: маршрут к operations-service
                .route("operations-service", r -> r
                        .path("/api/v1/operations/**")
                        .filters(f -> f
                                .stripPrefix(2)
                                .addRequestHeader("X-Gateway-Route", "operations")
                        )
                        .uri("http://localhost:8081")
                )
                // Пример: маршрут к documents-service
                .route("documents-service", r -> r
                        .path("/api/v1/documents/**")
                        .filters(f -> f
                                .stripPrefix(2)
                                .addRequestHeader("X-Gateway-Route", "documents")
                        )
                        .uri("http://localhost:8082")
                )
                .build();
    }
}
