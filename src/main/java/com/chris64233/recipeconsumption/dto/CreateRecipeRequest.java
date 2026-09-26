package com.chris64233.recipeconsumption.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateRecipeRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String productCode) {
}
