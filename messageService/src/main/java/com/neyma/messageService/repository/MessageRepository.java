package com.neyma.messageService.repository;

import com.neyma.messageService.entity.Message;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MessageRepository extends ReactiveCassandraRepository<Message, UUID> {
}
