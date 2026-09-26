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
 * 领料在单个原料批次上的扣减明细，是“批次去向”追溯的最小单元。
 */
@Entity
@Table(name = "material_issue_batch_alloc")
public class MaterialIssueBatchAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_line_id", nullable = false)
    private MaterialIssueLine issueLine;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "batch_no", nullable = false, length = 64)
    private String batchNo;

    @Column(name = "material_code", nullable = false, length = 64)
    private String materialCode;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    protected MaterialIssueBatchAllocation() {
    }

    public MaterialIssueBatchAllocation(MaterialIssueLine issueLine, MaterialBatch batch, BigDecimal quantity) {
        this.issueLine = issueLine;
        this.batchId = batch.getId();
        this.batchNo = batch.getBatchNo();
        this.materialCode = batch.getMaterialCode();
        this.quantity = quantity;
        issueLine.addAllocation(this);
    }

    public Long getId() {
        return id;
    }

    public MaterialIssueLine getIssueLine() {
        return issueLine;
    }

    public Long getBatchId() {
        return batchId;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public String getMaterialCode() {
        return materialCode;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }
}
