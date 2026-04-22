package com.safecircle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class GroupDto {

    public record CreateGroupRequest(
            @NotBlank String name,
            double distanceThreshold
    ) {}

    public record JoinGroupRequest(
            @NotBlank String inviteCode
    ) {}

    public record SetThresholdRequest(
            @NotNull @Positive Double threshold
    ) {}

    public record GroupResponse(
            String id,
            String name,
            String adminId,
            java.util.List<String> memberIds,
            String inviteCode,
            double distanceThreshold
    ) {}
}
