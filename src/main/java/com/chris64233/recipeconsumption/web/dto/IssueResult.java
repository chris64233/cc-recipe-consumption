package com.chris64233.recipeconsumption.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record IssueResult(
        String bizNo,
        String orderNo,
        Instant createdAt,
        List<LineResult> lines) {

    public record LineResult(
            String recipeMaterialCode,
            BigDecimal equivalentQty,
            List<BatchResult> batches) {
    }

    public record BatchResult(
            String batchNo,
            String materialCode,
            BigDecimal qty,
            BigDecimal equivalentQty,
            boolean substitute,
            BigDecimal conversionRatio) {
    }
}
