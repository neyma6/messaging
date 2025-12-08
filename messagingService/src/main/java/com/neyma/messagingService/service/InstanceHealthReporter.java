package com.neyma.messagingService.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InstanceHealthReporter {

    private final ReactiveRedisMessageListenerContainer container;

    private final java.util.UUID instanceId;

    @PostConstruct
    public void init() {
        // Subscribe to a unique channel to indicate liveness via PUBSUB NUMSUB
        container.receive(ChannelTopic.of("system:alive:" + instanceId))
                .subscribe(); // We don't expect messages here, just maintaining the subscription
    }
}
