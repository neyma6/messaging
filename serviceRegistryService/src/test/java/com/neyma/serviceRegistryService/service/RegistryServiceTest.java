package com.neyma.serviceRegistryService.service;

import com.neyma.serviceRegistryService.dto.ServiceAssignment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistryServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RegistryService registryService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // Mock execute to simulate alive instance
        lenient().when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(1L);
    }

    @Test
    void registerService_AddsToSetAndSetsAvailabilityKey() {
        UUID serviceId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        String address = "ws://localhost:8080";

        registryService.registerService(serviceId, address);

        verify(setOperations).add("available_services", serviceId.toString());
        verify(valueOperations).set("service_availability:" + serviceId, "active", 5, TimeUnit.SECONDS);
        verify(valueOperations).set("service_address:" + serviceId, address);
    }

    @Test
    void getServiceAssignment_ReturnsCachedServiceId() {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000456");
        UUID cachedId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        when(valueOperations.get("user_service:" + userId)).thenReturn(cachedId.toString());
        when(valueOperations.get("service_address:" + cachedId)).thenReturn("addr");

        ServiceAssignment result = registryService.getServiceAssignment(userId);

        assertEquals(cachedId, result.getServiceId());
        assertEquals("addr", result.getAddress());
        verify(setOperations, never()).randomMember(anyString());
    }

    @Test
    void getServiceAssignment_AssignsRandomServiceWhenNotCached() {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000456");
        UUID randomId = UUID.fromString("00000000-0000-0000-0000-000000000789");

        when(valueOperations.get("user_service:" + userId)).thenReturn(null);
        when(setOperations.randomMember("available_services")).thenReturn(randomId.toString());
        when(valueOperations.get("service_address:" + randomId)).thenReturn("addr");

        ServiceAssignment result = registryService.getServiceAssignment(userId);

        assertEquals(randomId, result.getServiceId());
        assertEquals("addr", result.getAddress());
        verify(valueOperations).set("user_service:" + userId, randomId.toString());
        verify(setOperations).add("service_users:" + randomId, userId.toString());
    }

    @Test
    void getServiceAssignment_ThrowsExceptionWhenNoServicesAvailable() {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000456");
        when(valueOperations.get("user_service:" + userId)).thenReturn(null);
        when(setOperations.randomMember("available_services")).thenReturn(null);

        assertThrows(RuntimeException.class, () -> registryService.getServiceAssignment(userId));
    }

    @Test
    void handleServiceExpiration_RemovesServiceAndCleansUpUsers() {
        UUID serviceId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        Set<String> userIds = Set.of("1", "2", "3");
        when(setOperations.members("service_users:" + serviceId)).thenReturn(userIds);

        registryService.handleServiceExpiration(serviceId);

        verify(setOperations).remove("available_services", serviceId.toString());
        verify(redisTemplate).delete("service_address:" + serviceId);
        verify(redisTemplate).delete("user_service:1");
        verify(redisTemplate).delete("user_service:2");
        verify(redisTemplate).delete("user_service:3");
        verify(redisTemplate).delete("service_users:" + serviceId);
    }
}
