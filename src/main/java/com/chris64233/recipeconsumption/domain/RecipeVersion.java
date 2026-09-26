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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 配方版本：包含一组原料标准用量，版本一旦创建不可修改，
 * 生产工单绑定后配方内容保持稳定。
 */
@Entity
@Table(name = "recipe_version",
        uniqueConstraints = @UniqueConstraint(columnNames = {"recipe_code", "version_no"}))
public class RecipeVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipe_code", nullable = false)
    private String recipeCode;

    @Column(name = "version_no", nullable = false)
    private String versionNo;

    /** 产出物（产品）编码 */
    @Column(name = "product_code", nullable = false)
    private String productCode;

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecipeItem> items = new ArrayList<>();

    protected RecipeVersion() {
    }

    public RecipeVersion(String recipeCode, String versionNo, String productCode) {
        this.recipeCode = recipeCode;
        this.versionNo = versionNo;
        this.productCode = productCode;
    }

    public void addItem(RecipeItem item) {
        items.add(item);
        item.setVersion(this);
    }

    public Long getId() {
        return id;
    }

    public String getRecipeCode() {
        return recipeCode;
    }

    public String getVersionNo() {
        return versionNo;
    }

    public String getProductCode() {
        return productCode;
    }

    public List<RecipeItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
