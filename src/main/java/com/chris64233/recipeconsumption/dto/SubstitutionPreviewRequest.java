package com.chris64233.recipeconsumption.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/**
 * 替代计算（试算）请求：给定计划产量和一组替代选择，给出主料/替代料需求量、
 * 换算数量以及是否突破最大替代比例。不落库、不扣库存。
 */
public record SubstitutionPreviewRequest(
        @NotEmpty @Valid List<Line> lines) {

    public record Line(
            String requirementMaterialCode,
            String substituteMaterialCode,
            @NotNull @Positive BigDecimal substituteEquivalentQuantity) {
    }
}
