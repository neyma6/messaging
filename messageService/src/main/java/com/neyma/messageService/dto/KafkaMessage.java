package com.neyma.messageService.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class KafkaMessage {
    private UUID sender;
    private UUID receiver;
    private String message;
    private String receiverName;
    private LocalDateTime messageTime;
    private UUID messageId;
    private UUID chatId;
}
