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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 配方版本中的一条主料标准用量（单位：每 1 单位计划产量耗用的主料数量）。
 * 工单的标准需用量 = 标准用量 × 计划产量。
 */
@Entity
@Table(name = "recipe_ingredient",
        uniqueConstraints = @UniqueConstraint(name = "uk_recipe_ingredient",
                columnNames = {"recipe_version_id", "material_code"}))
public class RecipeIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_version_id", nullable = false)
    private RecipeVersion recipeVersion;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "material_code", nullable = false, length = 64)
    private String materialCode;

    @Column(name = "material_name", length = 128)
    private String materialName;

    /** 单位计划产量的标准用量（主料自身单位）。 */
    @Column(name = "standard_quantity_per_unit", nullable = false, precision = 19, scale = 4)
    private BigDecimal standardQuantityPerUnit;

    @Column(nullable = false, length = 16)
    private String unit;

    @OneToMany(mappedBy = "primaryIngredient", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<IngredientSubstitution> substitutions = new LinkedHashSet<>();

    protected RecipeIngredient() {
    }

    public RecipeIngredient(RecipeVersion recipeVersion, int lineNo, String materialCode, String materialName,
                            BigDecimal standardQuantityPerUnit, String unit) {
        this.recipeVersion = recipeVersion;
        this.lineNo = lineNo;
        this.materialCode = materialCode;
        this.materialName = materialName;
        this.standardQuantityPerUnit = standardQuantityPerUnit;
        this.unit = unit;
        recipeVersion.addIngredient(this);
    }

    public void addSubstitution(IngredientSubstitution substitution) {
        substitutions.add(substitution);
    }

    /**
     * 查找允许的替代关系；不存在返回 null。
     */
    public IngredientSubstitution findSubstitution(String substituteMaterialCode) {
        for (IngredientSubstitution substitution : substitutions) {
            if (substitution.getSubstituteMaterialCode().equals(substituteMaterialCode)) {
                return substitution;
            }
        }
        return null;
    }
    /**
     * “一单位替代料折合多少单位主料”的系数；不允许替代时返回 null。
     */
    public BigDecimal conversionFactorFor(String substituteMaterialCode) {
        IngredientSubstitution substitution = findSubstitution(substituteMaterialCode);
        return substitution == null ? null : substitution.getConversionFactor();
    }

    public Long getId() {
        return id;
    }

    public RecipeVersion getRecipeVersion() {
        return recipeVersion;
    }

    public int getLineNo() {
        return lineNo;
    }

    public String getMaterialCode() {
        return materialCode;
    }

    public String getMaterialName() {
        return materialName;
    }

    public BigDecimal getStandardQuantityPerUnit() {
        return standardQuantityPerUnit;
    }

    public String getUnit() {
        return unit;
    }

    public Set<IngredientSubstitution> getSubstitutions() {
        return substitutions;
    }
}
