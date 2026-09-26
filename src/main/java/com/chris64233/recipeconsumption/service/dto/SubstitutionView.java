package com.chris64233.recipeconsumption.service.dto;

import java.math.BigDecimal;

/** 替代计算预览项。 */
public record SubstitutionView(
        String recipeMaterialCode,
        String substituteMaterialCode,
        BigDecimal conversionRatio,
        BigDecimal requestedSubstituteQty,
        BigDecimal equivalentQty,
        BigDecimal priorEquivalentQty,
        BigDecimal totalEquivalentQty,
        BigDecimal demandQty,
        BigDecimal maxRatio,
        BigDecimal capQty,
        boolean allowed) {
}
