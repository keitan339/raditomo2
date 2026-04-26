package com.raditomo.history.controller;

import com.raditomo.history.dto.RecordingDtos.*;
import com.raditomo.history.entity.DownloadHistory;
import com.raditomo.history.entity.DownloadStatus;
import com.raditomo.history.entity.PlaybackPosition;
import com.raditomo.history.repository.DownloadHistoryRepository;
import com.raditomo.history.service.RecordingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recordings")
@RequiredArgsConstructor
public class RecordingController {

    private final DownloadHistoryRepository historyRepository;
    private final RecordingService recordingService;

    @GetMapping("/groups")
    public List<RecordingGroupResponse> groups(@AuthenticationPrincipal Long userId) {
        require(userId);
        return historyRepository.findRecordingGroups(userId).stream()
                .map(RecordingGroupResponse::from)
                .toList();
    }

    @GetMapping
    public List<RecordingResponse> listByTitle(
            @AuthenticationPrincipal Long userId,
            @RequestParam String title) {
        require(userId);
        return historyRepository.findByUserIdAndProgramTitleOrderByBroadcastStartAtDesc(userId, title).stream()
                .filter(h -> h.getStatus() == DownloadStatus.SUCCESS && h.getFileDeletedAt() == null)
                .map(h -> RecordingResponse.from(
                        h, recordingService.hlsUrl(h), recordingService.isReDownloadable(h)))
                .toList();
    }

    @GetMapping("/{historyId}")
    public ResponseEntity<RecordingResponse> detail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long historyId) {
        require(userId);
        return recordingService.findOwnedById(userId, historyId)
                .map(h -> RecordingResponse.from(
                        h, recordingService.hlsUrl(h), recordingService.isReDownloadable(h)))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{historyId}")
    public ResponseEntity<Void> deleteFiles(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long historyId) {
        require(userId);
        return recordingService.deleteRecordingFiles(userId, historyId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @GetMapping("/{historyId}/playback-position")
    public ResponseEntity<PlaybackPositionResponse> getPosition(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long historyId) {
        require(userId);
        if (recordingService.findOwnedById(userId, historyId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PlaybackPosition p = recordingService.getOrZero(userId, historyId);
        return ResponseEntity.ok(new PlaybackPositionResponse(p.getPositionSeconds()));
    }

    @PutMapping("/{historyId}/playback-position")
    public ResponseEntity<PlaybackPositionResponse> putPosition(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long historyId,
            @Valid @RequestBody PlaybackPositionUpdateRequest request) {
        require(userId);
        if (recordingService.findOwnedById(userId, historyId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PlaybackPosition saved = recordingService.savePosition(userId, historyId, request.positionSeconds());
        return ResponseEntity.ok(new PlaybackPositionResponse(saved.getPositionSeconds()));
    }

    private void require(Long userId) {
        if (userId == null) throw new AccessDeniedException("Not authenticated");
    }
}
