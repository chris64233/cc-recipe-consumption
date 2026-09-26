package com.chris64233.recipeconsumption.dto;

import com.chris64233.recipeconsumption.domain.QualityStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateBatchRequest(
        @NotBlank String batchNo,
        @NotBlank String materialCode,
        String materialName,
        @NotNull @PositiveOrZero BigDecimal availableQuantity,
        QualityStatus qualityStatus,
        @NotNull LocalDate expiryDate) {
}
