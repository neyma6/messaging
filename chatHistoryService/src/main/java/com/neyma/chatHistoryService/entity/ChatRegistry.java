package com.neyma.chatHistoryService.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_registry", indexes = @Index(name = "idx_chat_registry_user_id", columnList = "userId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private java.util.UUID id;

    @Column(nullable = false)
    private java.util.UUID userId;

    @Column(nullable = false)
    private java.util.UUID chatId;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
