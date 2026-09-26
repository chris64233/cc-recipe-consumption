package com.chris64233.recipeconsumption.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateOrderRequest(
        @NotBlank String orderNo,
        @NotBlank String recipeCode,
        @NotBlank String versionNo,
        @NotNull @Positive BigDecimal plannedQty) {
}
