package com.neyma.messageService.controller;

import com.neyma.messageService.dto.MessageRequest;
import com.neyma.messageService.entity.Message;
import com.neyma.messageService.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping
    public Mono<Message> sendMessage(@RequestBody MessageRequest request) {
        return messageService.processAndSaveMessage(request);
    }

    @DeleteMapping("/cache/{chatId}")
    public Mono<Void> clearCache(@PathVariable java.util.UUID chatId) {
        return messageService.clearChatParticipantsCache(chatId).then();
    }
}
