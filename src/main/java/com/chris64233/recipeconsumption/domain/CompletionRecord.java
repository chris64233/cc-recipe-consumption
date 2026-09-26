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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 工单完工记录：记录实际产量，以及按配方主料逐行核对的用量平衡。
 *
 * <p>每个工单最多一条完工记录（唯一约束）。数量关系不成立时禁止写入、工单不得完工。
 */
@Entity
@Table(name = "completion_record",
        uniqueConstraints = @UniqueConstraint(name = "uk_completion_order", columnNames = "production_order_id"))
public class CompletionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "completion_no", nullable = false, unique = true, length = 64)
    private String completionNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "production_order_id", nullable = false)
    private ProductionOrder productionOrder;

    @Column(name = "actual_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal actualQuantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(length = 256)
    private String remark;

    @OneToMany(mappedBy = "completionRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("requirementMaterialCode ASC")
    private List<CompletionLine> lines = new ArrayList<>();

    protected CompletionRecord() {
    }

    public CompletionRecord(String completionNo, ProductionOrder productionOrder,
                            BigDecimal actualQuantity, String remark) {
        this.completionNo = completionNo;
        this.productionOrder = productionOrder;
        this.actualQuantity = actualQuantity;
        this.remark = remark;
    }

    public void addLine(CompletionLine line) {
        lines.add(line);
    }

    public Long getId() {
        return id;
    }

    public String getCompletionNo() {
        return completionNo;
    }

    public ProductionOrder getProductionOrder() {
        return productionOrder;
    }

    public BigDecimal getActualQuantity() {
        return actualQuantity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getRemark() {
        return remark;
    }

    public List<CompletionLine> getLines() {
        return lines;
    }
}
