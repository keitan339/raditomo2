package com.raditomo.history.controller;

import com.raditomo.history.dto.BadgeResponse;
import com.raditomo.history.dto.HistoryDtos.HistoryItem;
import com.raditomo.history.dto.HistoryDtos.PageResponse;
import com.raditomo.history.entity.DownloadHistory;
import com.raditomo.history.entity.DownloadStatus;
import com.raditomo.history.repository.DownloadHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/histories")
@RequiredArgsConstructor
public class HistoryController {

    private final DownloadHistoryRepository historyRepository;

    @GetMapping("/badge")
    public BadgeResponse badge(@AuthenticationPrincipal Long userId) {
        require(userId);
        long failed = historyRepository.countByUserIdAndStatus(userId, DownloadStatus.FAILED);
        long expired = historyRepository.countByUserIdAndStatus(userId, DownloadStatus.EXPIRED);
        return new BadgeResponse(failed, expired);
    }

    @GetMapping
    public PageResponse<HistoryItem> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) DownloadStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        require(userId);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "attemptedAt"));
        Page<DownloadHistory> hits = status == null
                ? historyRepository.findByUserId(userId, pageable)
                : historyRepository.findByUserIdAndStatus(userId, status, pageable);
        return new PageResponse<>(
                hits.getContent().stream().map(HistoryItem::from).toList(),
                hits.getNumber(), hits.getSize(),
                hits.getTotalElements(), hits.getTotalPages());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long userId, @PathVariable Long id) {
        require(userId);
        return historyRepository.findById(id)
                .filter(h -> h.getUserId().equals(userId))
                .map(h -> {
                    historyRepository.delete(h);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private void require(Long userId) {
        if (userId == null) throw new AccessDeniedException("Not authenticated");
    }
}
