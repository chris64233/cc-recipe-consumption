package com.chris64233.recipeconsumption.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 领料单行：对应“某配方主料需求被某一种实际物料满足”的一组批次扣减。
 *
 * <ul>
 *   <li>{@code fulfillmentType=PRIMARY}：实际物料即主料，换算系数 1；</li>
 *   <li>{@code fulfillmentType=SUBSTITUTE}：实际物料为允许的替代料，
 *       数量按配方换算系数折成主料量参与用量与替代比例核算。</li>
 * </ul>
 */
@Entity
@Table(name = "material_issue_line",
        uniqueConstraints = @UniqueConstraint(name = "uk_material_issue_line",
                columnNames = {"issue_id", "line_no"}))
public class MaterialIssueLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_id", nullable = false)
    private MaterialIssue issue;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    /** 被满足的配方主料编码。 */
    @Column(name = "requirement_material_code", nullable = false, length = 64)
    private String requirementMaterialCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_type", nullable = false, length = 16)
    private FulfillmentType fulfillmentType;

    /** 实际领用的物料编码（主料或替代料）。 */
    @Column(name = "picked_material_code", nullable = false, length = 64)
    private String pickedMaterialCode;

    @Column(name = "picked_material_name", length = 128)
    private String pickedMaterialName;

    /** 实际扣减数量（实际物料自身单位）。 */
    @Column(name = "picked_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal pickedQuantity;

    /** 换算系数：1 单位实际物料折合多少单位主料。 */
    @Column(name = "conversion_factor", nullable = false, precision = 19, scale = 6)
    private BigDecimal conversionFactor;

    /** 折主料数量 = pickedQuantity × conversionFactor。 */
    @Column(name = "equivalent_primary_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal equivalentPrimaryQuantity;

    @OneToMany(mappedBy = "issueLine", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private Set<MaterialIssueBatchAllocation> allocations = new LinkedHashSet<>();

    protected MaterialIssueLine() {
    }

    public MaterialIssueLine(MaterialIssue issue, int lineNo, String requirementMaterialCode,
                             FulfillmentType fulfillmentType, String pickedMaterialCode,
                             String pickedMaterialName, BigDecimal pickedQuantity,
                             BigDecimal conversionFactor, BigDecimal equivalentPrimaryQuantity) {
        this.issue = issue;
        this.lineNo = lineNo;
        this.requirementMaterialCode = requirementMaterialCode;
        this.fulfillmentType = fulfillmentType;
        this.pickedMaterialCode = pickedMaterialCode;
        this.pickedMaterialName = pickedMaterialName;
        this.pickedQuantity = pickedQuantity;
        this.conversionFactor = conversionFactor;
        this.equivalentPrimaryQuantity = equivalentPrimaryQuantity;
        issue.addLine(this);
    }

    public void addAllocation(MaterialIssueBatchAllocation allocation) {
        allocations.add(allocation);
    }
    public Long getId() {
        return id;
    }

    public MaterialIssue getIssue() {
        return issue;
    }

    public int getLineNo() {
        return lineNo;
    }

    public String getRequirementMaterialCode() {
        return requirementMaterialCode;
    }

    public FulfillmentType getFulfillmentType() {
        return fulfillmentType;
    }

    public String getPickedMaterialCode() {
        return pickedMaterialCode;
    }

    public String getPickedMaterialName() {
        return pickedMaterialName;
    }

    public BigDecimal getPickedQuantity() {
        return pickedQuantity;
    }

    public BigDecimal getConversionFactor() {
        return conversionFactor;
    }

    public BigDecimal getEquivalentPrimaryQuantity() {
        return equivalentPrimaryQuantity;
    }

    public Set<MaterialIssueBatchAllocation> getAllocations() {
        return allocations;
    }
}
