package com.raditomo.registration.controller;

import com.raditomo.registration.dto.RegistrationDtos.CreateRegistrationRequest;
import com.raditomo.registration.dto.RegistrationDtos.RegistrationResponse;
import com.raditomo.registration.entity.RegistrationStatus;
import com.raditomo.registration.service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/registrations")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    @GetMapping
    public List<RegistrationResponse> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) RegistrationStatus status) {
        require(userId);
        return registrationService.listForUser(userId, status).stream()
                .map(RegistrationResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<RegistrationResponse> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateRegistrationRequest req) {
        require(userId);
        try {
            var r = registrationService.create(userId, req);
            return ResponseEntity.status(HttpStatus.CREATED).body(RegistrationResponse.from(r));
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
