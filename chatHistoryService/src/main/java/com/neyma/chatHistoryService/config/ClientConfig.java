package com.neyma.chatHistoryService.config;

import com.neyma.chatHistoryService.client.ApiClient;
import com.neyma.chatHistoryService.client.api.MessageControllerApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ClientConfig {

    @Value("${application.gateway.url:http://api-gateway:8080}")
    private String gatewayUrl;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public ApiClient apiClient(RestTemplate restTemplate) {
        ApiClient apiClient = new ApiClient(restTemplate);
        apiClient.setBasePath(gatewayUrl);
        return apiClient;
    }

    @Bean
    public MessageControllerApi messageControllerApi(ApiClient apiClient) {
        return new MessageControllerApi(apiClient);
    }
}
