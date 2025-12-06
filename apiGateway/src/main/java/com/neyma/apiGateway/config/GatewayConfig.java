package com.neyma.apiGateway.config;

import com.neyma.apiGateway.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;

import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Configuration
public class GatewayConfig {

    @Value("${USER_SERVICE_URL:http://user-service:8080}")
    private String userServiceUrl;

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder, JwtUtil jwtUtil) {
        return builder.routes()
                .route("user-login-rewrite", r -> r.path("/users/login")
                        .and().method(HttpMethod.POST)
                        .filters(f -> f.modifyResponseBody(Map.class, Map.class, (exchange, responseBody) -> {
                            if (exchange.getResponse().getStatusCode() != null
                                    && exchange.getResponse().getStatusCode().is2xxSuccessful()
                                    && responseBody != null) {
                                try {
                                    String userId = (String) responseBody.get("id");
                                    if (userId != null) {
                                        String token = jwtUtil.generateToken(userId);
                                        String refreshToken = jwtUtil.generateRefreshToken(userId);
                                        responseBody.put("token", token);
                                        responseBody.put("refreshToken", refreshToken);
                                    }
                                } catch (Exception e) {
                                    // ignore
                                }
                            }
                            return Mono.justOrEmpty(responseBody);
                        }))
                        .uri(userServiceUrl))
                .route("user-register-rewrite", r -> r.path("/users/register")
                        .and().method(HttpMethod.POST)
                        .filters(f -> f.modifyResponseBody(Map.class, Map.class, (exchange, responseBody) -> {
                            if (exchange.getResponse().getStatusCode() != null
                                    && exchange.getResponse().getStatusCode().is2xxSuccessful()
                                    && responseBody != null) {
                                try {
                                    String userId = (String) responseBody.get("id");
                                    if (userId != null) {
                                        String token = jwtUtil.generateToken(userId);
                                        String refreshToken = jwtUtil.generateRefreshToken(userId);
                                        responseBody.put("token", token);
                                        responseBody.put("refreshToken", refreshToken);
                                    }
                                } catch (Exception e) {
                                    // ignore
                                }
                            }
                            return Mono.justOrEmpty(responseBody);
                        }))
                        .uri(userServiceUrl))
                .build();
    }
}
