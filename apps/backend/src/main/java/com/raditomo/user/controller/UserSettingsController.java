package com.raditomo.user.controller;

import com.raditomo.batch.controller.BatchController.BatchAsyncDispatcher;
import com.raditomo.batch.entity.BatchExecution;
import com.raditomo.batch.entity.BatchType;
import com.raditomo.batch.entity.TriggeredBy;
import com.raditomo.batch.service.BatchExecutionService;
import com.raditomo.user.dto.UserDtos.*;
import com.raditomo.user.entity.UserSettings;
import com.raditomo.user.entity.UserStationVisibility;
import com.raditomo.user.service.UserSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
@Slf4j
public class UserSettingsController {

    private static final String DEFAULT_AREA_ID = "JP13";

    private final UserSettingsService userSettingsService;
    private final BatchExecutionService batchExecutionService;
    private final BatchAsyncDispatcher batchDispatcher;

    @GetMapping("/settings")
    public UserSettingsResponse getSettings(@AuthenticationPrincipal Long userId) {
        require(userId);
        UserSettings s = userSettingsService.getOrCreate(userId, DEFAULT_AREA_ID);
        return new UserSettingsResponse(s.getCurrentAreaId());
    }

    @PutMapping("/settings")
    public UpdateSettingsResponse updateSettings(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateSettingsRequest request) {
        require(userId);
        // 1. エリアを先に保存（コミット確定）
        UserSettings updated = userSettingsService.updateArea(userId, request.currentAreaId());

        // 2. F4 を非同期キック（F2 連鎖なし）。失敗しても設定は維持される
        AreaChangeFetchStatus fetchStatus;
        try {
            BatchExecution exec = batchExecutionService.start(
                    BatchType.F4, TriggeredBy.WEB, userId, null);
            batchDispatcher.runAsync(exec, BatchType.F4, null);
            fetchStatus = new AreaChangeFetchStatus(exec.getId(), exec.getStatus().name());
        } catch (BatchExecutionService.BatchAlreadyRunningException e) {
            log.info("F4 already running, skipping area-change kick: id={}", e.getRunning().getId());
            fetchStatus = new AreaChangeFetchStatus(e.getRunning().getId(), e.getRunning().getStatus().name());
        }
        return new UpdateSettingsResponse(updated.getCurrentAreaId(), fetchStatus);
    }

    @GetMapping("/station-visibility")
    public List<StationVisibilityResponse> listVisibility(
            @AuthenticationPrincipal Long userId,
            @RequestParam String areaId) {
        require(userId);
        return userSettingsService.listVisibility(userId, areaId).stream()
                .map(v -> new StationVisibilityResponse(v.getStationId(), v.isVisible()))
                .toList();
    }

    @PutMapping("/station-visibility/{stationId}")
    public StationVisibilityResponse updateVisibility(
            @AuthenticationPrincipal Long userId,
            @PathVariable String stationId,
            @Valid @RequestBody UpdateVisibilityRequest request) {
        require(userId);
        UserStationVisibility v = userSettingsService.setVisibility(userId, stationId, request.visible());
        return new StationVisibilityResponse(v.getStationId(), v.isVisible());
    }

    private void require(Long userId) {
        if (userId == null) throw new AccessDeniedException("Not authenticated");
    }
}
