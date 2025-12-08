package com.neyma.chatHistoryService.repository;

import com.neyma.chatHistoryService.entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import java.util.UUID;

@Repository
public interface ChatRepository extends JpaRepository<Chat, UUID> {
    List<Chat> findAllByChatId(UUID chatId);

    List<Chat> findAllByUserId(UUID userId);
}
