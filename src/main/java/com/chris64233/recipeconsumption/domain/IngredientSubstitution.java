package com.chris64233.recipeconsumption.domain;

import org.hibernate.annotations.Check;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

/**
 * 受控替代关系：某主料允许用哪种替代料、如何换算、最多替代多少。
 *
 * <ul>
 *   <li>{@code conversionFactor}：1 单位替代料折合的主料数量（主料量 = 替代料量 × 系数）；</li>
 *   <li>{@code maxSubstitutionRatio}：被替代量占该主料标准需用量的最大比例，0~1。</li>
 * </ul>
 */
@Entity
@Table(name = "ingredient_substitution",
        uniqueConstraints = @UniqueConstraint(name = "uk_ingredient_substitution",
                columnNames = {"primary_ingredient_id", "substitute_material_code"}))
@Check(constraints = "conversion_factor > 0 and max_substitution_ratio > 0 and max_substitution_ratio <= 1")
public class IngredientSubstitution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "primary_ingredient_id", nullable = false)
    private RecipeIngredient primaryIngredient;

    @Column(name = "substitute_material_code", nullable = false, length = 64)
    private String substituteMaterialCode;

    @Column(name = "substitute_material_name", length = 128)
    private String substituteMaterialName;

    /** 1 单位替代料折合多少单位主料。 */
    @Column(name = "conversion_factor", nullable = false, precision = 19, scale = 6)
    private BigDecimal conversionFactor;

    /** 最大替代比例（按折主料量 / 主料标准需用量计算），范围 (0,1]。 */
    @Column(name = "max_substitution_ratio", nullable = false, precision = 9, scale = 6)
    private BigDecimal maxSubstitutionRatio;

    protected IngredientSubstitution() {
    }

    public IngredientSubstitution(RecipeIngredient primaryIngredient, String substituteMaterialCode,
                                  String substituteMaterialName, BigDecimal conversionFactor,
                                  BigDecimal maxSubstitutionRatio) {
        this.primaryIngredient = primaryIngredient;
        this.substituteMaterialCode = substituteMaterialCode;
        this.substituteMaterialName = substituteMaterialName;
        this.conversionFactor = conversionFactor;
        this.maxSubstitutionRatio = maxSubstitutionRatio;
        primaryIngredient.addSubstitution(this);
    }

    public Long getId() {
        return id;
    }

    public RecipeIngredient getPrimaryIngredient() {
        return primaryIngredient;
    }

    public String getSubstituteMaterialCode() {
        return substituteMaterialCode;
    }

    public String getSubstituteMaterialName() {
        return substituteMaterialName;
    }

    public BigDecimal getConversionFactor() {
        return conversionFactor;
    }

    public BigDecimal getMaxSubstitutionRatio() {
        return maxSubstitutionRatio;
    }
}
