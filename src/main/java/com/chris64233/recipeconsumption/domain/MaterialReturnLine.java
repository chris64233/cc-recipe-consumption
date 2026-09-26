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
 * 退料单行：必须对应原始领料的某一行（同一实际物料，沿用其换算系数），
 * 退料数量按原批次逐批回补，累计退料不得超过该行已领数量。
 */
@Entity
@Table(name = "material_return_line")
public class MaterialReturnLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_return_id", nullable = false)
    private MaterialReturn materialReturn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_line_id", nullable = false)
    private MaterialIssueLine issueLine;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "batch_no", nullable = false, length = 64)
    private String batchNo;

    /** 实际物料编码（冗余，便于查询）。 */
    @Column(name = "material_code", nullable = false, length = 64)
    private String materialCode;

    /** 退回的实际物料数量。 */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    /** 折主料数量，按原领料行换算系数计算。 */
    @Column(name = "equivalent_primary_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal equivalentPrimaryQuantity;

    protected MaterialReturnLine() {
    }

    public MaterialReturnLine(MaterialReturn materialReturn, MaterialIssueLine issueLine,
                              MaterialIssueBatchAllocation allocation, BigDecimal quantity,
                              BigDecimal equivalentPrimaryQuantity) {
        this.materialReturn = materialReturn;
        this.issueLine = issueLine;
        this.batchId = allocation.getBatchId();
        this.batchNo = allocation.getBatchNo();
        this.materialCode = issueLine.getPickedMaterialCode();
        this.quantity = quantity;
        this.equivalentPrimaryQuantity = equivalentPrimaryQuantity;
        materialReturn.addLine(this);
    }

    public Long getId() {
        return id;
    }

    public MaterialReturn getMaterialReturn() {
        return materialReturn;
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

    public BigDecimal getEquivalentPrimaryQuantity() {
        return equivalentPrimaryQuantity;
    }
}
