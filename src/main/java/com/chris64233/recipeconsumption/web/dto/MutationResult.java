package com.chris64233.recipeconsumption.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MutationResult(
        String bizNo,
        String orderNo,
        String batchNo,
        String recipeMaterialCode,
        String materialCode,
        BigDecimal qty,
        BigDecimal equivalentQty,
        Instant createdAt) {
}
