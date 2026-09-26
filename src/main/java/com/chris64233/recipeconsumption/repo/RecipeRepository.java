package com.chris64233.recipeconsumption.repo;

import com.chris64233.recipeconsumption.domain.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {
    Optional<Recipe> findByCode(String code);

    boolean existsByCode(String code);
}
