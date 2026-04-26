package com.raditomo.notification.template;

import com.raditomo.history.entity.DownloadHistory;
import com.raditomo.history.entity.DownloadStatus;
import com.raditomo.station.entity.Station;
import com.raditomo.station.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * F2 失敗・期限切れの通知メール本文を生成する。
 */
@Component
@RequiredArgsConstructor
public class F2FailureTemplate {

    private static final DateTimeFormatter SUBJECT_DATE_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final DateTimeFormatter PROGRAM_DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private final StationRepository stationRepository;

    public Mail render(LocalDate now, List<DownloadHistory> failed, List<DownloadHistory> expired) {
        String subject = "ダウンロード失敗通知 (" + SUBJECT_DATE_FMT.format(now) + ")";

        Map<String, String> stationNames = lookupStationNames(failed, expired);

        StringBuilder body = new StringBuilder();
        body.append(SUBJECT_DATE_FMT.format(now)).append(" のダウンロード処理で以下の問題が発生しました。\n\n");

        if (!failed.isEmpty()) {
            body.append("【ダウンロード失敗】").append(failed.size()).append("件\n");
            for (DownloadHistory h : failed) {
                body.append("  - ").append(stationNames.getOrDefault(h.getStationId(), h.getStationId()))
                        .append(" ").append(h.getProgramTitle())
                        .append(" (").append(PROGRAM_DATETIME_FMT.format(h.getBroadcastStartAt())).append(")\n");
                if (h.getErrorMessage() != null) {
                    body.append("    エラー: ").append(h.getErrorMessage()).append("\n");
                }
            }
            body.append("\n");
        }

        if (!expired.isEmpty()) {
            body.append("【タイムフリー期限切れ】").append(expired.size()).append("件\n");
            for (DownloadHistory h : expired) {
                body.append("  - ").append(stationNames.getOrDefault(h.getStationId(), h.getStationId()))
                        .append(" ").append(h.getProgramTitle())
                        .append(" (").append(PROGRAM_DATETIME_FMT.format(h.getBroadcastStartAt())).append(")\n");
            }
            body.append("\n");
        }

        body.append("詳細はサーバーログをご確認ください。\n");

        return new Mail(subject, body.toString());
    }

    private Map<String, String> lookupStationNames(List<DownloadHistory>... lists) {
        return java.util.Arrays.stream(lists)
                .flatMap(List::stream)
                .map(DownloadHistory::getStationId)
                .distinct()
                .collect(Collectors.toMap(
                        Function.identity(),
                        id -> stationRepository.findById(id).map(Station::getName).orElse(id),
                        (a, b) -> a));
    }

    public record Mail(String subject, String body) {}
}
