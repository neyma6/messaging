package com.neyma.messagingService.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KafkaMessage {
    private UUID sender;
    private UUID receiver;
    private String message;
    private String receiverName;
    private LocalDateTime messageTime;
    private UUID messageId;
    private UUID chatId;
}
