package com.neyma.serviceRegistryService.listener;

import com.neyma.serviceRegistryService.service.RegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

@Component
public class ServiceExpirationListener extends KeyExpirationEventMessageListener {

    private static final Logger logger = LoggerFactory.getLogger(ServiceExpirationListener.class);
    private final RegistryService registryService;

    public ServiceExpirationListener(RedisMessageListenerContainer listenerContainer, RegistryService registryService) {
        super(listenerContainer);
        this.registryService = registryService;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = message.toString();
        if (expiredKey.startsWith("service_availability:")) {
            String serviceIdStr = expiredKey.split(":")[1];
            try {
                Long serviceId = Long.parseLong(serviceIdStr);
                logger.info("Service expired: {}", serviceId);
                registryService.handleServiceExpiration(serviceId);
            } catch (NumberFormatException e) {
                logger.warn("Failed to parse service ID from key: {}", expiredKey);
            }
        }
    }
}
