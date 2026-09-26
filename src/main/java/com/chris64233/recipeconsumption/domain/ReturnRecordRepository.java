package com.chris64233.recipeconsumption.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ReturnRecordRepository extends JpaRepository<ReturnRecord, Long> {
    Optional<ReturnRecord> findByBizNo(String bizNo);
    List<ReturnRecord> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    List<ReturnRecord> findByBatchIdOrderByCreatedAtAsc(Long batchId);

    /** 工单内某配方原料从某批次累计退料的实物数量 */
    @Query("""
            select coalesce(sum(r.qty), 0)
            from ReturnRecord r
            where r.order.id = :orderId
              and r.recipeMaterialCode = :recipeMaterial
              and r.batch.id = :batchId
            """)
    BigDecimal sumQty(@Param("orderId") Long orderId,
                      @Param("recipeMaterial") String recipeMaterial,
                      @Param("batchId") Long batchId);

    /** 工单内某配方原料累计退料的标准当量 */
    @Query("""
            select coalesce(sum(r.equivalentQty), 0)
            from ReturnRecord r
            where r.order.id = :orderId
              and r.recipeMaterialCode = :recipeMaterial
            """)
    BigDecimal sumEquivalent(@Param("orderId") Long orderId,
                             @Param("recipeMaterial") String recipeMaterial);
}
