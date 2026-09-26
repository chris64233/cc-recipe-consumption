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
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 库存调整记录：领料错误无法走退料（如批次已报废、盘亏盘盈）时的修复手段。
 *
 * <p>原始领料记录保持不变，只在此追加一条带正负号的调整流水。
 * 挂接到工单的调整（productionOrder 非空）参与该工单的完工核对：
 * 负数调整视为工单损耗核销，正数调整视为余料冲回。
 */
@Entity
@Table(name = "inventory_adjustment",
        uniqueConstraints = @UniqueConstraint(name = "uk_inventory_adjustment_no", columnNames = "adjustment_no"))
public class InventoryAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "adjustment_no", nullable = false, length = 64)
    private String adjustmentNo;

    /** 可空：纯库存盘点调整不挂工单。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_order_id")
    private ProductionOrder productionOrder;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "batch_no", nullable = false, length = 64)
    private String batchNo;

    @Column(name = "material_code", nullable = false, length = 64)
    private String materialCode;

    /** 正：盘盈/回补；负：盘亏/核销。 */
    @Column(name = "delta_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal deltaQuantity;

    /** 该调整折到哪个配方主料（挂工单时必填，用于完工核对）。 */
    @Column(name = "requirement_material_code", length = 64)
    private String requirementMaterialCode;

    /** 折主料数量；挂工单时按 delta 的符号与替代关系换算。 */
    @Column(name = "equivalent_primary_quantity", precision = 19, scale = 4)
    private BigDecimal equivalentPrimaryQuantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(length = 256)
    private String reason;

    protected InventoryAdjustment() {
    }

    public InventoryAdjustment(String adjustmentNo, ProductionOrder productionOrder, MaterialBatch batch,
                               BigDecimal deltaQuantity, String requirementMaterialCode,
                               BigDecimal equivalentPrimaryQuantity, String reason) {
        this.adjustmentNo = adjustmentNo;
        this.productionOrder = productionOrder;
        this.batchId = batch.getId();
        this.batchNo = batch.getBatchNo();
        this.materialCode = batch.getMaterialCode();
        this.deltaQuantity = deltaQuantity;
        this.requirementMaterialCode = requirementMaterialCode;
        this.equivalentPrimaryQuantity = equivalentPrimaryQuantity;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public String getAdjustmentNo() {
        return adjustmentNo;
    }

    public ProductionOrder getProductionOrder() {
        return productionOrder;
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

    public BigDecimal getDeltaQuantity() {
        return deltaQuantity;
    }

    public String getRequirementMaterialCode() {
        return requirementMaterialCode;
    }

    public BigDecimal getEquivalentPrimaryQuantity() {
        return equivalentPrimaryQuantity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getReason() {
        return reason;
    }
}
