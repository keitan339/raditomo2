package com.raditomo.registration.repository;

import com.raditomo.registration.entity.DownloadRegistration;
import com.raditomo.registration.entity.RegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DownloadRegistrationRepository extends JpaRepository<DownloadRegistration, Long> {
    List<DownloadRegistration> findByUserIdAndStatus(Long userId, RegistrationStatus status);
}
