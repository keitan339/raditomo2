package com.raditomo.batch.repository;

import com.raditomo.batch.entity.BatchExecution;
import com.raditomo.batch.entity.BatchStatus;
import com.raditomo.batch.entity.BatchType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BatchExecutionRepository extends JpaRepository<BatchExecution, Long> {

    List<BatchExecution> findByStatusAndBatchTypeIn(BatchStatus status, List<BatchType> batchTypes);

    /**
     * 排他制御用: 指定タイプ集合で RUNNING 状態のバッチに行ロックをかけて取得。
     * トランザクション内でこのメソッドを呼び、結果が空なら新しい RUNNING を INSERT する。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b FROM BatchExecution b
            WHERE b.status = :status
              AND b.batchType IN :types
            ORDER BY b.id
            """)
    List<BatchExecution> findRunningWithLock(
            @Param("status") BatchStatus status,
            @Param("types") List<BatchType> types);
}
