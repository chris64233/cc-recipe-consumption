package com.chris64233.recipeconsumption.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateBatchRequest(
        @NotBlank String batchNo,
        @NotBlank String materialCode,
        @NotNull @Positive BigDecimal availableQty,
        @NotBlank String qualityStatus,
        @NotNull LocalDate expiryDate) {
}
