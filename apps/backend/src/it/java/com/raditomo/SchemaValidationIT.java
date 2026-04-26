package com.raditomo;

import com.raditomo.area.repository.AreaRepository;
import com.raditomo.batch.repository.BatchExecutionRepository;
import com.raditomo.history.repository.DownloadHistoryRepository;
import com.raditomo.history.repository.PlaybackPositionRepository;
import com.raditomo.program.repository.ProgramRepository;
import com.raditomo.program.repository.RawProgramXmlRepository;
import com.raditomo.registration.repository.DownloadRegistrationRepository;
import com.raditomo.station.repository.StationRepository;
import com.raditomo.user.repository.UserRepository;
import com.raditomo.user.repository.UserSettingsRepository;
import com.raditomo.user.repository.UserStationVisibilityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway でスキーマを構築し、全エンティティが Hibernate の ddl-auto=validate を
 * 通過することを確認する。エンティティとマイグレーションのカラム不一致を検知する。
 */
class SchemaValidationIT extends AbstractIT {

    @Autowired UserRepository userRepository;
    @Autowired UserSettingsRepository userSettingsRepository;
    @Autowired UserStationVisibilityRepository userStationVisibilityRepository;
    @Autowired AreaRepository areaRepository;
    @Autowired StationRepository stationRepository;
    @Autowired ProgramRepository programRepository;
    @Autowired RawProgramXmlRepository rawProgramXmlRepository;
    @Autowired DownloadRegistrationRepository downloadRegistrationRepository;
    @Autowired DownloadHistoryRepository downloadHistoryRepository;
    @Autowired PlaybackPositionRepository playbackPositionRepository;
    @Autowired BatchExecutionRepository batchExecutionRepository;

    @Test
    void contextLoadsAndAllRepositoriesUsable() {
        // Hibernate ddl-auto=validate がスキーマを通過したことの確認が主目的。
        // count() がエラーなく実行できれば各エンティティとテーブルのマッピングは整合している。
        // 他の IT との実行順依存を避けるため値の比較は最低限のみ（areas はマイグレーションで 47 件投入）。
        userRepository.count();
        userSettingsRepository.count();
        userStationVisibilityRepository.count();
        assertThat(areaRepository.count()).isEqualTo(47L);
        stationRepository.count();
        programRepository.count();
        rawProgramXmlRepository.count();
        downloadRegistrationRepository.count();
        downloadHistoryRepository.count();
        playbackPositionRepository.count();
        batchExecutionRepository.count();
    }
}
