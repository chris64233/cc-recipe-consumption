package com.chris64233.recipeconsumption.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 完工记录：登记实际产量与各配方原料的损耗，
 * 保存时数量关系（净领料 = 实际产量消耗 + 损耗）已核对成立。
 */
@Entity
@Table(name = "order_completion")
public class OrderCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", unique = true)
    private ProductionOrder order;

    @Column(name = "actual_qty", nullable = false, precision = 20, scale = 4)
    private BigDecimal actualQty;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @OneToMany(mappedBy = "completion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderCompletionLoss> losses = new ArrayList<>();

    protected OrderCompletion() {
    }

    public OrderCompletion(ProductionOrder order, BigDecimal actualQty, Instant completedAt) {
        this.order = order;
        this.actualQty = actualQty;
        this.completedAt = completedAt;
    }

    public void addLoss(OrderCompletionLoss loss) {
        losses.add(loss);
        loss.setCompletion(this);
    }

    public Long getId() {
        return id;
    }

    public ProductionOrder getOrder() {
        return order;
    }

    public BigDecimal getActualQty() {
        return actualQty;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public List<OrderCompletionLoss> getLosses() {
        return Collections.unmodifiableList(losses);
    }
}
