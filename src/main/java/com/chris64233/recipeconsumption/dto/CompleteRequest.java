package com.chris64233.recipeconsumption.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 完工请求。actualQuantity 必填；lossByMaterial 为按配方主料申报的损耗（折主料口径）。
 * 未申报损耗的主料按 0 处理。
 */
public record CompleteRequest(
        @NotBlank String completionNo,
        @NotNull @Positive BigDecimal actualQuantity,
        @Valid List<LossLine> losses,
        String remark) {

    public record LossLine(
            @NotBlank String requirementMaterialCode,
            @NotNull @PositiveOrZero BigDecimal declaredLoss) {
    }

    public Map<String, BigDecimal> lossMap() {
        if (losses == null) {
            return Map.of();
        }
        return losses.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                LossLine::requirementMaterialCode, LossLine::declaredLoss, (a, b) -> b));
    }
}
