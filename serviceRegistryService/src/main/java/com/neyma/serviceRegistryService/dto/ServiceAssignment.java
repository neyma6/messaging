package com.neyma.serviceRegistryService.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceAssignment {
    private UUID serviceId;
    private String address;
}
