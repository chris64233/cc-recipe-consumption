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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 配方原料行：生产一单位产品所需某原料的标准用量。
 */
@Entity
@Table(name = "recipe_item")
public class RecipeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id")
    private RecipeVersion version;

    /** 配方原料编码（标准原料） */
    @Column(name = "material_code", nullable = false)
    private String materialCode;

    /** 单位产品标准用量 */
    @Column(name = "standard_qty", nullable = false, precision = 20, scale = 4)
    private BigDecimal standardQty;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecipeSubstitution> substitutions = new ArrayList<>();

    protected RecipeItem() {
    }

    public RecipeItem(String materialCode, BigDecimal standardQty) {
        this.materialCode = materialCode;
        this.standardQty = standardQty;
    }

    public void addSubstitution(RecipeSubstitution substitution) {
        substitutions.add(substitution);
        substitution.setItem(this);
    }

    void setVersion(RecipeVersion version) {
        this.version = version;
    }

    public Long getId() {
        return id;
    }

    public RecipeVersion getVersion() {
        return version;
    }

    public String getMaterialCode() {
        return materialCode;
    }

    public BigDecimal getStandardQty() {
        return standardQty;
    }

    public List<RecipeSubstitution> getSubstitutions() {
        return Collections.unmodifiableList(substitutions);
    }
}
