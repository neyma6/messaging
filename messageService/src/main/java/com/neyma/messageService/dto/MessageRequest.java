package com.neyma.messageService.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class MessageRequest {
    private UUID chatId;
    private String messageSent;
    private UUID userId;
    private String messageContent;
}
