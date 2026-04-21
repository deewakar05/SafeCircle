package com.safecircle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class LocationDto {

    public record LocationUpdateRequest(
            @NotBlank String groupId,
            @NotNull Double lat,
            @NotNull Double lng,
            String status  // ONLINE | OFFLINE | NO_GPS
    ) {}

    public record LocationResponse(
            String userId,
            String userName,
            String groupId,
            double lat,
            double lng,
            String status,
            long timestamp
    ) {}
}
