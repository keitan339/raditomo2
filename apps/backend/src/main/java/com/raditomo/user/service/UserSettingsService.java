package com.raditomo.user.service;

import com.raditomo.user.entity.UserSettings;
import com.raditomo.user.entity.UserStationVisibility;
import com.raditomo.user.repository.UserSettingsRepository;
import com.raditomo.user.repository.UserStationVisibilityRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private final UserSettingsRepository userSettingsRepository;
    private final UserStationVisibilityRepository visibilityRepository;
    private final Clock clock;

    public UserSettings getOrCreate(Long userId, String defaultAreaId) {
        return userSettingsRepository.findById(userId).orElseGet(() -> {
            UserSettings created = UserSettings.builder()
                    .userId(userId)
                    .currentAreaId(defaultAreaId)
                    .updatedAt(OffsetDateTime.now(clock))
                    .build();
            return userSettingsRepository.save(created);
        });
    }

    @Transactional
    public UserSettings updateArea(Long userId, String newAreaId) {
        UserSettings settings = userSettingsRepository.findById(userId).orElseGet(() ->
                UserSettings.builder().userId(userId).build());
        settings.setCurrentAreaId(newAreaId);
        settings.setUpdatedAt(OffsetDateTime.now(clock));
        return userSettingsRepository.save(settings);
    }

    public List<UserStationVisibility> listVisibility(Long userId, String areaId) {
        return visibilityRepository.findByUserIdAndAreaId(userId, areaId);
    }

    @Transactional
    public UserStationVisibility setVisibility(Long userId, String stationId, boolean visible) {
        UserStationVisibility.Pk pk = new UserStationVisibility.Pk(userId, stationId);
        UserStationVisibility v = visibilityRepository.findById(pk)
                .orElseGet(() -> UserStationVisibility.builder()
                        .userId(userId)
                        .stationId(stationId)
                        .build());
        v.setVisible(visible);
        return visibilityRepository.save(v);
    }
}
