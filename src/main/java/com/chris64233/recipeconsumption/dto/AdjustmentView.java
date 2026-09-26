package com.chris64233.recipeconsumption.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AdjustmentView(String adjustmentNo, String orderNo, Long batchId, String batchNo,
                             String materialCode, BigDecimal deltaQuantity,
                             String requirementMaterialCode, BigDecimal equivalentPrimaryQuantity,
                             Instant createdAt, String reason) {
}
