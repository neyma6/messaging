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
        String channel = "system:alive:" + instanceId;
        String scriptStr = "local status, result = pcall(redis.call, 'PUBSUB', 'NUMSUB', KEYS[1]); " +
                "if status then return result[2] else return -1 end";

        org.springframework.data.redis.core.script.DefaultRedisScript<Object> redisScript = new org.springframework.data.redis.core.script.DefaultRedisScript<>(
                scriptStr, Object.class);

        try {
            Object result = redisTemplate.execute(redisScript, java.util.Collections.singletonList(channel));

            if (result instanceof Number) {
                long val = ((Number) result).longValue();
                if (val == -1) {
                    logger.error("PUBSUB command not allowed in Lua script.");
                    return true; // Fallback to alive
                }
                return val > 0;
            }

            logger.warn("Unexpected Lua result type: {}", result != null ? result.getClass().getName() : "null");
            return false;
        } catch (Exception e) {
            logger.error("Lua script execution failed", e);
            // Fallback to alive on error to avoid outage during Redis issues?
            // Or dead?
            // If we can't verify, assuming alive (random assignment) is risky but better
            // than 500 loop?
            // But getServiceAssignment retries 10 times. If all 'failed', it throws 500.
            // If script fails, we should distinct.
            return true; // Fallback
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
