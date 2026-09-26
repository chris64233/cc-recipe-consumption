package com.chris64233.recipeconsumption.repo;

import com.chris64233.recipeconsumption.domain.InventoryAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, Long> {

    Optional<InventoryAdjustment> findByAdjustmentNo(String adjustmentNo);

    boolean existsByAdjustmentNo(String adjustmentNo);

    List<InventoryAdjustment> findByBatchIdOrderByCreatedAtAsc(Long batchId);

    /**
     * 工单某主料的调整净值（折主料；正=冲回，负=核销）。完工核对按净值参与配平。
     */
    @Query("""
            select coalesce(sum(a.equivalentPrimaryQuantity), 0)
            from InventoryAdjustment a
            where a.productionOrder.id = :orderId
              and a.requirementMaterialCode = :requirementCode
            """)
    BigDecimal sumEquivalentPrimary(@Param("orderId") Long orderId,
                                    @Param("requirementCode") String requirementCode);
}
