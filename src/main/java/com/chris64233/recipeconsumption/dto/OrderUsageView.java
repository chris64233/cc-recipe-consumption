package com.chris64233.recipeconsumption.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 工单用料查询：按配方主料汇总标准需用、已领（区分主料/替代）、退料、调整净值与替代占比。
 */
public record OrderUsageView(String orderNo, String recipeCode, String versionNo,
                             BigDecimal plannedQuantity, String status, List<LineView> lines) {

    public record LineView(String requirementMaterialCode, String materialName,
                           BigDecimal standardQuantityPerUnit, BigDecimal standardRequiredQuantity,
                           BigDecimal issuedPrimaryQuantity, BigDecimal issuedSubstituteQuantity,
                           BigDecimal issuedTotalEquivalent, BigDecimal returnedQuantity,
                           BigDecimal adjustedNetQuantity, BigDecimal netConsumedQuantity,
                           BigDecimal substitutionRatio, BigDecimal maxSubstitutionRatio,
                           boolean substitutionWithinLimit) {
    }
}
