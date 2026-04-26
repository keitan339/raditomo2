package com.raditomo.program.controller;

import com.raditomo.common.time.JstTimes;
import com.raditomo.program.dto.ProgramDtos;
import com.raditomo.program.dto.ProgramDtos.*;
import com.raditomo.program.entity.Program;
import com.raditomo.program.repository.ProgramRepository;
import com.raditomo.registration.entity.DownloadRegistration;
import com.raditomo.registration.entity.RegistrationStatus;
import com.raditomo.registration.entity.RegistrationType;
import com.raditomo.registration.repository.DownloadRegistrationRepository;
import com.raditomo.station.entity.Station;
import com.raditomo.station.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/programs")
@RequiredArgsConstructor
public class ProgramController {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ProgramRepository programRepository;
    private final StationRepository stationRepository;
    private final DownloadRegistrationRepository registrationRepository;
    private final Clock clock;

    @GetMapping
    public ProgramListResponse list(
            @AuthenticationPrincipal Long userId,
            @RequestParam String areaId,
            @RequestParam String date) {
        require(userId);
        LocalDate broadcastDate = LocalDate.parse(date, YYYYMMDD);
        List<Program> programs = programRepository.findByAreaAndDate(areaId, broadcastDate);
        Map<String, Station> stationsById = stationRepository.findByAreaIdOrderBySortOrderAsc(areaId).stream()
                .collect(java.util.stream.Collectors.toMap(Station::getId, s -> s, (a, b) -> a, LinkedHashMap::new));
        Map<Long, RegistrationRef> regRefs = buildRegistrationRefs(userId);

        OffsetDateTime now = OffsetDateTime.now(clock);
        Map<String, List<ProgramItem>> grouped = new LinkedHashMap<>();
        stationsById.keySet().forEach(id -> grouped.put(id, new ArrayList<>()));
        for (Program p : programs) {
            ProgramItem item = ProgramDtos.toItem(p, now, JstTimes.expiresAt(p.getBroadcastStartAt()),
                    regRefs.get(p.getId()));
            grouped.computeIfAbsent(p.getStationId(), k -> new ArrayList<>()).add(item);
        }
        List<StationGroup> groups = new ArrayList<>();
        grouped.forEach((id, items) -> {
            Station st = stationsById.get(id);
            String name = st != null ? st.getName() : id;
            groups.add(new StationGroup(id, name, items));
        });
        return new ProgramListResponse(broadcastDate, groups);
    }

    @GetMapping("/search")
    public List<ProgramItem> search(
            @AuthenticationPrincipal Long userId,
            @RequestParam String areaId,
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        require(userId);
        if (q.isBlank()) return List.of();
        var pageable = PageRequest.of(page, size);
        List<Program> hits = programRepository.searchByAreaAndKeyword(areaId, q, pageable);
        Map<Long, RegistrationRef> refs = buildRegistrationRefs(userId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        return hits.stream()
                .map(p -> ProgramDtos.toItem(p, now, JstTimes.expiresAt(p.getBroadcastStartAt()),
                        refs.get(p.getId())))
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProgramDetailResponse> detail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        require(userId);
        return programRepository.findById(id)
                .map(p -> {
                    Station st = stationRepository.findById(p.getStationId()).orElse(null);
                    OffsetDateTime now = OffsetDateTime.now(clock);
                    RegistrationRef ref = buildRegistrationRefs(userId).get(p.getId());
                    return ResponseEntity.ok(new ProgramDetailResponse(
                            p.getId(),
                            p.getStationId(),
                            st == null ? p.getStationId() : st.getName(),
                            p.getTitle(),
                            p.getPerformers(),
                            p.getDescription(),
                            p.getInfo(),
                            p.getImageUrl(),
                            p.getBroadcastStartAt(),
                            p.getBroadcastEndAt(),
                            !p.getBroadcastEndAt().isAfter(now),
                            now.isBefore(JstTimes.expiresAt(p.getBroadcastStartAt())),
                            ref));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Map<Long, RegistrationRef> buildRegistrationRefs(Long userId) {
        // ユーザーの ACTIVE 登録を、登録時刻 → program との紐付けに使う。
        // ONCE は (station, broadcast_start_at) で program を引いて registration_id を関連付ける。
        // WEEKLY は (station, day_of_week, title) で複数 program に紐付く。
        Map<Long, RegistrationRef> out = new HashMap<>();
        List<DownloadRegistration> regs = registrationRepository
                .findByUserIdAndStatus(userId, RegistrationStatus.ACTIVE);
        for (DownloadRegistration r : regs) {
            if (r.getRegistrationType() == RegistrationType.ONCE) {
                programRepository.findByStationIdAndBroadcastStartAt(r.getStationId(), r.getBroadcastStartAt())
                        .ifPresent(p -> out.put(p.getId(), new RegistrationRef(r.getId(), r.getRegistrationType())));
            } else {
                programRepository.findFirstByStationIdAndBroadcastDateAndTitleOrderByBroadcastStartAtAsc(
                                r.getStationId(),
                                JstTimes.broadcastDate(r.getBroadcastStartAt()),
                                r.getTitle())
                        .ifPresent(p -> out.put(p.getId(), new RegistrationRef(r.getId(), r.getRegistrationType())));
            }
        }
        return out;
    }

    private void require(Long userId) {
        if (userId == null) throw new AccessDeniedException("Not authenticated");
    }
}
