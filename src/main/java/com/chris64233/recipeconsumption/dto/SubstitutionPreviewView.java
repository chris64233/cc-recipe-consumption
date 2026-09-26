package com.chris64233.recipeconsumption.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 替代计算（试算）结果：给定想要替代的折主料量，算出实领替代料数量、
 * 当前累计替代占比、试算后的占比以及是否仍在最大替代比例内。不落库。
 */
public record SubstitutionPreviewView(String orderNo, BigDecimal plannedQuantity, List<LineView> lines) {

    public record LineView(String requirementMaterialCode, String substituteMaterialCode,
                           BigDecimal conversionFactor, BigDecimal requestedEquivalentQuantity,
                           BigDecimal requiredPickedQuantity, BigDecimal standardRequiredQuantity,
                           BigDecimal maxSubstitutionRatio, BigDecimal maxAllowedEquivalentQuantity,
                           BigDecimal alreadySubstitutedEquivalent,
                           BigDecimal projectedSubstitutionRatio, boolean withinLimit) {
    }
}
