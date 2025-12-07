package com.neyma.chatHistoryService.repository;

import com.neyma.chatHistoryService.entity.Message;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends CassandraRepository<Message, UUID> {
    List<Message> findByChatId(UUID chatId);

    List<Message> findByChatIdAndMessageTimeBetween(UUID chatId, java.time.LocalDateTime from,
            java.time.LocalDateTime to);
}
