package com.raditomo.batch.service;

import com.raditomo.batch.entity.*;
import com.raditomo.batch.repository.BatchExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchExecutionServiceTest {

    @Mock BatchExecutionRepository repository;
    BatchExecutionService service;

    @BeforeEach
    void setup() {
        Clock clock = Clock.fixed(Instant.parse("2026-04-27T00:00:00Z"), ZoneOffset.UTC);
        service = new BatchExecutionService(repository, clock);
    }

    @Test
    void exclusiveSetOf_f4_blocksF4andF4F2() {
        assertThat(BatchExecutionService.exclusiveSetOf(BatchType.F4))
                .containsExactlyInAnyOrder(BatchType.F4, BatchType.F4_F2);
    }

    @Test
    void exclusiveSetOf_f2_blocksF2andF4F2() {
        assertThat(BatchExecutionService.exclusiveSetOf(BatchType.F2))
                .containsExactlyInAnyOrder(BatchType.F2, BatchType.F4_F2);
    }

    @Test
    void exclusiveSetOf_f4f2_blocksAll() {
        assertThat(BatchExecutionService.exclusiveSetOf(BatchType.F4_F2))
                .containsExactlyInAnyOrder(BatchType.F4, BatchType.F2, BatchType.F4_F2);
    }

    @Test
    void start_insertsRunningWhenNothingExclusiveIsRunning() {
        when(repository.findRunningWithLock(eq(BatchStatus.RUNNING), any())).thenReturn(List.of());
        when(repository.save(any(BatchExecution.class))).thenAnswer(inv -> {
            BatchExecution e = inv.getArgument(0);
            e.setId(101L);
            return e;
        });

        BatchExecution started = service.start(BatchType.F4, TriggeredBy.SCHEDULER, null, null);

        assertThat(started.getId()).isEqualTo(101L);
        assertThat(started.getStatus()).isEqualTo(BatchStatus.RUNNING);
        assertThat(started.getBatchType()).isEqualTo(BatchType.F4);
    }

    @Test
    void start_throwsWhenExclusiveBatchIsRunning() {
        BatchExecution running = BatchExecution.builder()
                .id(99L).batchType(BatchType.F4_F2).status(BatchStatus.RUNNING).build();
        when(repository.findRunningWithLock(eq(BatchStatus.RUNNING), any())).thenReturn(List.of(running));

        assertThatThrownBy(() -> service.start(BatchType.F4, TriggeredBy.WEB, 1L, null))
                .isInstanceOf(BatchExecutionService.BatchAlreadyRunningException.class)
                .satisfies(e -> {
                    var ex = (BatchExecutionService.BatchAlreadyRunningException) e;
                    assertThat(ex.getRunning().getId()).isEqualTo(99L);
                });
        verify(repository, never()).save(any());
    }

    @Test
    void complete_setsStatusAndFinishedAt() {
        BatchExecution running = BatchExecution.builder()
                .id(77L).batchType(BatchType.F2).status(BatchStatus.RUNNING).build();
        when(repository.findById(77L)).thenReturn(Optional.of(running));
        when(repository.save(any(BatchExecution.class))).thenAnswer(inv -> inv.getArgument(0));

        BatchExecution completed = service.complete(77L, BatchStatus.SUCCESS, "ok");

        assertThat(completed.getStatus()).isEqualTo(BatchStatus.SUCCESS);
        assertThat(completed.getSummary()).isEqualTo("ok");
        assertThat(completed.getFinishedAt()).isNotNull();
    }
}
