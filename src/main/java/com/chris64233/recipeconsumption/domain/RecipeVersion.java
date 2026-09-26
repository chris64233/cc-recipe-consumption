package com.chris64233.recipeconsumption.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 不可变的配方版本。一旦发布即冻结：标准用量、替代关系均不允许修改，
 * 生产工单绑定的就是某个具体版本，保证工单用料口径始终可追溯。
 */
@Entity
@Table(name = "recipe_version",
        uniqueConstraints = @UniqueConstraint(name = "uk_recipe_version", columnNames = {"recipe_id", "version_no"}))
public class RecipeVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(name = "version_no", nullable = false, length = 32)
    private String versionNo;

    /** 发布后冻结，目前建档即发布，预留状态位。 */
    @Column(nullable = false)
    private boolean published = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "recipeVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<RecipeIngredient> ingredients = new ArrayList<>();

    protected RecipeVersion() {
    }

    public RecipeVersion(Recipe recipe, String versionNo) {
        this.recipe = recipe;
        this.versionNo = versionNo;
        recipe.getVersions().add(this);
    }

    public void addIngredient(RecipeIngredient ingredient) {
        ingredients.add(ingredient);
    }

    public Long getId() {
        return id;
    }

    public Recipe getRecipe() {
        return recipe;
    }

    public String getVersionNo() {
        return versionNo;
    }

    public boolean isPublished() {
        return published;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<RecipeIngredient> getIngredients() {
        return ingredients;
    }

    /**
     * 按物料编码查找主料行，找不到返回 null。
     */
    public RecipeIngredient findIngredient(String materialCode) {
        for (RecipeIngredient ingredient : ingredients) {
            if (ingredient.getMaterialCode().equals(materialCode)) {
                return ingredient;
            }
        }
        return null;
    }

    /**
     * 该版本中“一单位该替代物料相当于多少单位主料”的换算系数；
     * 不是该版本允许的替代关系时返回 null。
     */
    public BigDecimal conversionFactorFor(String primaryMaterialCode, String substituteMaterialCode) {
        RecipeIngredient primary = findIngredient(primaryMaterialCode);
        if (primary == null) {
            return null;
        }
        return primary.conversionFactorFor(substituteMaterialCode);
    }
}
