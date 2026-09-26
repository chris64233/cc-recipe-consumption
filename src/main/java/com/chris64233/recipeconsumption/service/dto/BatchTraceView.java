package com.chris64233.recipeconsumption.service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 批次去向查询：批次当前库存及其在各工单上的领、退、调整流水。 */
public record BatchTraceView(
        String batchNo,
        String materialCode,
        BigDecimal availableQty,
        String qualityStatus,
        List<Movement> movements) {

    public record Movement(
            String type,
            Instant time,
            String bizNo,
            String orderNo,
            String recipeMaterialCode,
            BigDecimal qty,
            BigDecimal equivalentQty,
            String reason) {
    }
}
