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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.SignalType;

import java.util.UUID;

@Component
public class ChatWebSocketHandler implements WebSocketHandler {

    private final ReactiveRedisMessageListenerContainer redisListenerContainer;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    public ChatWebSocketHandler(
            ReactiveRedisMessageListenerContainer redisListenerContainer,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${message.service.url:http://message-service:8080}") String messageServiceUrl) {
        this.redisListenerContainer = redisListenerContainer;
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder.baseUrl(messageServiceUrl).build();
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        UUID userId = extractUserId(session);
        if (userId == null) {
            return session.close();
        }

        logger.info("User {} connected", userId);

        String channel = "inbox:user:" + userId;

        // Input: WebSocket -> MessageRequest -> MessageService
        // This stream completes when the WebSocket connection is closed by the client.
        // Create a sink to signal when the input stream (client connection) ends
        reactor.core.publisher.Sinks.Empty<Void> completionSignal = reactor.core.publisher.Sinks.empty();

        // Input: WebSocket -> MessageRequest -> MessageService
        // This stream completes when the WebSocket connection is closed by the client.
        Mono<Void> input = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(payload -> {
                    try {
                        MessageRequest req = objectMapper.readValue(payload, MessageRequest.class);
                        // Security override (optional but recommended)
                        req.setUserId(userId);

                        return webClient.post()
                                .uri("/messages")
                                .bodyValue(req)
                                .retrieve()
                                .bodyToMono(Void.class)
                                .onErrorResume(e -> {
                                    logger.error("Error forwarding message to MessageService: {}", e.getMessage());
                                    // Notify user of failure
                                    String errorMessage = "{\"error\": \"Failed to send\"}";
                                    return session.send(Mono.just(session.textMessage(errorMessage))).then();
                                });
                    } catch (Exception e) {
                        logger.error("Invalid JSON received: {}", e.getMessage());
                        return Mono.empty();
                    }
                })
                .doFinally(signal -> {
                    if (signal == SignalType.ON_COMPLETE || signal == SignalType.CANCEL
                            || signal == SignalType.ON_ERROR) {
                        logger.info("User {} disconnected (Signal: {})", userId, signal);
                        completionSignal.tryEmitEmpty();
                    }
                })
                .then();

        // Output: Redis -> WebSocket
        // We use takeUntilOther(completionSignal.asMono()) so we don't double-subscribe
        // to 'session.receive()'
        Mono<Void> output = session.send(
                redisListenerContainer.receive(ChannelTopic.of(channel))
                        .map(p -> session.textMessage(p.getMessage()))
                        .takeUntilOther(completionSignal.asMono()));

        return Mono.zip(input, output).then();
    }

    private UUID extractUserId(WebSocketSession session) {
        try {
            // Check Sec-WebSocket-Protocol header
            java.util.List<String> protocols = session.getHandshakeInfo().getHeaders().get("Sec-WebSocket-Protocol");
            if (protocols != null && !protocols.isEmpty()) {
                // Browser might send "protocol, other" or just "protocol"
                // We expect userId as one of the protocols.
                // Since UUID has hyphens, it's a valid protocol string token usually.
                String protocolHeader = protocols.get(0);
                for (String token : protocolHeader.split(",")) {
                    String trimmed = token.trim();
                    try {
                        return UUID.fromString(trimmed);
                    } catch (IllegalArgumentException e) {
                        // Not a UUID, try next
                    }
                }
            }

            // Fallback to query (optional, maybe remove for strict security?)
            // Keeping it for backward compatibility or direct testing if needed,
            // but the goal is to use header. Let's remove query param check for security as
            // requested.
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
