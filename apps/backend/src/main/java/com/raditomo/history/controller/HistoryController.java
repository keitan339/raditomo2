package com.raditomo.history.controller;

import com.raditomo.history.dto.BadgeResponse;
import com.raditomo.history.entity.DownloadStatus;
import com.raditomo.history.repository.DownloadHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/histories")
@RequiredArgsConstructor
public class HistoryController {

    private final DownloadHistoryRepository historyRepository;

    @GetMapping("/badge")
    public BadgeResponse badge(@AuthenticationPrincipal Long userId) {
        if (userId == null) throw new AccessDeniedException("Not authenticated");
        long failed = historyRepository.countByUserIdAndStatus(userId, DownloadStatus.FAILED);
        long expired = historyRepository.countByUserIdAndStatus(userId, DownloadStatus.EXPIRED);
        return new BadgeResponse(failed, expired);
    }
}
