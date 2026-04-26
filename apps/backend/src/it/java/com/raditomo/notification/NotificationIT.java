package com.raditomo.notification;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;
import com.raditomo.AbstractIT;
import com.raditomo.history.entity.DownloadHistory;
import com.raditomo.history.entity.DownloadStatus;
import com.raditomo.history.repository.DownloadHistoryRepository;
import com.raditomo.notification.service.NotificationService;
import com.raditomo.station.entity.Station;
import com.raditomo.station.repository.StationRepository;
import com.raditomo.user.entity.User;
import com.raditomo.user.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationIT extends AbstractIT {

    private static final int SMTP_PORT = 13025;

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(
            new ServerSetup(SMTP_PORT, "127.0.0.1", "smtp"))
            .withConfiguration(GreenMailConfiguration.aConfig()
                    .withUser("test@example.com", "test"));

    @Autowired NotificationService notificationService;
    @Autowired UserRepository userRepository;
    @Autowired StationRepository stationRepository;
    @Autowired DownloadHistoryRepository historyRepository;

    @DynamicPropertySource
    static void mailProps(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> SMTP_PORT);
        registry.add("spring.mail.username", () -> "test@example.com");
        registry.add("spring.mail.password", () -> "test");
        // GreenMail はデフォルトで認証を要求しないので start TLS / auth は無効化
        registry.add("spring.mail.properties.mail.smtp.auth", () -> "false");
        registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> "false");
        registry.add("raditomo.notification.from", () -> "noreply@example.com");
    }

    @BeforeEach
    void cleanData() {
        historyRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterEach
    void resetMail() {
        greenMail.reset();
    }

    @Test
    void notifyAllPendingFailures_sendsEmailWhenFailedHistoryExists() throws Exception {
        User user = userRepository.save(User.builder().email("user@example.com").active(true).build());
        ensureStation();
        historyRepository.save(failureHistory(user.getId()));

        NotificationService.NotificationResult result = notificationService.notifyAllPendingFailures();
        assertThat(result.sentMails()).isEqualTo(1);
        assertThat(result.markedHistories()).isEqualTo(1);

        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertThat(messages).hasSize(1);
        MimeMessage m = messages[0];
        assertThat(m.getSubject()).startsWith("[Raditomo]");
        assertThat(m.getSubject()).contains("ダウンロード失敗通知");
        assertThat(m.getAllRecipients()).extracting(Object::toString).contains("user@example.com");
        String body = (String) m.getContent();
        assertThat(body).contains("ダウンロード失敗");
        assertThat(body).contains("テスト番組");

        // notified_at 更新確認
        DownloadHistory persisted = historyRepository.findAll().get(0);
        assertThat(persisted.getNotifiedAt()).isNotNull();
    }

    @Test
    void notifyAllPendingFailures_sendsNothingWhenNoFailures() {
        User user = userRepository.save(User.builder().email("user@example.com").active(true).build());
        ensureStation();
        // SUCCESS のみ
        DownloadHistory ok = DownloadHistory.builder()
                .userId(user.getId())
                .stationId("TBS")
                .programTitle("成功番組")
                .broadcastStartAt(OffsetDateTime.parse("2026-04-26T05:00:00+09:00"))
                .broadcastEndAt(OffsetDateTime.parse("2026-04-26T06:00:00+09:00"))
                .status(DownloadStatus.SUCCESS)
                .build();
        historyRepository.save(ok);

        NotificationService.NotificationResult result = notificationService.notifyAllPendingFailures();
        assertThat(result.sentMails()).isZero();
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void notifyAllPendingFailures_skipsAlreadyNotified() throws Exception {
        User user = userRepository.save(User.builder().email("user@example.com").active(true).build());
        ensureStation();
        DownloadHistory already = failureHistory(user.getId());
        already.setNotifiedAt(OffsetDateTime.now());
        historyRepository.save(already);

        NotificationService.NotificationResult result = notificationService.notifyAllPendingFailures();
        assertThat(result.sentMails()).isZero();
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    private void ensureStation() {
        if (!stationRepository.existsById("TBS")) {
            stationRepository.save(Station.builder()
                    .id("TBS")
                    .areaId("JP13")
                    .name("TBSラジオ")
                    .updatedAt(OffsetDateTime.now())
                    .build());
        }
    }

    private DownloadHistory failureHistory(Long userId) {
        return DownloadHistory.builder()
                .userId(userId)
                .stationId("TBS")
                .programTitle("テスト番組")
                .broadcastStartAt(OffsetDateTime.parse("2026-04-26T05:00:00+09:00"))
                .broadcastEndAt(OffsetDateTime.parse("2026-04-26T06:00:00+09:00"))
                .status(DownloadStatus.FAILED)
                .errorMessage("HTTP 500")
                .retryCount((short) 3)
                .build();
    }
}
