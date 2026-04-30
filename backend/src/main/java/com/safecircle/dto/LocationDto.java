package com.safecircle.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class LocationDto {

    /**
     * Sent by the client when sharing GPS position.
     *
     * <p>GPS bounds are enforced at the DTO level:
     * <ul>
     *   <li>lat ∈ [-90, 90]</li>
     *   <li>lng ∈ [-180, 180]</li>
     * </ul>
     * OFFLINE/NO_GPS updates may send lat=0, lng=0 — these are accepted but not
     * plotted on the map.</p>
     */
    public record LocationUpdateRequest(

            @NotBlank
            String groupId,

            @NotNull
            @DecimalMin(value = "-90.0",  message = "Latitude must be >= -90")
            @DecimalMax(value = "90.0",   message = "Latitude must be <= 90")
            Double lat,

            @NotNull
            @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
            @DecimalMax(value = "180.0",  message = "Longitude must be <= 180")
            Double lng,

            /** ONLINE | OFFLINE | NO_GPS | SOS */
            String status,

            /** Horizontal GPS accuracy in metres; may be null if unavailable */
            Double accuracy

    ) {}

    /**
     * Returned to clients in REST polling and WebSocket broadcasts.
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
