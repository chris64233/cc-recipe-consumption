package com.chris64233.recipeconsumption.service.dto;

import java.math.BigDecimal;
import java.util.List;

/** 工单用料查询：按配方原料汇总净领料及替代构成。 */
public record OrderMaterialUsageView(
        Long orderId,
        String orderNo,
        String recipeCode,
        String versionNo,
        BigDecimal plannedQty,
        BigDecimal actualQty,
        String status,
        List<MaterialUsage> materials) {

    public record MaterialUsage(
            String recipeMaterialCode,
            BigDecimal standardQty,
            BigDecimal demandQty,
            BigDecimal issuedEquivalentQty,
            BigDecimal returnedEquivalentQty,
            BigDecimal adjustedEquivalentQty,
            BigDecimal netEquivalentQty,
            List<SubstituteUsage> substitutes) {
    }

    public record SubstituteUsage(
            String materialCode,
            BigDecimal equivalentQty,
            BigDecimal ratioInDemand,
            boolean overMaxRatio) {
    }
}
