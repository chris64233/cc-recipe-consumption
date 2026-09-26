package com.chris64233.recipeconsumption.service;

import com.chris64233.recipeconsumption.domain.IngredientSubstitution;
import com.chris64233.recipeconsumption.domain.Recipe;
import com.chris64233.recipeconsumption.domain.RecipeIngredient;
import com.chris64233.recipeconsumption.domain.RecipeVersion;
import com.chris64233.recipeconsumption.dto.AddVersionRequest;
import com.chris64233.recipeconsumption.dto.CreateRecipeRequest;
import com.chris64233.recipeconsumption.repo.RecipeRepository;
import com.chris64233.recipeconsumption.repo.RecipeVersionRepository;
import com.chris64233.recipeconsumption.support.BusinessRuleException;
import com.chris64233.recipeconsumption.support.DuplicateBusinessNoException;
import com.chris64233.recipeconsumption.support.NotFoundException;
import com.chris64233.recipeconsumption.support.Quantities;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * 配方与不可变版本建档。版本一旦写入不提供任何修改入口。
 */
@Service
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final RecipeVersionRepository versionRepository;

    public RecipeService(RecipeRepository recipeRepository, RecipeVersionRepository versionRepository) {
        this.recipeRepository = recipeRepository;
        this.versionRepository = versionRepository;
    }

    @Transactional
    public Recipe createRecipe(CreateRecipeRequest request) {
        if (recipeRepository.existsByCode(request.code())) {
            throw new DuplicateBusinessNoException("配方编码已存在: " + request.code());
        }
        return recipeRepository.save(new Recipe(request.code(), request.name(), request.productCode()));
    }

    @Transactional
    public RecipeVersion addVersion(String recipeCode, AddVersionRequest request) {
        Recipe recipe = recipeRepository.findByCode(recipeCode)
                .orElseThrow(() -> new NotFoundException("配方不存在: " + recipeCode));
        boolean versionExists = recipe.getVersions().stream()
                .anyMatch(v -> v.getVersionNo().equals(request.versionNo()));
        if (versionExists) {
            throw new DuplicateBusinessNoException(
                    "配方版本已存在且不可变: " + recipeCode + "/" + request.versionNo());
        }

        RecipeVersion version = new RecipeVersion(recipe, request.versionNo());
        Set<String> materialCodes = new HashSet<>();
        int autoLineNo = 1;
        for (AddVersionRequest.IngredientSpec spec : request.ingredients()) {
            if (!materialCodes.add(spec.materialCode())) {
                throw new BusinessRuleException("配方版本中主料重复: " + spec.materialCode());
            }
            int lineNo = spec.lineNo() == null ? autoLineNo : spec.lineNo();
            RecipeIngredient ingredient = new RecipeIngredient(
                    version, lineNo, spec.materialCode(), spec.materialName(),
                    Quantities.require("standardQuantityPerUnit", spec.standardQuantityPerUnit(), true),
                    spec.unit());
            autoLineNo++;

            if (spec.substitutions() != null) {
                Set<String> substituteCodes = new HashSet<>();
                for (AddVersionRequest.SubstitutionSpec sub : spec.substitutions()) {
                    if (!substituteCodes.add(sub.substituteMaterialCode())) {
                        throw new BusinessRuleException(
                                "主料 " + spec.materialCode() + " 的替代料重复: " + sub.substituteMaterialCode());
                    }
                    if (sub.substituteMaterialCode().equals(spec.materialCode())) {
                        throw new BusinessRuleException("替代料不能与主料相同: " + spec.materialCode());
                    }
                    BigDecimal factor = Quantities.require("conversionFactor", sub.conversionFactor(), true);
                    BigDecimal maxRatio = Quantities.normalizeRatio(sub.maxSubstitutionRatio());
                    if (maxRatio.compareTo(BigDecimal.ZERO) <= 0
                            || maxRatio.compareTo(BigDecimal.ONE) > 0) {
                        throw new BusinessRuleException(
                                "最大替代比例必须在 (0,1] 之间: " + sub.maxSubstitutionRatio());
                    }
                    new IngredientSubstitution(ingredient, sub.substituteMaterialCode(),
                            sub.substituteMaterialName(), factor, maxRatio);
                }
            }
        }
        return versionRepository.save(version);
    }

    @Transactional(readOnly = true)
    public RecipeVersion getVersion(String recipeCode, String versionNo) {
        return versionRepository.findWithDetailsByRecipe_CodeAndVersionNo(recipeCode, versionNo)
                .orElseThrow(() -> new NotFoundException("配方版本不存在: " + recipeCode + "/" + versionNo));
    }

    @Transactional(readOnly = true)
    public Recipe getRecipe(String recipeCode) {
        return recipeRepository.findByCode(recipeCode)
                .orElseThrow(() -> new NotFoundException("配方不存在: " + recipeCode));
    }
}
