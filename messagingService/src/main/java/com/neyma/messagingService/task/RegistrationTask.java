package com.neyma.messagingService.task;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.net.URI;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RegistrationTask {

        private final WebClient.Builder webClientBuilder;

        private final java.util.UUID instanceId;

        @Value("${SERVICE_REGISTRY_URL:http://service-registry-service:8080}")
        private String serviceRegistryUrl;

        @Value("${messaging.client.url:ws://localhost:8085/ws}")
        private String clientUrl;

        @EventListener(ApplicationReadyEvent.class)
        public void registerOnStartup() {
                URI uri = org.springframework.web.util.UriComponentsBuilder.fromHttpUrl(serviceRegistryUrl)
                                .path("/registry/service/" + instanceId)
                                .queryParam("address", clientUrl)
                                .build()
                                .toUri();

                System.out.println("Attempting to register service: " + instanceId + " at " + clientUrl);

                webClientBuilder.build()
                                .post()
                                .uri(uri)
                                .retrieve()
                                .toBodilessEntity()
                                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(2)) // Retry indefinitely
                                                                                                // until registry is up
                                                .maxBackoff(Duration.ofSeconds(30)))
                                .subscribe(
                                                success -> System.out.println(
                                                                "Service registered successfully: " + instanceId),
                                                error -> System.err
                                                                .println("Registration failed: " + error.getMessage()));
        }
}
