package com.raditomo.batch.service;

import com.raditomo.batch.entity.BatchExecution;
import com.raditomo.batch.entity.BatchStatus;
import com.raditomo.batch.entity.BatchType;
import com.raditomo.batch.entity.TriggeredBy;
import com.raditomo.batch.repository.BatchExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class StaleRunningBatchCleanerTest {

    private BatchExecutionRepository repo;
    private StaleRunningBatchCleaner cleaner;
    private Clock fixedClock;

    @BeforeEach
    void setup() {
        repo = mock(BatchExecutionRepository.class);
        fixedClock = Clock.fixed(Instant.parse("2026-04-30T12:00:00Z"), ZoneOffset.UTC);
        cleaner = new StaleRunningBatchCleaner(repo, fixedClock);
    }

    @Test
    void marksStaleRunningBatchesAsFailed() {
        BatchExecution stale = new BatchExecution();
        stale.setId(10L);
        stale.setBatchType(BatchType.F2);
        stale.setTriggeredBy(TriggeredBy.WEB);
        stale.setStatus(BatchStatus.RUNNING);
        stale.setStartedAt(OffsetDateTime.now(fixedClock).minusHours(1));
        when(repo.findByStatusAndBatchTypeIn(eq(BatchStatus.RUNNING), anyList()))
                .thenReturn(List.of(stale));

        cleaner.cleanupOnStartup();

        assertThat(stale.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(stale.getFinishedAt()).isEqualTo(OffsetDateTime.now(fixedClock));
        assertThat(stale.getSummary()).isEqualTo("Interrupted by application restart");
        verify(repo).saveAll(anyList());
    }

    @Test
    void doesNothingIfNoStaleRunning() {
        when(repo.findByStatusAndBatchTypeIn(eq(BatchStatus.RUNNING), anyList()))
                .thenReturn(List.of());

        cleaner.cleanupOnStartup();

        verify(repo, never()).saveAll(any());
    }

    @Test
    void preservesExistingSummaryAndAppendsReason() {
        BatchExecution stale = new BatchExecution();
        stale.setId(11L);
        stale.setBatchType(BatchType.F4);
        stale.setTriggeredBy(TriggeredBy.SCHEDULER);
        stale.setStatus(BatchStatus.RUNNING);
        stale.setStartedAt(OffsetDateTime.now(fixedClock).minusMinutes(10));
        stale.setSummary("F4 areas=1 dates=15 partial");
        when(repo.findByStatusAndBatchTypeIn(eq(BatchStatus.RUNNING), anyList()))
                .thenReturn(List.of(stale));

        cleaner.cleanupOnStartup();

        assertThat(stale.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(stale.getSummary())
                .isEqualTo("F4 areas=1 dates=15 partial | Interrupted by application restart");
    }
}
