package com.neyma.messageDispatcher.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KafkaConsumerService {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "message", groupId = "message-dispatcher-group")
    public void listen(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            if (node.has("receiver")) {
                String receiverId = node.get("receiver").asText();
                String channel = "inbox:user:" + receiverId;

                redisTemplate.convertAndSend(channel, message)
                        .doOnNext(count -> {
                            if (count == 0) {
                                System.out.println("User offline (No active session): " + receiverId);
                            } else {
                                System.out.println("Message delivered to user: " + receiverId);
                            }
                        })
                        .subscribe();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
