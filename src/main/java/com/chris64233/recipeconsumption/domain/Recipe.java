package com.chris64233.recipeconsumption.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 配方（产品的配方族），其下拥有多个不可变的 {@link RecipeVersion}。
 */
@Entity
@Table(name = "recipe", uniqueConstraints = @UniqueConstraint(name = "uk_recipe_code", columnNames = "code"))
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    /** 配方产出的物料编码。 */
    @Column(name = "product_code", nullable = false, length = 64)
    private String productCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecipeVersion> versions = new ArrayList<>();

    protected Recipe() {
    }

    public Recipe(String code, String name, String productCode) {
        this.code = code;
        this.name = name;
        this.productCode = productCode;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getProductCode() {
        return productCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<RecipeVersion> getVersions() {
        return versions;
    }
}
