package com.chris64233.recipeconsumption.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/**
 * 一次领料请求。每行针对一个配方主料：
 * <ul>
 *   <li>不填 pickedMaterialCode 表示领主料；</li>
 *   <li>填写为已登记的替代料编码表示领替代料，数量按配方系数换算并受最大替代比例约束。</li>
 * </ul>
 */
public record IssueRequest(
        @NotBlank String issueNo,
        String remark,
        @NotEmpty @Valid List<Line> lines) {

    public record Line(
            @NotBlank String requirementMaterialCode,
            String pickedMaterialCode,
            @NotNull @Positive BigDecimal pickedQuantity) {
    }
}
