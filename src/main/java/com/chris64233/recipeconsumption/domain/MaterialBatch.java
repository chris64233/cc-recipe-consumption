package com.chris64233.recipeconsumption.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 原料批次：记录可用数量、质量状态和有效期。
 * 扣减通过悲观锁 + 非负校验保证并发安全。
 */
@Entity
@Table(name = "material_batch")
public class MaterialBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_no", nullable = false, unique = true)
    private String batchNo;

    @Column(name = "material_code", nullable = false)
    private String materialCode;

    @Column(name = "available_qty", nullable = false, precision = 20, scale = 4)
    private BigDecimal availableQty;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality_status", nullable = false)
    private QualityStatus qualityStatus;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Version
    private long version;

    protected MaterialBatch() {
    }

    public MaterialBatch(String batchNo, String materialCode, BigDecimal availableQty,
                         QualityStatus qualityStatus, LocalDate expiryDate) {
        this.batchNo = batchNo;
        this.materialCode = materialCode;
        this.availableQty = availableQty;
        this.qualityStatus = qualityStatus;
        this.expiryDate = expiryDate;
    }

    public Long getId() {
        return id;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public String getMaterialCode() {
        return materialCode;
    }

    public BigDecimal getAvailableQty() {
        return availableQty;
    }

    public void setAvailableQty(BigDecimal availableQty) {
        this.availableQty = availableQty;
    }

    public QualityStatus getQualityStatus() {
        return qualityStatus;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }
}
