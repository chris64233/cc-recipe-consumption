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
 * 一次领料单。业务编号 {@code issueNo} 全局唯一，用于幂等：
 * 同一编号重复提交直接返回原单，不重复扣减库存。
 *
 * <p>领料记录一经写入不可修改（无 setter、不可删除）；
 * 领料错误只能通过退料单 {@link MaterialReturn} 或库存调整 {@link InventoryAdjustment} 修复。
 */
@Entity
@Table(name = "material_issue",
        uniqueConstraints = @UniqueConstraint(name = "uk_material_issue_no", columnNames = "issue_no"))
public class MaterialIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue_no", nullable = false, length = 64)
    private String issueNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "production_order_id", nullable = false)
    private ProductionOrder productionOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(length = 256)
    private String remark;

    @OneToMany(mappedBy = "issue", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<MaterialIssueLine> lines = new ArrayList<>();

    protected MaterialIssue() {
    }

    public MaterialIssue(String issueNo, ProductionOrder productionOrder, String remark) {
        this.issueNo = issueNo;
        this.productionOrder = productionOrder;
        this.remark = remark;
    }

    public void addLine(MaterialIssueLine line) {
        lines.add(line);
    }

    public Long getId() {
        return id;
    }

    public String getIssueNo() {
        return issueNo;
    }

    public ProductionOrder getProductionOrder() {
        return productionOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getRemark() {
        return remark;
    }

    public List<MaterialIssueLine> getLines() {
        return lines;
    }
}
