package com.raditomo.program.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.raditomo.program.entity.Program;
import com.raditomo.registration.entity.RegistrationType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public class ProgramDtos {

    public record ProgramListResponse(LocalDate broadcastDate, List<StationGroup> stations) {}

    public record StationGroup(
            String stationId,
            String name,
            List<ProgramItem> programs
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProgramItem(
            Long id,
            String title,
            String performers,
            OffsetDateTime broadcastStartAt,
            OffsetDateTime broadcastEndAt,
            boolean isPast,
            boolean isWithinTimefreeWindow,
            RegistrationRef registration
    ) {}

    public record RegistrationRef(Long registrationId, RegistrationType type) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProgramDetailResponse(
            Long id,
            String stationId,
            String stationName,
            String title,
            String performers,
            String description,
            String info,
            String imageUrl,
            OffsetDateTime broadcastStartAt,
            OffsetDateTime broadcastEndAt,
            boolean isPast,
            boolean isWithinTimefreeWindow,
            RegistrationRef registration
    ) {}

    public static ProgramItem toItem(Program p, OffsetDateTime now,
                                     OffsetDateTime expiresAt,
                                     RegistrationRef ref) {
        return new ProgramItem(
                p.getId(), p.getTitle(), p.getPerformers(),
                p.getBroadcastStartAt(), p.getBroadcastEndAt(),
                !p.getBroadcastEndAt().isAfter(now),
                now.isBefore(expiresAt),
                ref);
    }

    private ProgramDtos() {}
}
