package com.neyma.serviceRegistryService.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    public void registerService(Long serviceId) {
        logger.info("Registering service with ID: {}", serviceId);
        // Add to the set of available services (no score/TTL here needed if we rely on
        // the separate key)
        redisTemplate.opsForSet().add(AVAILABLE_SERVICES_KEY, String.valueOf(serviceId));

        // Set a separate key for TTL to trigger expiration events
        String availabilityKey = "service_availability:" + serviceId;
        redisTemplate.opsForValue().set(availabilityKey, "active", 5, TimeUnit.SECONDS);
    }

    // refreshServiceCache is removed as there is no database to refresh from.
    // Initial population of AVAILABLE_SERVICES_KEY will now solely depend on
    // registerService calls.

    public Long getServiceId(Long userId) {
        String cacheKey = "user_service:" + userId;
        // 1. Check Redis for User Mapping
        String cachedServiceId = redisTemplate.opsForValue().get(cacheKey);
        if (cachedServiceId != null) {
            logger.debug("Found cached service ID {} for user {}", cachedServiceId, userId);
            return Long.parseLong(cachedServiceId);
        }

        // 2. Fallback: Assign Random Service from Redis
        Long randomServiceId = getRandomServiceId();
        logger.info("Assigned random service ID {} to user {}", randomServiceId, userId);

        // Caching the new mapping
        cacheServiceId(cacheKey, randomServiceId);

        // Add to reverse index to track users assigned to this service
        String reverseIndexKey = "service_users:" + randomServiceId;
        redisTemplate.opsForSet().add(reverseIndexKey, String.valueOf(userId));

        return randomServiceId;
    }

    private Long getRandomServiceId() {
        // Pick a random service from the SET
        String randomId = redisTemplate.opsForSet().randomMember(AVAILABLE_SERVICES_KEY);
        if (randomId == null) {
            logger.error("No available services found in registry");
            throw new RuntimeException("No services available to assign");
        }
        return Long.parseLong(randomId);
    }

    private void cacheServiceId(String key, Long serviceId) {
        redisTemplate.opsForValue().set(key, String.valueOf(serviceId));
    }

    public void handleServiceExpiration(Long serviceId) {
        logger.info("Handling expiration for service ID: {}", serviceId);
        // 1. Remove from available services set
        redisTemplate.opsForSet().remove(AVAILABLE_SERVICES_KEY, String.valueOf(serviceId));

        // 2. Get all users assigned to this service
        String reverseIndexKey = "service_users:" + serviceId;
        java.util.Set<String> userIds = redisTemplate.opsForSet().members(reverseIndexKey);

        if (userIds != null && !userIds.isEmpty()) {
            logger.info("Cleaning up {} users assigned to expired service {}", userIds.size(), serviceId);
            // 3. Delete user mappings
            for (String userId : userIds) {
                redisTemplate.delete("user_service:" + userId);
            }
        } else {
            logger.info("No users found assigned to expired service {}", serviceId);
        }

        // 4. Delete the reverse index itself
        redisTemplate.delete(reverseIndexKey);
    }
}
