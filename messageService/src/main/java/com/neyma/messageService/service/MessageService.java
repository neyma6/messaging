package com.neyma.messageService.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neyma.messageService.client.api.ChatHistoryApi;
import com.neyma.messageService.dto.KafkaMessage;
import com.neyma.messageService.dto.MessageRequest;
import com.neyma.messageService.entity.Message;
import com.neyma.messageService.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChatHistoryApi chatHistoryApi;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, KafkaMessage> kafkaTemplate;

    public Mono<Message> processAndSaveMessage(MessageRequest request) {
        return getChatParticipantsJson(request.getChatId())
                .flatMap(participantsJson -> createAndSaveMessage(request)
                        .flatMap(savedMessage -> broadcastToKafka(participantsJson, savedMessage)));
    }

    private Mono<String> getChatParticipantsJson(UUID chatId) {
        String cacheKey = chatId.toString();
        return redisTemplate.opsForValue().get(cacheKey)
                .switchIfEmpty(fetchAndCacheParticipants(chatId, cacheKey));
    }

    private Mono<String> fetchAndCacheParticipants(UUID chatId, String cacheKey) {
        return chatHistoryApi.getChatParticipants(chatId)
                .flatMap(response -> {
                    try {
                        String json = objectMapper.writeValueAsString(response.getUserIds());
                        return redisTemplate.opsForValue().set(cacheKey, json, java.time.Duration.ofMinutes(30))
                                .thenReturn(json);
                    } catch (JsonProcessingException e) {
                        return Mono.error(new RuntimeException("Error serializing user IDs", e));
                    }
                });
    }

    private Mono<Message> createAndSaveMessage(MessageRequest request) {
        Message message = new Message(
                request.getChatId(),
                request.getUserId(),
                request.getMessageContent(),
                request.getMessageSent());

        return saveMessage(message);
    }

    private Mono<Message> broadcastToKafka(String userIdsJson, Message savedMessage) {
        try {
            List<UUID> userIds = objectMapper.readValue(userIdsJson, new TypeReference<List<UUID>>() {
            });
            return Flux.fromIterable(userIds)
                    .flatMap(receiverId -> sendKafkaMessage(savedMessage, receiverId))
                    .then(Mono.just(savedMessage));
        } catch (JsonProcessingException e) {
            return Mono.error(new RuntimeException("Error deserializing user IDs", e));
        }
    }

    private Mono<Void> sendKafkaMessage(Message savedMessage, UUID receiverId) {
        KafkaMessage kafkaMessage = KafkaMessage.builder()
                .sender(savedMessage.getUserId())
                .receiver(receiverId)
                .message(savedMessage.getMessageContent())
                .receiverName(savedMessage.getMessageSent())
                .messageTime(savedMessage.getMessageTime())
                .messageId(savedMessage.getMessageId())
                .chatId(savedMessage.getChatId())
                .build();
        return Mono.fromFuture(kafkaTemplate.send("message", kafkaMessage)).then();
    }

    public Mono<Message> saveMessage(Message message) {
        if (message.getMessageId() == null) {
            message.setMessageId(UUID.randomUUID());
        }
        if (message.getMessageTime() == null) {
            message.setMessageTime(LocalDateTime.now());
        }
        return messageRepository.save(message);
    }

    public Mono<Boolean> clearChatParticipantsCache(UUID chatId) {
        return redisTemplate.delete(chatId.toString())
                .map(count -> count > 0);
    }
}
