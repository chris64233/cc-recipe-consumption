package com.chris64233.recipeconsumption.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AdjustmentRecordRepository extends JpaRepository<AdjustmentRecord, Long> {
    Optional<AdjustmentRecord> findByBizNo(String bizNo);
    List<AdjustmentRecord> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    List<AdjustmentRecord> findByBatchIdOrderByCreatedAtAsc(Long batchId);

    /** 工单内某配方原料累计调整的标准当量（带符号） */
    @Query("""
            select coalesce(sum(a.equivalentDelta), 0)
            from AdjustmentRecord a
            where a.order.id = :orderId
              and a.recipeMaterialCode = :recipeMaterial
            """)
    BigDecimal sumEquivalent(@Param("orderId") Long orderId,
                             @Param("recipeMaterial") String recipeMaterial);
}
