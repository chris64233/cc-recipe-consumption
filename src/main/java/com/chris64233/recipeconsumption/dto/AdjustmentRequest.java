package com.chris64233.recipeconsumption.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 库存调整。orderNo 可空（纯盘点）；挂工单时 requirementMaterialCode 为该批次物料
 * 折入的配方主料编码（替代料批次可指定其替代的主料）。
 */
public record AdjustmentRequest(
        @NotBlank String adjustmentNo,
        String orderNo,
        @NotBlank String batchNo,
        @NotNull BigDecimal deltaQuantity,
        String requirementMaterialCode,
        String reason) {
}
