package com.neyma.chatHistoryService.repository;

import com.neyma.chatHistoryService.entity.ChatRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import java.util.UUID;

@Repository
public interface ChatRegistryRepository extends JpaRepository<ChatRegistry, UUID> {
    List<ChatRegistry> findAllByUserId(UUID userId);

    @org.springframework.data.jpa.repository.Query("SELECT c1.chatId FROM ChatRegistry c1 WHERE c1.userId = :userId1 AND c1.chatId IN (SELECT c2.chatId FROM ChatRegistry c2 WHERE c2.userId = :userId2)")
    java.util.Optional<UUID> findCommonChatId(@org.springframework.data.repository.query.Param("userId1") UUID userId1,
            @org.springframework.data.repository.query.Param("userId2") UUID userId2);
}
