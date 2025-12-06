package com.neyma.serviceRegistryService.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Set;
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
    }

    @Test
    void registerService_AddsToSetAndSetsAvailabilityKey() {
        Long serviceId = 123L;

        registryService.registerService(serviceId);

        verify(setOperations).add("available_services", "123");
        verify(valueOperations).set("service_availability:123", "active", 5, TimeUnit.SECONDS);
    }

    @Test
    void getServiceId_ReturnsCachedServiceId() {
        Long userId = 456L;
        when(valueOperations.get("user_service:456")).thenReturn("123");

        Long result = registryService.getServiceId(userId);

        assertEquals(123L, result);
        verify(setOperations, never()).randomMember(anyString());
    }

    @Test
    void getServiceId_AssignsRandomServiceWhenNotCached() {
        Long userId = 456L;
        when(valueOperations.get("user_service:456")).thenReturn(null);
        when(setOperations.randomMember("available_services")).thenReturn("789");

        Long result = registryService.getServiceId(userId);

        assertEquals(789L, result);
        verify(valueOperations).set("user_service:456", "789");
        verify(setOperations).add("service_users:789", "456");
    }

    @Test
    void getServiceId_ThrowsExceptionWhenNoServicesAvailable() {
        Long userId = 456L;
        when(valueOperations.get("user_service:456")).thenReturn(null);
        when(setOperations.randomMember("available_services")).thenReturn(null);

        assertThrows(RuntimeException.class, () -> registryService.getServiceId(userId));
    }

    @Test
    void handleServiceExpiration_RemovesServiceAndCleansUpUsers() {
        Long serviceId = 123L;
        Set<String> userIds = Set.of("1", "2", "3");
        when(setOperations.members("service_users:123")).thenReturn(userIds);

        registryService.handleServiceExpiration(serviceId);

        verify(setOperations).remove("available_services", "123");
        verify(redisTemplate).delete("user_service:1");
        verify(redisTemplate).delete("user_service:2");
        verify(redisTemplate).delete("user_service:3");
        verify(redisTemplate).delete("service_users:123");
    }

    @Test
    void handleServiceExpiration_NoUsersToCleanUp() {
        Long serviceId = 123L;
        when(setOperations.members("service_users:123")).thenReturn(Set.of());

        registryService.handleServiceExpiration(serviceId);

        verify(setOperations).remove("available_services", "123");
        verify(redisTemplate).delete("service_users:123");
        verify(redisTemplate, never()).delete(startsWith("user_service:"));
    }
}
