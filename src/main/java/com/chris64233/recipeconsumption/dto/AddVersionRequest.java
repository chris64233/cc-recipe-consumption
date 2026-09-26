package com.chris64233.recipeconsumption.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/**
 * 新增一个不可变配方版本。
 */
public record AddVersionRequest(
        @NotBlank String versionNo,
        @NotEmpty @Valid List<IngredientSpec> ingredients) {

    public record IngredientSpec(
            Integer lineNo,
            @NotBlank String materialCode,
            String materialName,
            @NotNull @Positive BigDecimal standardQuantityPerUnit,
            @NotBlank String unit,
            @Valid List<SubstitutionSpec> substitutions) {
    }

    public record SubstitutionSpec(
            @NotBlank String substituteMaterialCode,
            String substituteMaterialName,
            @NotNull @Positive BigDecimal conversionFactor,
            @NotNull @Positive(message = "最大替代比例必须大于 0")
            BigDecimal maxSubstitutionRatio) {
    }
}
