package com.raditomo.registration.controller;

import com.raditomo.registration.dto.RegistrationDtos.CreateRegistrationRequest;
import com.raditomo.registration.dto.RegistrationDtos.RegistrationResponse;
import com.raditomo.registration.entity.RegistrationStatus;
import com.raditomo.registration.service.RegistrationService;
import com.raditomo.station.repository.StationRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/registrations")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;
    private final StationRepository stationRepository;

    @GetMapping
    public List<RegistrationResponse> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) RegistrationStatus status) {
        require(userId);
        var regs = registrationService.listForUser(userId, status);
        var stationIds = regs.stream().map(r -> r.getStationId()).distinct().toList();
        Map<String, String> nameByStationId = stationRepository.findAllById(stationIds).stream()
                .collect(Collectors.toMap(s -> s.getId(), s -> s.getName(), (a, b) -> a));
        return regs.stream()
                .map(r -> RegistrationResponse.from(r, nameByStationId.get(r.getStationId())))
                .toList();
    }

    @PostMapping
    public ResponseEntity<RegistrationResponse> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateRegistrationRequest req) {
        require(userId);
        try {
            var r = registrationService.create(userId, req);
            String stationName = stationRepository.findById(r.getStationId())
                    .map(s -> s.getName()).orElse(null);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(RegistrationResponse.from(r, stationName));
        } catch (RegistrationService.DuplicateRegistrationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long userId, @PathVariable Long id) {
        require(userId);
        return registrationService.delete(userId, id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    private void require(Long userId) {
        if (userId == null) throw new AccessDeniedException("Not authenticated");
    }
}
