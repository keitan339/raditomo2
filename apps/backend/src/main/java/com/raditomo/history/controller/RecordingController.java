package com.raditomo.history.controller;

import com.raditomo.history.dto.RecordingDtos.*;
import com.raditomo.history.entity.DownloadHistory;
import com.raditomo.history.entity.DownloadStatus;
import com.raditomo.history.entity.PlaybackPosition;
import com.raditomo.history.repository.DownloadHistoryRepository;
import com.raditomo.history.service.RecordingService;
import com.raditomo.history.service.RecordingTitleGrouper;
import com.raditomo.station.repository.StationRepository;
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
    private final RecordingTitleGrouper titleGrouper;
    private final StationRepository stationRepository;

    private String stationNameOf(String stationId) {
        return stationRepository.findById(stationId).map(s -> s.getName()).orElse(stationId);
    }

    @GetMapping("/groups")
    public List<RecordingGroupResponse> groups(@AuthenticationPrincipal Long userId) {
        require(userId);
        // タイトルから「○時台...」「(N)」「Part N」「第N回」を取り除いたグループキーで集約。
        // 例: 「らじらー！　サンデー　8時台 ...」「同 9時台 ...」「同 10時台 ...」 → 1グループ
        // また force 再実行で重複した履歴は (station, broadcastStartAt) 単位でデデュープする。
        List<DownloadHistory> distinct = dedupePerBroadcast(
                historyRepository.findAvailableRecordings(userId));
        var byKey = new java.util.LinkedHashMap<String, RecordingGroupResponse>();
        for (DownloadHistory h : distinct) {
            String key = titleGrouper.groupKey(h.getProgramTitle());
            RecordingGroupResponse cur = byKey.get(key);
            if (cur == null) {
                byKey.put(key, new RecordingGroupResponse(key, 1, h.getBroadcastStartAt()));
            } else {
                var latest = cur.latestBroadcastAt().isAfter(h.getBroadcastStartAt())
                        ? cur.latestBroadcastAt() : h.getBroadcastStartAt();
                byKey.put(key, new RecordingGroupResponse(key, cur.count() + 1, latest));
            }
        }
        return byKey.values().stream()
                .sorted(java.util.Comparator.comparing(RecordingGroupResponse::latestBroadcastAt).reversed())
                .toList();
    }

    @GetMapping
    public List<RecordingResponse> listByTitle(
            @AuthenticationPrincipal Long userId,
            @RequestParam String title) {
        require(userId);
        // title は groups エンドポイントで返したグループキー。
        // 各履歴のタイトルをグループキー化して、リクエストの title と一致するものを返す。
        // force 再実行で同じ放送（同 station + 同 broadcastStartAt）の履歴が複数あった場合は
        // 最新（最大 historyId）の 1 件だけ返す（実ファイルは同じ mp3 を共有しているため）。
        var matched = historyRepository.findAvailableRecordings(userId).stream()
                .filter(h -> title.equals(titleGrouper.groupKey(h.getProgramTitle())))
                .toList();
        return dedupePerBroadcast(matched).stream()
                .map(h -> RecordingResponse.from(
                        h, stationNameOf(h.getStationId()),
                        recordingService.hlsUrl(h), recordingService.isReDownloadable(h)))
                .toList();
    }

    /**
     * 同じ放送（station_id + broadcast_start_at）の重複履歴を最新（id 大）の 1 件に絞る。
     * F2 の force 再実行で同じ MP3 を上書きしながら history を新規 INSERT した結果の見た目重複を解消する。
     */
    private static List<DownloadHistory> dedupePerBroadcast(List<DownloadHistory> hits) {
        var byBroadcast = new java.util.LinkedHashMap<String, DownloadHistory>();
        for (DownloadHistory h : hits) {
            String key = h.getStationId() + "|" + h.getBroadcastStartAt();
            DownloadHistory cur = byBroadcast.get(key);
            if (cur == null || h.getId() > cur.getId()) {
                byBroadcast.put(key, h);
            }
        }
        return List.copyOf(byBroadcast.values());
    }

    @GetMapping("/{historyId}")
    public ResponseEntity<RecordingResponse> detail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long historyId) {
        require(userId);
        return recordingService.findOwnedById(userId, historyId)
                .map(h -> RecordingResponse.from(
                        h, stationNameOf(h.getStationId()),
                        recordingService.hlsUrl(h), recordingService.isReDownloadable(h)))
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
