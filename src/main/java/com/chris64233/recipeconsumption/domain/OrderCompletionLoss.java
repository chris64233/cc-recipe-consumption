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
 * 完工损耗行：某配方原料在本次生产中的损耗量（折标准原料）。
 */
@Entity
@Table(name = "order_completion_loss")
public class OrderCompletionLoss {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "completion_id")
    private OrderCompletion completion;

    @Column(name = "material_code", nullable = false)
    private String materialCode;

    @Column(name = "loss_qty", nullable = false, precision = 20, scale = 4)
    private BigDecimal lossQty;

    protected OrderCompletionLoss() {
    }

    public OrderCompletionLoss(String materialCode, BigDecimal lossQty) {
        this.materialCode = materialCode;
        this.lossQty = lossQty;
    }

    void setCompletion(OrderCompletion completion) {
        this.completion = completion;
    }

    public Long getId() {
        return id;
    }

    public OrderCompletion getCompletion() {
        return completion;
    }

    public String getMaterialCode() {
        return materialCode;
    }

    public BigDecimal getLossQty() {
        return lossQty;
    }
}
