package com.chris64233.recipeconsumption.repo;

import com.chris64233.recipeconsumption.domain.MaterialReturnLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface MaterialReturnLineRepository extends JpaRepository<MaterialReturnLine, Long> {

    /** 批次去向：某批次的全部退料回补流水。 */
    List<MaterialReturnLine> findByBatchIdOrderByIdAsc(Long batchId);

    /** 某领料行+批次累计退料，用于按批次控制可退数量。 */
    @Query("""
            select coalesce(sum(r.quantity), 0)
            from MaterialReturnLine r
            where r.issueLine.id = :issueLineId
              and r.batchId = :batchId
            """)
    BigDecimal sumReturnedForIssueLineAndBatch(@Param("issueLineId") Long issueLineId,
                                               @Param("batchId") Long batchId);

    /** 工单某主料累计退料折主料量（完工核对用）。 */
    @Query("""
            select coalesce(sum(r.equivalentPrimaryQuantity), 0)
            from MaterialReturnLine r
            where r.issueLine.issue.productionOrder.id = :orderId
              and r.issueLine.requirementMaterialCode = :requirementCode
            """)
    BigDecimal sumEquivalentPrimary(@Param("orderId") Long orderId,
                                    @Param("requirementCode") String requirementCode);
}
