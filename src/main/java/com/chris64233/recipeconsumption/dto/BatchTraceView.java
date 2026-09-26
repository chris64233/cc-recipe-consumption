package com.chris64233.recipeconsumption.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 批次去向查询：一个批次被哪些工单/领料单扣减、被哪些退料回补、被哪些调整盘动。
 */
public record BatchTraceView(String batchNo, String materialCode, String materialName,
                             BigDecimal currentAvailableQuantity, String qualityStatus,
                             List<IssueFlow> issues, List<ReturnFlow> returns,
                             List<AdjustmentFlow> adjustments) {

    public record IssueFlow(String orderNo, String issueNo, Instant issueTime,
                            String requirementMaterialCode, String fulfillmentType,
                            BigDecimal quantity) {
    }

    public record ReturnFlow(String orderNo, String returnNo, Instant returnTime,
                             String issueNo, BigDecimal quantity) {
    }

    public record AdjustmentFlow(String orderNo, String adjustmentNo, Instant adjustmentTime,
                                 BigDecimal deltaQuantity, String reason) {
    }
}
