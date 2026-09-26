package com.chris64233.recipeconsumption.domain;

import com.chris64233.recipeconsumption.support.Quantities;
import com.chris64233.recipeconsumption.support.QuantityBalanceException;

import org.hibernate.annotations.Check;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 原料批次：记录某物料一批库存的可用数量、质量状态和有效期。
 *
 * <p>所有库存增减通过 {@link #reserve(BigDecimal)} / {@link #release(BigDecimal)}
 * / {@link #adjust(BigDecimal, String)} 完成，对象层面即保证数量不为负；
 * 数据库另有 CHECK 约束兜底，服务层通过悲观锁串行化并发扣减。
 */
@Entity
@Table(name = "material_batch",
        uniqueConstraints = @UniqueConstraint(name = "uk_material_batch_no", columnNames = "batch_no"))
@Check(constraints = "available_quantity >= 0")
public class MaterialBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_no", nullable = false, length = 64)
    private String batchNo;

    @Column(name = "material_code", nullable = false, length = 64)
    private String materialCode;

    @Column(name = "material_name", length = 128)
    private String materialName;

    @Column(name = "available_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal availableQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality_status", nullable = false, length = 16)
    private QualityStatus qualityStatus = QualityStatus.AVAILABLE;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    /** JPA 乐观锁版本，悲观锁之外的第二重保护。 */
    @Version
    private long version;

    protected MaterialBatch() {
    }

    public MaterialBatch(String batchNo, String materialCode, String materialName,
                         BigDecimal availableQuantity, QualityStatus qualityStatus, LocalDate expiryDate) {
        this.batchNo = batchNo;
        this.materialCode = materialCode;
        this.materialName = materialName;
        this.availableQuantity = Quantities.require("availableQuantity", availableQuantity, false);
        this.qualityStatus = qualityStatus == null ? QualityStatus.AVAILABLE : qualityStatus;
        if (expiryDate == null) {
            throw new IllegalArgumentException("expiryDate 不能为空");
        }
        this.expiryDate = expiryDate;
    }

    /** 扣减可用库存；不足时抛异常，调用方必须整体回滚。 */
    public void reserve(BigDecimal quantity) {
        BigDecimal q = Quantities.require("扣减数量", quantity, true);
        if (this.availableQuantity.compareTo(q) < 0) {
            throw new QuantityBalanceException("批次 " + batchNo + " 可用数量不足: 现有 "
                    + availableQuantity + ", 申请 " + q);
        }
        this.availableQuantity = Quantities.normalize(this.availableQuantity.subtract(q));
    }

    /** 退料/红冲回补库存。 */
    public void release(BigDecimal quantity) {
        BigDecimal q = Quantities.require("回补数量", quantity, true);
        this.availableQuantity = Quantities.normalize(this.availableQuantity.add(q));
    }

    /** 盘点调整：delta 为正表示盘盈，为负表示盘亏，盘亏不得使库存为负。 */
    public void adjust(BigDecimal delta) {
        BigDecimal d = Quantities.normalize(delta);
        if (this.availableQuantity.add(d).compareTo(BigDecimal.ZERO) < 0) {
            throw new QuantityBalanceException("批次 " + batchNo + " 盘亏后库存为负: 现有 "
                    + availableQuantity + ", 调整 " + d);
        }
        this.availableQuantity = Quantities.normalize(this.availableQuantity.add(d));
    }

    public boolean isUsable(LocalDate today) {
        return qualityStatus == QualityStatus.AVAILABLE && !expiryDate.isBefore(today);
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

    public String getMaterialName() {
        return materialName;
    }

    public BigDecimal getAvailableQuantity() {
        return availableQuantity;
    }

    public QualityStatus getQualityStatus() {
        return qualityStatus;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public long getVersion() {
        return version;
    }
}
