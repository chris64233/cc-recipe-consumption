package com.chris64233.recipeconsumption.dto;

import com.chris64233.recipeconsumption.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderView(Long id, String orderNo, String recipeCode, String versionNo,
                        BigDecimal plannedQuantity, OrderStatus status,
                        BigDecimal actualQuantity, Instant createdAt, Instant completedAt) {
}
