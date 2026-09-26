package com.chris64233.recipeconsumption.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CompletionResult(
        String orderNo,
        BigDecimal actualQty,
        Instant completedAt,
        List<LossResult> losses) {

    public record LossResult(String materialCode, BigDecimal lossQty) {
    }
}
