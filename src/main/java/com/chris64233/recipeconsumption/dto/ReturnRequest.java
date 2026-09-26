package com.chris64233.recipeconsumption.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/**
 * 退料请求：针对一次原始领料逐行退回到原来的批次。
 * 不提供批次时默认按原领料批次（FEFO 顺序）退；
 * 也可以在行内显式指定原批次与数量，但只能退该领料行实际扣过的批次。
 */
public record ReturnRequest(
        @NotBlank String returnNo,
        @NotBlank String issueNo,
        String reason,
        @NotEmpty @Valid List<Line> lines) {

    public record Line(
            @NotBlank String requirementMaterialCode,
            String batchNo,
            @NotNull @Positive BigDecimal quantity) {
    }
}
