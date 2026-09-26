package com.chris64233.recipeconsumption.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface IssueLineBatchRepository extends JpaRepository<IssueLineBatch, Long> {

    /** 工单内某配方原料从某实物原料（标准或替代料）累计领用的标准当量 */
    @Query("""
            select coalesce(sum(ilb.equivalentQty), 0)
            from IssueLineBatch ilb
            where ilb.line.recipeMaterialCode = :recipeMaterial
              and ilb.materialCode = :materialCode
              and ilb.line.record.order.id = :orderId
            """)
    BigDecimal sumEquivalent(@Param("orderId") Long orderId,
                             @Param("recipeMaterial") String recipeMaterial,
                             @Param("materialCode") String materialCode);

    /** 工单内某配方原料累计领用的标准当量 */
    @Query("""
            select coalesce(sum(ilb.equivalentQty), 0)
            from IssueLineBatch ilb
            where ilb.line.recipeMaterialCode = :recipeMaterial
              and ilb.line.record.order.id = :orderId
            """)
    BigDecimal sumEquivalentByRecipeMaterial(@Param("orderId") Long orderId,
                                             @Param("recipeMaterial") String recipeMaterial);

    /** 工单全部批次扣减明细 */
    @Query("""
            select ilb from IssueLineBatch ilb
            where ilb.line.record.order.id = :orderId
            order by ilb.line.record.createdAt, ilb.id
            """)
    List<IssueLineBatch> findByOrderId(@Param("orderId") Long orderId);

    @Query("""
            select ilb from IssueLineBatch ilb
            where ilb.batch.id = :batchId
            order by ilb.line.record.createdAt, ilb.id
            """)
    List<IssueLineBatch> findByBatchId(@Param("batchId") Long batchId);

    /** 工单内某配方原料从某批次累计领料的实物数量 */
    @Query("""
            select coalesce(sum(ilb.qty), 0)
            from IssueLineBatch ilb
            where ilb.line.record.order.id = :orderId
              and ilb.line.recipeMaterialCode = :recipeMaterial
              and ilb.batch.id = :batchId
            """)
    BigDecimal sumQtyByBatch(@Param("orderId") Long orderId,
                             @Param("recipeMaterial") String recipeMaterial,
                             @Param("batchId") Long batchId);
}
