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
        assertThat(userRepository.count()).isEqualTo(0L);
        assertThat(userSettingsRepository.count()).isEqualTo(0L);
        assertThat(userStationVisibilityRepository.count()).isEqualTo(0L);
        assertThat(areaRepository.count()).isEqualTo(47L); // V2 で投入
        assertThat(stationRepository.count()).isEqualTo(0L);
        assertThat(programRepository.count()).isEqualTo(0L);
        assertThat(rawProgramXmlRepository.count()).isEqualTo(0L);
        assertThat(downloadRegistrationRepository.count()).isEqualTo(0L);
        assertThat(downloadHistoryRepository.count()).isEqualTo(0L);
        assertThat(playbackPositionRepository.count()).isEqualTo(0L);
        assertThat(batchExecutionRepository.count()).isEqualTo(0L);
    }
}
