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
        String channel = "system:alive:" + instanceId;
        byte[] channelBytes = channel.getBytes(StandardCharsets.UTF_8);

        try {
            Long subscribers = redisTemplate
                    .execute((org.springframework.data.redis.core.RedisCallback<Long>) connection -> {
                        Object nativeConnection = connection.getNativeConnection();
                        java.util.Map<Object, Long> results = null;

                        try {
                            // Check for Lettuce driver (Sync)
                            if (nativeConnection instanceof io.lettuce.core.api.sync.RedisCommands) {
                                @SuppressWarnings("unchecked")
                                io.lettuce.core.api.sync.RedisCommands<Object, Object> commands = (io.lettuce.core.api.sync.RedisCommands<Object, Object>) nativeConnection;
                                results = commands.pubsubNumsub(channelBytes);
                            }
                            // Check for Lettuce driver (Async)
                            else if (nativeConnection instanceof io.lettuce.core.api.async.RedisAsyncCommands) {
                                @SuppressWarnings("unchecked")
                                io.lettuce.core.api.async.RedisAsyncCommands<Object, Object> commands = (io.lettuce.core.api.async.RedisAsyncCommands<Object, Object>) nativeConnection;
                                // Block for result
                                results = commands.pubsubNumsub(channelBytes).get();
                            }
                            // Check for Cluster (Sync)
                            else if (nativeConnection instanceof io.lettuce.core.cluster.api.sync.RedisClusterCommands) {
                                @SuppressWarnings("unchecked")
                                io.lettuce.core.cluster.api.sync.RedisClusterCommands<Object, Object> commands = (io.lettuce.core.cluster.api.sync.RedisClusterCommands<Object, Object>) nativeConnection;
                                results = commands.pubsubNumsub(channelBytes);
                            }
                            // Check for Cluster (Async)
                            else if (nativeConnection instanceof io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands) {
                                @SuppressWarnings("unchecked")
                                io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands<Object, Object> commands = (io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands<Object, Object>) nativeConnection;
                                results = commands.pubsubNumsub(channelBytes).get();
                            } else {
                                logger.error("Unknown Redis connection type: {}",
                                        nativeConnection.getClass().getName());
                                return 0L;
                            }
                        } catch (Exception e) {
                            logger.error("Error executing native Redis command", e);
                            return 0L;
                        }

                        if (results == null)
                            return 0L;

                        if (logger.isDebugEnabled()) {
                            logger.debug("PUBSUB NUMSUB results: {}", results);
                        }

                        for (java.util.Map.Entry<Object, Long> entry : results.entrySet()) {
                            Object key = entry.getKey();
                            String keyStr = null;
                            if (key instanceof String) {
                                keyStr = (String) key;
                            } else if (key instanceof byte[]) {
                                keyStr = new String((byte[]) key, StandardCharsets.UTF_8);
                            }

                            if (channel.equals(keyStr)) {
                                return entry.getValue();
                            }
                        }
                        return 0L; // Fallback if key not found
                    });
            return subscribers != null && subscribers > 0;
        } catch (Exception e) {
            logger.error("Failed to check if instance {} is alive", instanceId, e);
            // Assume alive to maintain service availability during transient errors
            return true;
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
