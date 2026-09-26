package com.chris64233.recipeconsumption.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ReturnView(String returnNo, String orderNo, String issueNo, Instant createdAt,
                         String reason, List<LineView> lines) {

    public record LineView(String requirementMaterialCode, Long batchId, String batchNo,
                           String materialCode, BigDecimal quantity,
                           BigDecimal equivalentPrimaryQuantity) {
    }
}
