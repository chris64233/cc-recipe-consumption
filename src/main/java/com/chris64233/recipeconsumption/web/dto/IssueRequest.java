package com.chris64233.recipeconsumption.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/**
 * 领料请求。一次领料可包含多个配方原料行，每行可从多个批次扣减，
 * 批次的原料可以是标准原料，也可以是该原料允许的替代料。
 */
public record IssueRequest(
        @NotBlank String bizNo,
        @NotBlank String orderNo,
        @NotEmpty @Valid List<Line> lines) {

    public record Line(
            @NotBlank String recipeMaterialCode,
            @NotEmpty @Valid List<BatchPick> batches) {
    }

    public record BatchPick(
            @NotBlank String batchNo,
            @NotNull @Positive BigDecimal qty) {
    }
}
