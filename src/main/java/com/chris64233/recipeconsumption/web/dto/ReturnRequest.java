package com.chris64233.recipeconsumption.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** 退料请求：将领错的原料退回指定批次，原始领料记录保持不变。 */
public record ReturnRequest(
        @NotBlank String bizNo,
        @NotBlank String orderNo,
        @NotBlank String recipeMaterialCode,
        @NotBlank String batchNo,
        @NotNull @Positive BigDecimal qty,
        String reason) {
}
