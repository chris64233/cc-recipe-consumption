package com.chris64233.recipeconsumption.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public record CreateRecipeRequest(
        @NotBlank String recipeCode,
        @NotBlank String versionNo,
        @NotBlank String productCode,
        @NotEmpty @Valid List<Item> items) {

    public record Item(
            @NotBlank String materialCode,
            @NotNull @Positive BigDecimal standardQty,
            @Valid List<Substitution> substitutions) {
    }

    public record Substitution(
            @NotBlank String substituteMaterialCode,
            @NotNull @Positive BigDecimal conversionRatio,
            @NotNull BigDecimal maxRatio) {
    }
}
