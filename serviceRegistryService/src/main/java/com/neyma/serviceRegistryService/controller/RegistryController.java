package com.neyma.serviceRegistryService.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.neyma.serviceRegistryService.service.RegistryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/registry")
@Tag(name = "Service Registry", description = "API for managing service registration and user assignment")
public class RegistryController {

    private final RegistryService registryService;

    @Autowired
    public RegistryController(RegistryService registryService) {
        this.registryService = registryService;
    }

    @Operation(summary = "Register a service", description = "Registers a service instance as available")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Service registered successfully")
    })
    @PostMapping("/service/{serviceId}")
    public ResponseEntity<Void> registerService(
            @Parameter(description = "The ID of the service to register") @PathVariable Long serviceId) {
        registryService.registerService(serviceId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get assigned service ID", description = "Retrieves the service ID assigned to a user. If no service is assigned, one is randomly assigned from the available pool.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved assigned service ID"),
            @ApiResponse(responseCode = "500", description = "No services available to assign")
    })
    @GetMapping("/user/{userId}")
    public ResponseEntity<Long> getServiceId(
            @Parameter(description = "The ID of the user requesting a service") @PathVariable Long userId) {
        Long serviceId = registryService.getServiceId(userId);
        return ResponseEntity.ok(serviceId);
    }
}
