package com.neyma.chatHistoryService.service;

import com.neyma.chatHistoryService.dto.ChatParticipantsResponse;
import com.neyma.chatHistoryService.entity.Chat;
import com.neyma.chatHistoryService.entity.ChatRegistry;
import com.neyma.chatHistoryService.entity.Message;
import com.neyma.chatHistoryService.repository.ChatRegistryRepository;
import com.neyma.chatHistoryService.repository.ChatRepository;
import com.neyma.chatHistoryService.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatHistoryService {

    private final ChatRegistryRepository chatRegistryRepository;
    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;

    @Transactional
    public UUID getChatId(UUID userId1, UUID userId2) {
        Optional<UUID> existingChatId = chatRegistryRepository.findCommonChatId(userId1, userId2);

        if (existingChatId.isPresent()) {
            return existingChatId.get();
        }

        UUID newChatId = UUID.randomUUID();

        // Save to ChatRegistry
        saveToRegistry(userId1, newChatId);
        saveToRegistry(userId2, newChatId);

        // Save to Chat
        saveToChat(newChatId, userId1);
        saveToChat(newChatId, userId2);

        return newChatId;
    }

    public ChatParticipantsResponse getUserIdsForChat(UUID chatId) {
        List<UUID> userIds = chatRepository.findAllByChatId(chatId)
                .stream()
                .map(Chat::getUserId)
                .collect(Collectors.toList());

        return new ChatParticipantsResponse(chatId, userIds);
    }

    public List<Message> getMessages(UUID chatId, LocalDateTime from, LocalDateTime to) {
        return messageRepository.findByChatIdAndMessageTimeBetween(chatId, from, to);
    }

    private void saveToRegistry(UUID userId, UUID chatId) {
        ChatRegistry registry = ChatRegistry.builder()
                .userId(userId)
                .chatId(chatId)
                // createdAt handled by @PrePersist
                .build();
        chatRegistryRepository.save(registry);
    }

    private void saveToChat(UUID chatId, UUID userId) {
        Chat chat = Chat.builder()
                .chatId(chatId)
                .userId(userId)
                // timestamp handled by @PrePersist
                .build();
        chatRepository.save(chat);
    }
}
