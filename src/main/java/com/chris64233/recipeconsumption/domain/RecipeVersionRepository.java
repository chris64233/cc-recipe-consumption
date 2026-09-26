package com.chris64233.recipeconsumption.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecipeVersionRepository extends JpaRepository<RecipeVersion, Long> {
    Optional<RecipeVersion> findByRecipeCodeAndVersionNo(String recipeCode, String versionNo);
}
