package com.chris64233.recipeconsumption.service.dto;

import java.math.BigDecimal;
import java.util.List;

/** 产量差异查询：计划产量、实际产量及逐原料的净领料/应耗/损耗/差异。 */
public record ProductionVarianceView(
        String orderNo,
        String status,
        BigDecimal plannedQty,
        BigDecimal actualQty,
        BigDecimal outputVariance,
        List<MaterialVariance> materials) {

    public record MaterialVariance(
            String materialCode,
            BigDecimal netIssuedQty,
            BigDecimal standardConsumption,
            BigDecimal declaredLossQty,
            BigDecimal unexplainedVariance) {
    }
}
