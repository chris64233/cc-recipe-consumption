package com.chris64233.recipeconsumption.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MaterialBatchRepository extends JpaRepository<MaterialBatch, Long> {

    Optional<MaterialBatch> findByBatchNo(String batchNo);

    /** 悲观锁加载单个批次 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from MaterialBatch b where b.batchNo = :batchNo")
    Optional<MaterialBatch> findByBatchNoForUpdate(@Param("batchNo") String batchNo);

    /** 按批次号一次加锁加载（id 升序），避免先读锁外数据再读锁数据引发版本冲突 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from MaterialBatch b where b.batchNo in :batchNos order by b.id")
    List<MaterialBatch> findByBatchNosForUpdate(@Param("batchNos") List<String> batchNos);
}
