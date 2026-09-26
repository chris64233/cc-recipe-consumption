package com.chris64233.recipeconsumption.domain;

import com.chris64233.recipeconsumption.support.Quantities;
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
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 生产工单。绑定一个不可变的 {@link RecipeVersion} 和计划产量，
 * 绑定后配方口径不再变化；完工后只允许查询。
 */
@Entity
@Table(name = "production_order",
        uniqueConstraints = @UniqueConstraint(name = "uk_production_order_no", columnNames = "order_no"))
public class ProductionOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, length = 64)
    private String orderNo;

    /** 不可变绑定。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_version_id", nullable = false)
    private RecipeVersion recipeVersion;

    @Column(name = "planned_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal plannedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OrderStatus status = OrderStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** 完工核对通过后的实际产量，完工前为 null。 */
    @Column(name = "actual_quantity", precision = 19, scale = 4)
    private BigDecimal actualQuantity;

    /** 完工时登记的损耗数量（折主料口径的总体损耗，按主料行分别记录在完工明细中）。 */
    @Column(name = "completed_at")
    private Instant completedAt;

    protected ProductionOrder() {
    }

    public ProductionOrder(String orderNo, RecipeVersion recipeVersion, BigDecimal plannedQuantity) {
        this.orderNo = orderNo;
        this.recipeVersion = recipeVersion;
        this.plannedQuantity = Quantities.require("plannedQuantity", plannedQuantity, true);
    }

    public void requireOpen() {
        if (status == OrderStatus.COMPLETED) {
            throw new IllegalStateException("工单 " + orderNo + " 已完工，不能继续领料、退料或调整");
        }
    }

    /** 由完工服务在产量核对通过后调用。 */
    public void markCompleted(BigDecimal actualQuantity) {
        requireOpen();
        this.actualQuantity = Quantities.require("actualQuantity", actualQuantity, true);
        this.status = OrderStatus.COMPLETED;
        this.completedAt = Instant.now();
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

    public BigDecimal getPlannedQuantity() {
        return plannedQuantity;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public BigDecimal getActualQuantity() {
        return actualQuantity;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
