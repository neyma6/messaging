package com.neyma.messageService.config;

import com.neyma.messageService.client.ApiClient;
import com.neyma.messageService.client.api.ChatHistoryApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
public class ClientConfig {

    @Value("${API_GATEWAY_URL:http://api-gateway:8080}")
    private String apiGatewayUrl;

    @Bean
    public ApiClient apiClient(WebClient.Builder builder) {
        WebClient webClient = builder
                .filter(bearerTokenFilter())
                .build();
        return new ApiClient(webClient).setBasePath(apiGatewayUrl);
    }

    private ExchangeFilterFunction bearerTokenFilter() {
        return (request, next) -> Mono.deferContextual(ctx -> {
            if (ctx.hasKey(ServerWebExchange.class)) {
                ServerWebExchange exchange = ctx.get(ServerWebExchange.class);
                String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    ClientRequest newRequest = ClientRequest.from(request)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .build();
                    return next.exchange(newRequest);
                }
            }
            return next.exchange(request);
        });
    }

    @Bean
    public ChatHistoryApi chatHistoryApi(ApiClient apiClient) {
        return new ChatHistoryApi(apiClient);
    }
}
