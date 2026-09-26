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
import java.time.Instant;

/**
 * 调整记录：用于修复领料错误（如批次库存账实不符），
 * 以带符号数量直接修正批次库存与工单用量口径，原始领料记录保持不变。
 * qtyDelta 为正表示增加批次库存，为负表示减少批次库存。
 */
@Entity
@Table(name = "adjustment_record")
public class AdjustmentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "biz_no", nullable = false, unique = true)
    private String bizNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private ProductionOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id")
    private MaterialBatch batch;

    /** 对应的配方原料编码（标准原料口径） */
    @Column(name = "recipe_material_code", nullable = false)
    private String recipeMaterialCode;

    @Column(name = "material_code", nullable = false)
    private String materialCode;

    /** 批次库存调整量（带符号） */
    @Column(name = "qty_delta", nullable = false, precision = 20, scale = 4)
    private BigDecimal qtyDelta;

    /** 工单用量调整量（折标准原料，带符号） */
    @Column(name = "equivalent_delta", nullable = false, precision = 20, scale = 4)
    private BigDecimal equivalentDelta;

    @Column(name = "reason")
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AdjustmentRecord() {
    }

    public AdjustmentRecord(String bizNo, ProductionOrder order, MaterialBatch batch,
                            String recipeMaterialCode, String materialCode,
                            BigDecimal qtyDelta, BigDecimal equivalentDelta, String reason, Instant createdAt) {
        this.bizNo = bizNo;
        this.order = order;
        this.batch = batch;
        this.recipeMaterialCode = recipeMaterialCode;
        this.materialCode = materialCode;
        this.qtyDelta = qtyDelta;
        this.equivalentDelta = equivalentDelta;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getBizNo() {
        return bizNo;
    }

    public ProductionOrder getOrder() {
        return order;
    }

    public MaterialBatch getBatch() {
        return batch;
    }

    public String getRecipeMaterialCode() {
        return recipeMaterialCode;
    }

    public String getMaterialCode() {
        return materialCode;
    }

    public BigDecimal getQtyDelta() {
        return qtyDelta;
    }

    public BigDecimal getEquivalentDelta() {
        return equivalentDelta;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
