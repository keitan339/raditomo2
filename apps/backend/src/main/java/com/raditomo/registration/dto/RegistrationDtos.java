package com.raditomo.registration.dto;

import com.raditomo.registration.entity.DownloadRegistration;
import com.raditomo.registration.entity.RegistrationStatus;
import com.raditomo.registration.entity.RegistrationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public class RegistrationDtos {

    public record CreateRegistrationRequest(
            @NotBlank String stationId,
            @NotBlank String title,
            @NotNull OffsetDateTime broadcastStartAt,
            @NotNull OffsetDateTime broadcastEndAt,
            @NotNull RegistrationType registrationType
    ) {}

    public record RegistrationResponse(
            Long id,
            String stationId,
            String stationName,
            String title,
            OffsetDateTime broadcastStartAt,
            OffsetDateTime broadcastEndAt,
            Short dayOfWeek,
            RegistrationType registrationType,
            RegistrationStatus status
    ) {
        public static RegistrationResponse from(DownloadRegistration r, String stationName) {
            return new RegistrationResponse(
                    r.getId(), r.getStationId(),
                    stationName != null ? stationName : r.getStationId(),
                    r.getTitle(),
                    r.getBroadcastStartAt(), r.getBroadcastEndAt(),
                    r.getDayOfWeek(), r.getRegistrationType(), r.getStatus());
        }
    }

    private RegistrationDtos() {}
}
