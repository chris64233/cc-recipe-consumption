package com.chris64233.recipeconsumption.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;

/**
 * 生产工单：绑定一个不可变的配方版本和计划产量。
 */
@Entity
@Table(name = "production_order")
public class ProductionOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true)
    private String orderNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_version_id")
    private RecipeVersion recipeVersion;

    @Column(name = "planned_qty", nullable = false, precision = 20, scale = 4)
    private BigDecimal plannedQty;

    /** 完工时登记的实际产量 */
    @Column(name = "actual_qty", precision = 20, scale = 4)
    private BigDecimal actualQty;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status = OrderStatus.OPEN;

    @Version
    private long version;

    protected ProductionOrder() {
    }

    public ProductionOrder(String orderNo, RecipeVersion recipeVersion, BigDecimal plannedQty) {
        this.orderNo = orderNo;
        this.recipeVersion = recipeVersion;
        this.plannedQty = plannedQty;
    }

    public void complete(BigDecimal actualQty) {
        this.actualQty = actualQty;
        this.status = OrderStatus.COMPLETED;
    }

    public Long getId() {
        return id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public RecipeVersion getRecipeVersion() {
        return recipeVersion;
    }

    public BigDecimal getPlannedQty() {
        return plannedQty;
    }

    public BigDecimal getActualQty() {
        return actualQty;
    }

    public OrderStatus getStatus() {
        return status;
    }
}
