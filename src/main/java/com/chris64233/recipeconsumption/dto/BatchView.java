package com.chris64233.recipeconsumption.dto;

import com.chris64233.recipeconsumption.domain.QualityStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BatchView(Long id, String batchNo, String materialCode, String materialName,
                        BigDecimal availableQuantity, QualityStatus qualityStatus,
                        LocalDate expiryDate, boolean usable, long version) {
}
