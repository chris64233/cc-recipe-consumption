package com.chris64233.recipeconsumption.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 退料单：修复领料错误或退回余料。回补批次库存，参与完工数量核对。
 * 原始领料单 {@link MaterialIssue} 不做任何修改。
 * 退料必须引用一次原始领料，且不能退到已完工工单。
 */
@Entity
@Table(name = "material_return",
        uniqueConstraints = @UniqueConstraint(name = "uk_material_return_no", columnNames = "return_no"))
public class MaterialReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "return_no", nullable = false, length = 64)
    private String returnNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "production_order_id", nullable = false)
    private ProductionOrder productionOrder;

    /** 引用的原始领料单。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_issue_id", nullable = false)
    private MaterialIssue materialIssue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(length = 256)
    private String reason;

    @OneToMany(mappedBy = "materialReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<MaterialReturnLine> lines = new ArrayList<>();

    protected MaterialReturn() {
    }

    public MaterialReturn(String returnNo, ProductionOrder productionOrder,
                          MaterialIssue materialIssue, String reason) {
        this.returnNo = returnNo;
        this.productionOrder = productionOrder;
        this.materialIssue = materialIssue;
        this.reason = reason;
    }

    public void addLine(MaterialReturnLine line) {
        lines.add(line);
    }

    public Long getId() {
        return id;
    }

    public String getReturnNo() {
        return returnNo;
    }

    public ProductionOrder getProductionOrder() {
        return productionOrder;
    }

    public MaterialIssue getMaterialIssue() {
        return materialIssue;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getReason() {
        return reason;
    }

    public List<MaterialReturnLine> getLines() {
        return lines;
    }
}
