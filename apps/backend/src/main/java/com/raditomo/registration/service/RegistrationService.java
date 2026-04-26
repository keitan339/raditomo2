package com.raditomo.registration.service;

import com.raditomo.common.time.JstTimes;
import com.raditomo.registration.dto.RegistrationDtos.CreateRegistrationRequest;
import com.raditomo.registration.entity.DownloadRegistration;
import com.raditomo.registration.entity.RegistrationStatus;
import com.raditomo.registration.entity.RegistrationType;
import com.raditomo.registration.repository.DownloadRegistrationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final DownloadRegistrationRepository repository;

    public List<DownloadRegistration> listForUser(Long userId, RegistrationStatus status) {
        return status == null
                ? repository.findByUserIdAndStatus(userId, RegistrationStatus.ACTIVE)
                : repository.findByUserIdAndStatus(userId, status);
    }

    @Transactional
    public DownloadRegistration create(Long userId, CreateRegistrationRequest req) {
        Short dayOfWeek = null;
        if (req.registrationType() == RegistrationType.WEEKLY) {
            dayOfWeek = JstTimes.dayOfWeek(JstTimes.broadcastDate(req.broadcastStartAt()));
        }
        DownloadRegistration r = DownloadRegistration.builder()
                .userId(userId)
                .stationId(req.stationId())
                .registrationType(req.registrationType())
                .title(req.title())
                .broadcastStartAt(req.broadcastStartAt())
                .broadcastEndAt(req.broadcastEndAt())
                .dayOfWeek(dayOfWeek)
                .status(RegistrationStatus.ACTIVE)
                .build();
        try {
            return repository.save(r);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateRegistrationException();
        }
    }

    @Transactional
    public boolean delete(Long userId, Long registrationId) {
        return repository.findById(registrationId)
                .filter(r -> r.getUserId().equals(userId))
                .map(r -> {
                    repository.delete(r);
                    return true;
                })
                .orElse(false);
    }

    public static class DuplicateRegistrationException extends RuntimeException {
        public DuplicateRegistrationException() { super("Duplicate registration"); }
    }
}
