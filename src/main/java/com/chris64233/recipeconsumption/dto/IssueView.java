package com.chris64233.recipeconsumption.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record IssueView(String issueNo, String orderNo, Instant createdAt, String remark,
                        List<LineView> lines) {

    public record LineView(int lineNo, String requirementMaterialCode, String fulfillmentType,
                           String pickedMaterialCode, String pickedMaterialName,
                           BigDecimal pickedQuantity, BigDecimal conversionFactor,
                           BigDecimal equivalentPrimaryQuantity,
                           List<AllocationView> allocations) {
    }

    public record AllocationView(Long batchId, String batchNo, String materialCode, BigDecimal quantity) {
    }
}
