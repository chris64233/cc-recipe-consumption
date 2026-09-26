package com.chris64233.recipeconsumption.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * 替代关系：允许用 substituteMaterialCode 替代配方原料。
 * conversionRatio 表示 1 单位替代料折合多少单位标准原料（标准当量 = 替代料数量 × conversionRatio）。
 * maxRatio 表示该替代料的标准当量占配方原料总需求量的最大比例（0~1）。
 */
@Entity
@Table(name = "recipe_substitution")
public class RecipeSubstitution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id")
    private RecipeItem item;

    @Column(name = "substitute_material_code", nullable = false)
    private String substituteMaterialCode;

    /** 替代料 -> 标准原料的换算系数 */
    @Column(name = "conversion_ratio", nullable = false, precision = 20, scale = 6)
    private BigDecimal conversionRatio;

    /** 最大替代比例（标准当量口径，0~1） */
    @Column(name = "max_ratio", nullable = false, precision = 20, scale = 6)
    private BigDecimal maxRatio;

    protected RecipeSubstitution() {
    }

    public RecipeSubstitution(String substituteMaterialCode, BigDecimal conversionRatio, BigDecimal maxRatio) {
        this.substituteMaterialCode = substituteMaterialCode;
        this.conversionRatio = conversionRatio;
        this.maxRatio = maxRatio;
    }

    void setItem(RecipeItem item) {
        this.item = item;
    }

    public Long getId() {
        return id;
    }

    public RecipeItem getItem() {
        return item;
    }

    public String getSubstituteMaterialCode() {
        return substituteMaterialCode;
    }

    public BigDecimal getConversionRatio() {
        return conversionRatio;
    }

    public BigDecimal getMaxRatio() {
        return maxRatio;
    }
}
