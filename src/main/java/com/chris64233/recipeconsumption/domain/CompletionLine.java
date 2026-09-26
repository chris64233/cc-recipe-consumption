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
 * 完工核对明细行（每个配方主料一行，全部折成主料口径）：
 *
 * <pre>
 * 实际领用总量(折主料) = 完工应耗 + 退料量 + 调整核销 + 未说明差异
 * </pre>
 *
 * 其中 {@code 完工应耗 = 标准单耗 × 实际产量}；
 * {@code 未说明差异} 为配平方，落库必须为 0（容差内），否则不得完工。
 * 行内同时给出替代占比，便于审计替代比例上限是否被遵守。
 */
@Entity
@Table(name = "completion_line")
public class CompletionLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "completion_record_id", nullable = false)
    private CompletionRecord completionRecord;

    @Column(name = "requirement_material_code", nullable = false, length = 64)
    private String requirementMaterialCode;

    /** 标准单耗 × 实际产量。 */
    @Column(name = "expected_consumption", nullable = false, precision = 19, scale = 4)
    private BigDecimal expectedConsumption;

    /** 全部领料折主料量（含替代料折算）。 */
    @Column(name = "issued_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal issuedQuantity;

    /** 其中替代料折主料量。 */
    @Column(name = "substituted_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal substitutedQuantity;

    /** 退料折主料量。 */
    @Column(name = "returned_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal returnedQuantity;

    /** 挂工单的库存调整折主料量：正为冲回，负为核销。 */
    @Column(name = "adjusted_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal adjustedQuantity;

    /** 申报损耗（折主料），必须 >= 0。 */
    @Column(name = "declared_loss", nullable = false, precision = 19, scale = 4)
    private BigDecimal declaredLoss;

    /**
     * 未说明差异 = 已领 - 退料 - 调整冲回/核销净值 - 申报损耗 - 应耗。
     * 必须为 0（在统一容差内）才允许完工。
     */
    @Column(name = "unexplained_variance", nullable = false, precision = 19, scale = 4)
    private BigDecimal unexplainedVariance;

    protected CompletionLine() {
    }

    public CompletionLine(CompletionRecord completionRecord, String requirementMaterialCode,
                          BigDecimal expectedConsumption, BigDecimal issuedQuantity,
                          BigDecimal substitutedQuantity, BigDecimal returnedQuantity,
                          BigDecimal adjustedQuantity, BigDecimal declaredLoss,
                          BigDecimal unexplainedVariance) {
        this.completionRecord = completionRecord;
        this.requirementMaterialCode = requirementMaterialCode;
        this.expectedConsumption = expectedConsumption;
        this.issuedQuantity = issuedQuantity;
        this.substitutedQuantity = substitutedQuantity;
        this.returnedQuantity = returnedQuantity;
        this.adjustedQuantity = adjustedQuantity;
        this.declaredLoss = declaredLoss;
        this.unexplainedVariance = unexplainedVariance;
        completionRecord.addLine(this);
    }

    public Long getId() {
        return id;
    }

    public CompletionRecord getCompletionRecord() {
        return completionRecord;
    }

    public String getRequirementMaterialCode() {
        return requirementMaterialCode;
    }

    public BigDecimal getExpectedConsumption() {
        return expectedConsumption;
    }

    public BigDecimal getIssuedQuantity() {
        return issuedQuantity;
    }

    public BigDecimal getSubstitutedQuantity() {
        return substitutedQuantity;
    }

    public BigDecimal getReturnedQuantity() {
        return returnedQuantity;
    }

    public BigDecimal getAdjustedQuantity() {
        return adjustedQuantity;
    }

    public BigDecimal getDeclaredLoss() {
        return declaredLoss;
    }

    public BigDecimal getUnexplainedVariance() {
        return unexplainedVariance;
    }
}
