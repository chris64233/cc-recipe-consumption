package com.chris64233.recipeconsumption.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 调整请求：修复领料错误的另一途径（如账实不符）。
 * qtyDelta 直接调整批次库存（带符号），equivalentDelta 调整工单净用量（折标准原料，带符号）。
 */
public record AdjustmentRequest(
        @NotBlank String bizNo,
        @NotBlank String orderNo,
        @NotBlank String recipeMaterialCode,
        @NotBlank String batchNo,
        @NotNull BigDecimal qtyDelta,
        @NotNull BigDecimal equivalentDelta,
        String reason) {
}
