package com.neyma.messagingService.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neyma.messagingService.dto.MessageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler implements WebSocketHandler {

    private final ReactiveRedisMessageListenerContainer redisListenerContainer;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${message.service.url:http://message-service:8080}")
    private String messageServiceUrl;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        UUID userId = extractUserId(session);
        if (userId == null) {
            return session.close();
        }

        String channel = "inbox:user:" + userId;

        // Output: Redis -> WebSocket
        Mono<Void> output = session.send(
                redisListenerContainer.receive(ChannelTopic.of(channel))
                        .map(p -> session.textMessage(p.getMessage())));

        // Input: WebSocket -> MessageRequest -> MessageService
        Mono<Void> input = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(payload -> {
                    try {
                        MessageRequest req = objectMapper.readValue(payload, MessageRequest.class);
                        // Security override (optional but recommended)
                        req.setUserId(userId);

                        return webClientBuilder.build()
                                .post()
                                .uri(messageServiceUrl + "/messages")
                                .bodyValue(req)
                                .retrieve()
                                .bodyToMono(Void.class)
                                .onErrorResume(e -> {
                                    System.err.println("Error forwarding message to MessageService: " + e.getMessage());
                                    return Mono.empty();
                                });
                    } catch (Exception e) {
                        System.err.println("Invalid JSON received: " + e.getMessage());
                        return Mono.empty();
                    }
                })
                .then();

        return Mono.zip(input, output).then();
    }

    private UUID extractUserId(WebSocketSession session) {
        try {
            String query = session.getHandshakeInfo().getUri().getQuery();
            if (query != null && query.contains("userId=")) {
                for (String param : query.split("&")) {
                    if (param.startsWith("userId=")) {
                        return UUID.fromString(param.split("=")[1]);
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }
}
