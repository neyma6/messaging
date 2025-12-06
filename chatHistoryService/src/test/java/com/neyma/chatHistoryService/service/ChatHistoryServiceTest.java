package com.neyma.chatHistoryService.service;

import com.neyma.chatHistoryService.dto.ChatParticipantsResponse;
import com.neyma.chatHistoryService.entity.Chat;
import com.neyma.chatHistoryService.entity.ChatRegistry;
import com.neyma.chatHistoryService.entity.Message;
import com.neyma.chatHistoryService.repository.ChatRegistryRepository;
import com.neyma.chatHistoryService.repository.ChatRepository;
import com.neyma.chatHistoryService.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatHistoryServiceTest {

    @Mock
    private ChatRegistryRepository chatRegistryRepository;

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private MessageRepository messageRepository;

    @InjectMocks
    private ChatHistoryService chatHistoryService;

    private UUID userId1;
    private UUID userId2;
    private UUID chatId;

    @BeforeEach
    void setUp() {
        userId1 = UUID.randomUUID();
        userId2 = UUID.randomUUID();
        chatId = UUID.randomUUID();
    }

    @Test
    void getChatId_ReturnsExistingChatId() {
        when(chatRegistryRepository.findCommonChatId(userId1, userId2)).thenReturn(Optional.of(chatId));

        UUID result = chatHistoryService.getChatId(userId1, userId2);

        assertEquals(chatId, result);
        verify(chatRegistryRepository, never()).save(any());
        verify(chatRepository, never()).save(any());
    }

    @Test
    void getChatId_CreatesNewChatWhenNoneExists() {
        when(chatRegistryRepository.findCommonChatId(userId1, userId2)).thenReturn(Optional.empty());

        UUID result = chatHistoryService.getChatId(userId1, userId2);

        assertNotNull(result);
        verify(chatRegistryRepository, times(2)).save(any(ChatRegistry.class));
        verify(chatRepository, times(2)).save(any(Chat.class));
    }

    @Test
    void getUserIdsForChat_ReturnsParticipants() {
        Chat chat1 = Chat.builder().chatId(chatId).userId(userId1).build();
        Chat chat2 = Chat.builder().chatId(chatId).userId(userId2).build();
        when(chatRepository.findAllByChatId(chatId)).thenReturn(List.of(chat1, chat2));

        ChatParticipantsResponse response = chatHistoryService.getUserIdsForChat(chatId);

        assertEquals(chatId, response.getChatId());
        assertEquals(2, response.getUserIds().size());
        assertTrue(response.getUserIds().contains(userId1));
        assertTrue(response.getUserIds().contains(userId2));
    }

    @Test
    void getUserIdsForChat_ReturnsEmptyListForNonExistentChat() {
        when(chatRepository.findAllByChatId(chatId)).thenReturn(List.of());

        ChatParticipantsResponse response = chatHistoryService.getUserIdsForChat(chatId);

        assertEquals(chatId, response.getChatId());
        assertTrue(response.getUserIds().isEmpty());
    }

    @Test
    void getMessages_ReturnsMessagesFromTimeRange() {
        LocalDateTime messageTime = LocalDateTime.now().minusHours(1);
        Message message1 = Message.builder()
                .chatId(chatId)
                .userId(userId1)
                .messageContent("Hello")
                .messageTime(LocalDateTime.now())
                .build();
        Message message2 = Message.builder()
                .chatId(chatId)
                .userId(userId2)
                .messageContent("Hi there")
                .messageTime(LocalDateTime.now())
                .build();

        when(messageRepository.findByChatIdAndMessageTimeGreaterThanEqual(chatId, messageTime))
                .thenReturn(List.of(message1, message2));

        List<Message> result = chatHistoryService.getMessages(chatId, messageTime);

        assertEquals(2, result.size());
        verify(messageRepository).findByChatIdAndMessageTimeGreaterThanEqual(chatId, messageTime);
    }

    @Test
    void getMessages_ReturnsEmptyListWhenNoMessages() {
        LocalDateTime messageTime = LocalDateTime.now();
        when(messageRepository.findByChatIdAndMessageTimeGreaterThanEqual(chatId, messageTime))
                .thenReturn(List.of());

        List<Message> result = chatHistoryService.getMessages(chatId, messageTime);

        assertTrue(result.isEmpty());
    }
}
