package com.neyma.messageService.service;

import com.neyma.messageService.entity.Message;
import com.neyma.messageService.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;

    public Mono<Message> saveMessage(Message message) {
        if (message.getMessageId() == null) {
            message.setMessageId(UUID.randomUUID());
        }
        if (message.getMessageTime() == null) {
            message.setMessageTime(LocalDateTime.now());
        }
        return messageRepository.save(message);
    }
}
