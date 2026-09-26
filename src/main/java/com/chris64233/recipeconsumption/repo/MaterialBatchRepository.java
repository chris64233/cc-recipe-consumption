package com.chris64233.recipeconsumption.repo;

import com.chris64233.recipeconsumption.domain.MaterialBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MaterialBatchRepository extends JpaRepository<MaterialBatch, Long> {

    Optional<MaterialBatch> findByBatchNo(String batchNo);

    List<MaterialBatch> findByMaterialCodeOrderByExpiryDateAscIdAsc(String materialCode);

    /**
     * 悲观锁定若干物料的全部批次行。领料事务内最先调用，与工单行锁共同保证并发领料不会超扣。
     * 按主键排序以固定加锁顺序，避免死锁。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from MaterialBatch b where b.materialCode in :codes order by b.id asc")
    List<MaterialBatch> findForUpdateByMaterialCodes(@Param("codes") Collection<String> codes);

    /**
     * 悲观锁定单个批次（退料、调整时使用）。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from MaterialBatch b where b.batchNo = :batchNo")
    Optional<MaterialBatch> findForUpdateByBatchNo(@Param("batchNo") String batchNo);

    /**
     * 悲观锁定多个批次（退料分散回补时使用）。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from MaterialBatch b where b.id in :ids order by b.id asc")
    List<MaterialBatch> findForUpdateByIds(@Param("ids") Collection<Long> ids);
}
