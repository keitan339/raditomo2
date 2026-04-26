package com.raditomo.batch.service;

import com.raditomo.registration.entity.RegistrationType;

import java.time.OffsetDateTime;

/**
 * F2 ダウンロード対象1件分。動的照合の結果として生成される。
 *
 * @param registrationId 紐付く登録 ID
 * @param userId         ユーザー ID
 * @param stationId      放送局
 * @param title          ID3 / ファイル名に使う番組名（毎週マッチング 手順4 で時刻ベースの場合は
 *                       DB 上の該当時間帯の番組名が入る）
 * @param performers     出演者
 * @param broadcastStartAt 実際にダウンロードする放送開始日時
 * @param broadcastEndAt 実際にダウンロードする放送終了日時
 * @param registrationType ONCE / WEEKLY
 * @param matchStep      マッチングの段階（once / weekly-1 / weekly-3 / weekly-4）— ログ用
 */
public record DownloadCandidate(
        Long registrationId,
        Long userId,
        String stationId,
        String title,
        String performers,
        OffsetDateTime broadcastStartAt,
        OffsetDateTime broadcastEndAt,
        RegistrationType registrationType,
        String matchStep
) {
}
