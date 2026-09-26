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
 * 领料批次扣减明细：某次领料从某个批次扣减的数量。
 * qty 为批次原料实物数量，equivalentQty 为折合标准原料数量。
 */
@Entity
@Table(name = "issue_line_batch")
public class IssueLineBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "line_id")
    private IssueLine line;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id")
    private MaterialBatch batch;

    /** 批次实际原料编码（可能是替代料） */
    @Column(name = "material_code", nullable = false)
    private String materialCode;

    /** 扣减的实物数量（批次原料单位） */
    @Column(name = "qty", nullable = false, precision = 20, scale = 4)
    private BigDecimal qty;

    /** 折合标准原料数量 */
    @Column(name = "equivalent_qty", nullable = false, precision = 20, scale = 4)
    private BigDecimal equivalentQty;

    @Column(name = "substitute", nullable = false)
    private boolean substitute;

    /** 本次扣减使用的换算系数（标准原料为 1） */
    @Column(name = "conversion_ratio", nullable = false, precision = 20, scale = 6)
    private BigDecimal conversionRatio;

    protected IssueLineBatch() {
    }

    public IssueLineBatch(MaterialBatch batch, String materialCode, BigDecimal qty,
                          BigDecimal equivalentQty, boolean substitute, BigDecimal conversionRatio) {
        this.batch = batch;
        this.materialCode = materialCode;
        this.qty = qty;
        this.equivalentQty = equivalentQty;
        this.substitute = substitute;
        this.conversionRatio = conversionRatio;
    }

    void setLine(IssueLine line) {
        this.line = line;
    }

    public Long getId() {
        return id;
    }

    public IssueLine getLine() {
        return line;
    }

    public MaterialBatch getBatch() {
        return batch;
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

    public boolean isSubstitute() {
        return substitute;
    }

    public BigDecimal getConversionRatio() {
        return conversionRatio;
    }
}
