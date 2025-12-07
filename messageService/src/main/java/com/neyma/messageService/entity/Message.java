package com.neyma.messageService.entity;

import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @PrimaryKeyColumn(name = "chat_id", type = PrimaryKeyType.PARTITIONED)
    private UUID chatId;

    @PrimaryKeyColumn(name = "message_time", ordinal = 0, type = PrimaryKeyType.CLUSTERED)
    private LocalDateTime messageTime;

    @Builder.Default
    @PrimaryKeyColumn(name = "message_id", ordinal = 1, type = PrimaryKeyType.CLUSTERED)
    private UUID messageId = UUID.randomUUID();

    @Column("user_id")
    private UUID userId;

    @Column("message_content")
    private String messageContent;

    @Column("message_sent")
    private String messageSent;

    public Message(UUID chatId, UUID userId, String messageContent, String messageSent) {
        this.chatId = chatId;
        this.userId = userId;
        this.messageContent = messageContent;
        this.messageSent = messageSent;
        this.messageTime = LocalDateTime.now();
        this.messageId = UUID.randomUUID();
    }
}
