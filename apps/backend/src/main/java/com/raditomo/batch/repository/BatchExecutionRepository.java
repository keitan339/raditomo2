package com.raditomo.batch.repository;

import com.raditomo.batch.entity.BatchExecution;
import com.raditomo.batch.entity.BatchStatus;
import com.raditomo.batch.entity.BatchType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BatchExecutionRepository extends JpaRepository<BatchExecution, Long> {
    List<BatchExecution> findByStatusAndBatchTypeIn(BatchStatus status, List<BatchType> batchTypes);
}
