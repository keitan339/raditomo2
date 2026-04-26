package com.raditomo.user.dto;

import jakarta.validation.constraints.NotBlank;

public class UserDtos {

    public record UserSettingsResponse(String currentAreaId) {}

    public record UpdateSettingsRequest(@NotBlank String currentAreaId) {}

    public record UpdateSettingsResponse(
            String currentAreaId,
            AreaChangeFetchStatus areaChangeFetchStatus) {}

    public record AreaChangeFetchStatus(Long batchExecutionId, String status) {}

    public record StationVisibilityResponse(String stationId, boolean visible) {}

    public record UpdateVisibilityRequest(boolean visible) {}

    private UserDtos() {}
}
