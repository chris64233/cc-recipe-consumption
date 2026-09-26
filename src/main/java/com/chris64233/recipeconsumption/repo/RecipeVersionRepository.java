package com.chris64233.recipeconsumption.repo;

import com.chris64233.recipeconsumption.domain.RecipeVersion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecipeVersionRepository extends JpaRepository<RecipeVersion, Long> {

    /**
     * 连同配方行与替代关系一起抓取，工单绑定的版本是只读快照，读取时一次抓全。
     */
    @EntityGraph(attributePaths = {"recipe", "ingredients", "ingredients.substitutions"})
    Optional<RecipeVersion> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"recipe", "ingredients", "ingredients.substitutions"})
    Optional<RecipeVersion> findWithDetailsByRecipe_CodeAndVersionNo(String recipeCode, String versionNo);
}
