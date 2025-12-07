package com.neyma.chatHistoryService.controller;

import com.neyma.chatHistoryService.dto.ChatParticipantsResponse;
import com.neyma.chatHistoryService.entity.Message;
import com.neyma.chatHistoryService.service.ChatHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/history")
@RequiredArgsConstructor
@Tag(name = "Chat History", description = "API for retrieving chat history and participants")
public class ChatHistoryController {

    private final ChatHistoryService chatHistoryService;

    @Operation(summary = "Get Chat ID", description = "Get common chat ID for two users, creating one if it doesn't exist")
    @GetMapping("/chat")
    public ResponseEntity<UUID> getChatId(
            @Parameter(description = "ID of the first user") @RequestParam UUID userId1,
            @Parameter(description = "ID of the second user") @RequestParam UUID userId2) {
        return ResponseEntity.ok(chatHistoryService.getChatId(userId1, userId2));
    }

    @Operation(summary = "Get Chat Participants", description = "Get list of user IDs participating in a chat")
    @GetMapping("/chat/{chatId}/participants")
    public ResponseEntity<ChatParticipantsResponse> getChatParticipants(
            @Parameter(description = "ID of the chat") @PathVariable UUID chatId) {
        return ResponseEntity.ok(chatHistoryService.getUserIdsForChat(chatId));
    }

    @Operation(summary = "Get Messages", description = "Get messages for a chat within a time range")
    @GetMapping("/messages")
    public ResponseEntity<List<Message>> getMessages(
            @Parameter(description = "ID of the chat") @RequestParam UUID chatId,
            @Parameter(description = "Start time (ISO-8601)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "End time (ISO-8601)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(chatHistoryService.getMessages(chatId, from, to));
    }

    @Operation(summary = "Add User to Chat", description = "Adds a user to a specific chat")
    @PostMapping("/chat/{chatId}/user/{userId}")
    public ResponseEntity<Void> addUserToChat(
            @Parameter(description = "ID of the chat") @PathVariable UUID chatId,
            @Parameter(description = "ID of the user") @PathVariable UUID userId) {
        chatHistoryService.addUserToChat(userId, chatId);
        return ResponseEntity.ok().build();
    }
}
