package com.chris64233.recipeconsumption.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RecipeView(Long id, String code, String name, String productCode, Instant createdAt,
                         List<VersionView> versions) {
    public record VersionView(Long id, String versionNo, boolean published, Instant createdAt,
                              List<IngredientView> ingredients) {
    }

    public record IngredientView(int lineNo, String materialCode, String materialName,
                                 BigDecimal standardQuantityPerUnit, String unit,
                                 List<SubstitutionView> substitutions) {
    }

    public record SubstitutionView(String substituteMaterialCode, String substituteMaterialName,
                                   BigDecimal conversionFactor, BigDecimal maxSubstitutionRatio) {
    }
}
