package com.neyma.serviceRegistryService.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.neyma.serviceRegistryService.service.RegistryService;
import com.neyma.serviceRegistryService.dto.ServiceAssignment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/registry")
@Tag(name = "Service Registry", description = "API for managing service registration and user assignment")
public class RegistryController {

    private final RegistryService registryService;

    @Autowired
    public RegistryController(RegistryService registryService) {
        this.registryService = registryService;
    }

    @Operation(summary = "Register a service", description = "Registers a service instance as available, along with its client-facing address.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Service registered successfully")
    })
    @PostMapping("/service/{serviceId}")
    public ResponseEntity<Void> registerService(
            @Parameter(description = "The UUID of the service instance to register") @PathVariable UUID serviceId,
            @Parameter(description = "The public WebSocket address (URL) of the service") @RequestParam String address) {
        registryService.registerService(serviceId, address);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get assigned service", description = "Retrieves the assigned Service ID and Address for a specific user. If no service is assigned, one will be selected.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved assigned service info"),
            @ApiResponse(responseCode = "500", description = "No services available to assign")
    })
    @GetMapping("/user/{userId}")
    public ResponseEntity<ServiceAssignment> getServiceAssignment(
            @Parameter(description = "The UUID of the user requesting a service") @PathVariable UUID userId) {
        ServiceAssignment assignment = registryService.getServiceAssignment(userId);
        return ResponseEntity.ok(assignment);
    }
}
