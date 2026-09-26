package com.chris64233.recipeconsumption.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

/**
 * 完工请求：登记实际产量与各配方原料损耗，服务端核对
 * 净领料（折标准原料）= 实际产量标准用量 + 损耗，关系不成立时拒绝完工。
 */
public record CompleteOrderRequest(
        @NotBlank String orderNo,
        @NotNull @PositiveOrZero BigDecimal actualQty,
        @NotEmpty @Valid List<LossLine> losses) {

    public record LossLine(
            @NotBlank String materialCode,
            @NotNull @PositiveOrZero BigDecimal lossQty) {
    }
}
