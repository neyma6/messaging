package com.neyma.messagingService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MessagingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(MessagingServiceApplication.class, args);
	}

	@org.springframework.context.annotation.Bean
	public java.util.UUID instanceId() {
		return java.util.UUID.randomUUID();
	}
}
