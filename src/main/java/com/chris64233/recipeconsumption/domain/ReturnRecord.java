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
 * 退料记录：将领错的原料退回批次库存，原始领料记录保持不变。
 * bizNo 为幂等业务号。
 */
@Entity
@Table(name = "return_record")
public class ReturnRecord {

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

    /** 退回的批次原料编码 */
    @Column(name = "material_code", nullable = false)
    private String materialCode;

    /** 退回实物数量 */
    @Column(name = "qty", nullable = false, precision = 20, scale = 4)
    private BigDecimal qty;

    /** 退回数量折合标准原料数量 */
    @Column(name = "equivalent_qty", nullable = false, precision = 20, scale = 4)
    private BigDecimal equivalentQty;

    @Column(name = "reason")
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReturnRecord() {
    }

    public ReturnRecord(String bizNo, ProductionOrder order, MaterialBatch batch,
                        String recipeMaterialCode, String materialCode,
                        BigDecimal qty, BigDecimal equivalentQty, String reason, Instant createdAt) {
        this.bizNo = bizNo;
        this.order = order;
        this.batch = batch;
        this.recipeMaterialCode = recipeMaterialCode;
        this.materialCode = materialCode;
        this.qty = qty;
        this.equivalentQty = equivalentQty;
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

    public BigDecimal getQty() {
        return qty;
    }

    public BigDecimal getEquivalentQty() {
        return equivalentQty;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
