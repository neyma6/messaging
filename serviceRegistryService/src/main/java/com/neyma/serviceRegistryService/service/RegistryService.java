package com.neyma.serviceRegistryService.service;

import com.neyma.serviceRegistryService.dto.ServiceAssignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisPubSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;

@org.springframework.stereotype.Service
public class RegistryService {

    private static final Logger logger = LoggerFactory.getLogger(RegistryService.class);

    private final StringRedisTemplate redisTemplate;

    @Autowired
    public RegistryService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private static final String AVAILABLE_SERVICES_KEY = "available_services";

    public void registerService(UUID serviceId, String address) {
        logger.info("Registering service with ID: {} at address: {}", serviceId, address);
        redisTemplate.opsForSet().add(AVAILABLE_SERVICES_KEY, serviceId.toString());
        // Save address
        redisTemplate.opsForValue().set("service_address:" + serviceId, address);
    }

    public ServiceAssignment getServiceAssignment(UUID userId) {
        String cacheKey = "user_service:" + userId;
        String cachedServiceId = redisTemplate.opsForValue().get(cacheKey);

        UUID serviceId = null;
        if (cachedServiceId != null) {
            UUID cachedUuid = UUID.fromString(cachedServiceId);
            if (isInstanceAlive(cachedUuid)) {
                logger.debug("Found cached and alive service ID {} for user {}", cachedUuid, userId);
                serviceId = cachedUuid;
            } else {
                logger.warn("Cached service {} is dead. Re-assigning.", cachedUuid);
                handleServiceExpiration(cachedUuid);
            }
        }

        if (serviceId == null) {
            serviceId = getRandomServiceId();
            logger.info("Assigned random service ID {} to user {}", serviceId, userId);

            cacheServiceId(cacheKey, serviceId);

            String reverseIndexKey = "service_users:" + serviceId;
            redisTemplate.opsForSet().add(reverseIndexKey, userId.toString());
        }

        String address = redisTemplate.opsForValue().get("service_address:" + serviceId);
        return new ServiceAssignment(serviceId, address);
    }

    private UUID getRandomServiceId() {
        for (int i = 0; i < 10; i++) {
            String randomId = redisTemplate.opsForSet().randomMember(AVAILABLE_SERVICES_KEY);
            if (randomId == null) {
                logger.error("No available services found in registry");
                throw new RuntimeException("No services available to assign");
            }

            UUID serviceId = UUID.fromString(randomId);
            if (isInstanceAlive(serviceId)) {
                return serviceId;
            } else {
                logger.warn("Randomly picked service {} is dead. removing...", serviceId);
                handleServiceExpiration(serviceId);
            }
        }
        throw new RuntimeException("No alive services found after multiple attempts");
    }

    private boolean isInstanceAlive(UUID instanceId) {
        String key = "service:alive:" + instanceId;
        String timestampStr = redisTemplate.opsForValue().get(key);

        if (timestampStr == null) {
            logger.warn("No heartbeat found for instance: {}", instanceId);
            return false;
        }

        try {
            long lastHeartbeat = Long.parseLong(timestampStr);
            long now = System.currentTimeMillis();
            // Threshold: 1.5 seconds (1500ms)
            long threshold = 1500;

            if (now - lastHeartbeat > threshold) {
                logger.warn("Instance {} is dead. Last heartbeat: {}, Now: {}", instanceId, lastHeartbeat, now);
                return false;
            }

            return true;
        } catch (NumberFormatException e) {
            logger.error("Invalid timestamp for instance {}: {}", instanceId, timestampStr);
            return false;
        }
    }

    private void cacheServiceId(String key, UUID serviceId) {
        redisTemplate.opsForValue().set(key, serviceId.toString());
    }

    public void handleServiceExpiration(UUID serviceId) {
        logger.info("Handling expiration for service ID: {}", serviceId);
        redisTemplate.opsForSet().remove(AVAILABLE_SERVICES_KEY, serviceId.toString());
        redisTemplate.delete("service_address:" + serviceId);

        String reverseIndexKey = "service_users:" + serviceId;
        Set<String> userIds = redisTemplate.opsForSet().members(reverseIndexKey);

        if (userIds != null && !userIds.isEmpty()) {
            logger.info("Cleaning up {} users assigned to expired service {}", userIds.size(), serviceId);
            for (String userId : userIds) {
                redisTemplate.delete("user_service:" + userId);
            }
        } else {
            logger.info("No users found assigned to expired service {}", serviceId);
        }

        redisTemplate.delete(reverseIndexKey);
    }
}
