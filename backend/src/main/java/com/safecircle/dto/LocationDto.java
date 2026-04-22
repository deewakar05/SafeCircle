package com.safecircle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class LocationDto {

    /**
     * Sent by the client when sharing GPS position.
     * accuracy is optional (metres, from Geolocation API).
     */
    public record LocationUpdateRequest(
            @NotBlank String groupId,
            @NotNull  Double lat,
            @NotNull  Double lng,
            String  status,    // ONLINE | OFFLINE | NO_GPS
            Double  accuracy   // horizontal accuracy in metres; may be null
    ) {}

    /**
     * Returned to clients in polling & WebSocket broadcasts.
     */
    public record LocationResponse(
            String userId,
            String userName,
            String groupId,
            double lat,
            double lng,
            String status,
            long   timestamp,
            Double accuracy
    ) {}
}
