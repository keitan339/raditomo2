package com.raditomo.notification.service;

import com.raditomo.history.entity.DownloadHistory;
import com.raditomo.history.entity.DownloadStatus;
import com.raditomo.history.repository.UnnotifiedHistoryRepository;
import com.raditomo.common.util.LogMask;
import com.raditomo.notification.NotificationProperties;
import com.raditomo.notification.template.F2FailureTemplate;
import com.raditomo.user.entity.User;
import com.raditomo.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * バッチ完了時の通知メール送信。
 *
 * - 各ユーザーの未通知の FAILED / EXPIRED 履歴を抽出
 * - あれば 1 通にまとめて送信
 * - 送信成功なら notified_at = NOW() で更新
 * - 送信失敗時は notified_at を更新せず、次回バッチで再送
 *
 * 通知無効化: {@code raditomo.notification.enabled=false} で無効化（テスト用）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private static final List<DownloadStatus> NOTIFIABLE_STATUSES =
            List.of(DownloadStatus.FAILED, DownloadStatus.EXPIRED);

    private final JavaMailSender mailSender;
    private final NotificationProperties notificationProperties;
    private final UnnotifiedHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final F2FailureTemplate template;
    private final Clock clock;

    /**
     * 全ユーザー分の未通知失敗・期限切れ履歴を集計してメール送信する。
     */
    @Transactional
    public NotificationResult notifyAllPendingFailures() {
        if (!notificationProperties.enabled()) {
            log.info("Notification disabled. Skipping.");
            return new NotificationResult(0, 0);
        }

        int sentMails = 0;
        int totalMarked = 0;
        for (User user : userRepository.findAll()) {
            if (!user.isActive() || user.getEmail() == null) continue;
            List<DownloadHistory> pending = historyRepository
                    .findUnnotifiedForUser(user.getId(), NOTIFIABLE_STATUSES);
            if (pending.isEmpty()) continue;

            List<DownloadHistory> failed = new ArrayList<>();
            List<DownloadHistory> expired = new ArrayList<>();
            for (DownloadHistory h : pending) {
                if (h.getStatus() == DownloadStatus.FAILED) failed.add(h);
                else if (h.getStatus() == DownloadStatus.EXPIRED) expired.add(h);
            }

            try {
                sendMail(user.getEmail(), failed, expired);
                List<Long> ids = new ArrayList<>(failed.size() + expired.size());
                failed.forEach(h -> ids.add(h.getId()));
                expired.forEach(h -> ids.add(h.getId()));
                int marked = historyRepository.markNotified(ids, OffsetDateTime.now(clock));
                totalMarked += marked;
                sentMails++;
            } catch (RuntimeException e) {
                log.error("Failed to send notification to {}: {}", LogMask.email(user.getEmail()), e.toString(), e);
                // notified_at は更新しない → 次回バッチで再送
            }
        }
        log.info("Notification done: sentMails={} markedHistories={}", sentMails, totalMarked);
        return new NotificationResult(sentMails, totalMarked);
    }

    private void sendMail(String to, List<DownloadHistory> failed, List<DownloadHistory> expired) {
        F2FailureTemplate.Mail mail = template.render(
                java.time.LocalDate.now(clock.withZone(ZoneId.of("Asia/Tokyo"))),
                failed, expired);
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(notificationProperties.from());
        msg.setTo(to);
        msg.setSubject(notificationProperties.subjectPrefix() + " " + mail.subject());
        msg.setText(mail.body());
        mailSender.send(msg);
    }

    public record NotificationResult(int sentMails, int markedHistories) {}
}
