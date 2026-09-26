package com.chris64233.recipeconsumption.repo;

import com.chris64233.recipeconsumption.domain.FulfillmentType;
import com.chris64233.recipeconsumption.domain.MaterialIssueLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface MaterialIssueLineRepository extends JpaRepository<MaterialIssueLine, Long> {

    /** 工单上某主料的累计已领（折主料量，含替代折算）。 */
    @Query("""
            select coalesce(sum(l.equivalentPrimaryQuantity), 0)
            from MaterialIssueLine l
            where l.issue.productionOrder.id = :orderId
              and l.requirementMaterialCode = :requirementCode
            """)
    BigDecimal sumEquivalentPrimary(@Param("orderId") Long orderId,
                                    @Param("requirementCode") String requirementCode);

    /** 工单上某主料累计被替代满足的折主料量（用于替代比例上限的累计校验）。 */
    @Query("""
            select coalesce(sum(l.equivalentPrimaryQuantity), 0)
            from MaterialIssueLine l
            where l.issue.productionOrder.id = :orderId
              and l.requirementMaterialCode = :requirementCode
              and l.fulfillmentType = :type
            """)
    BigDecimal sumEquivalentPrimaryByFulfillment(@Param("orderId") Long orderId,
                                                 @Param("requirementCode") String requirementCode,
                                                 @Param("type") FulfillmentType type);

    /** 工单上某主料被某一种具体替代料累计替代的折主料量。 */
    @Query("""
            select coalesce(sum(l.equivalentPrimaryQuantity), 0)
            from MaterialIssueLine l
            where l.issue.productionOrder.id = :orderId
              and l.requirementMaterialCode = :requirementCode
              and l.pickedMaterialCode = :pickedCode
              and l.fulfillmentType = :type
            """)
    BigDecimal sumEquivalentPrimaryByFulfillmentForPicked(@Param("orderId") Long orderId,
                                                          @Param("requirementCode") String requirementCode,
                                                          @Param("pickedCode") String pickedCode,
                                                          @Param("type") FulfillmentType type);
}
