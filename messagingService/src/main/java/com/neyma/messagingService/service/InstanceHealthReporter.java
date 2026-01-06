package com.neyma.messagingService.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class InstanceHealthReporter {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final java.util.UUID instanceId;

    private static final long HEARTBEAT_TTL_SECONDS = 5; // Short TTL for fast failure detection

    @Scheduled(fixedRate = 1000) // Run every 1 second
    public void reportHealth() {
        long timestamp = System.currentTimeMillis();
        String key = "service:alive:" + instanceId;

        org.slf4j.LoggerFactory.getLogger(InstanceHealthReporter.class)
                .debug("Sending heartbeat for instance {}:Timestamp={}", instanceId, timestamp);

        redisTemplate.opsForValue()
                .set(key, String.valueOf(timestamp), Duration.ofSeconds(HEARTBEAT_TTL_SECONDS))
                .subscribe(
                        success -> {
                        },
                        error -> org.slf4j.LoggerFactory.getLogger(InstanceHealthReporter.class)
                                .error("Failed to send heartbeat", error));
    }
}
