package com.safecircle.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public class RouteDto {

    public record CheckpointDto(
            @NotNull Double lat,
            @NotNull Double lng,
            String name
    ) {}

    public record UpdateRouteRequest(
            @NotNull List<CheckpointDto> checkpoints
    ) {}
}
