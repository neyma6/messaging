package com.neyma.messagingService.config;

import com.neyma.messagingService.handler.ChatWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import org.springframework.web.reactive.socket.server.upgrade.ReactorNettyRequestUpgradeStrategy;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Arrays;

@Configuration
public class WebSocketConfig {

    @Bean
    public HandlerMapping webSocketHandlerMapping(ChatWebSocketHandler handler) {
        Map<String, WebSocketHandler> map = new HashMap<>();
        map.put("/ws", handler);

        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setOrder(1);
        mapping.setUrlMap(map);
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter(webSocketService());
    }

    @Bean
    public WebSocketService webSocketService() {
        // Create a custom HandshakeWebSocketService to handle subprotocols dynamic
        // echoing
        // and permissive CORS.
        ReactorNettyRequestUpgradeStrategy strategy = new ReactorNettyRequestUpgradeStrategy();

        return new HandshakeWebSocketService(strategy) {
            @Override
            public Mono<Void> handleRequest(ServerWebExchange exchange, WebSocketHandler handler) {
                // Extract requested subprotocols from header
                String protocolHeader = exchange.getRequest().getHeaders().getFirst("Sec-WebSocket-Protocol");

                // Proxy the handler to return the requested protocols, forcing the service to
                // accept them.
                WebSocketHandler proxyHandler = new WebSocketHandler() {
                    @Override
                    public Mono<Void> handle(WebSocketSession session) {
                        return handler.handle(session);
                    }

                    @Override
                    public List<String> getSubProtocols() {
                        if (protocolHeader != null && !protocolHeader.isEmpty()) {
                            // Split by comma if multiple protocols are requested
                            return Arrays.stream(protocolHeader.split(","))
                                    .map(String::trim)
                                    .collect(Collectors.toList());
                        }
                        return Collections.emptyList();
                    }
                };

                return super.handleRequest(exchange, proxyHandler);
            }
        };
    }
}
