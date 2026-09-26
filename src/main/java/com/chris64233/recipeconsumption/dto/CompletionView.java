package com.chris64233.recipeconsumption.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CompletionView(String completionNo, String orderNo, String status,
                             BigDecimal plannedQuantity,
                             BigDecimal actualQuantity, BigDecimal outputVariance,
                             boolean provisional,
                             Instant createdAt, String remark, List<LineView> lines) {

    public record LineView(String requirementMaterialCode, BigDecimal expectedConsumption,
                           BigDecimal issuedQuantity, BigDecimal substitutedQuantity,
                           BigDecimal substitutionRatio, BigDecimal returnedQuantity,
                           BigDecimal adjustedQuantity, BigDecimal declaredLoss,
                           BigDecimal unexplainedVariance, boolean balanced) {
    }
}
