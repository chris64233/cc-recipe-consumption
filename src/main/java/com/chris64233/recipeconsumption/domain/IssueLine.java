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
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 领料单行：针对某个配方原料的一次领料，可拆到多个批次扣减。
 * equivalentQty 为该行全部扣减折合标准原料的总量。
 */
@Entity
@Table(name = "issue_line")
public class IssueLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id")
    private IssueRecord record;

    /** 配方原料编码（标准原料口径） */
    @Column(name = "recipe_material_code", nullable = false)
    private String recipeMaterialCode;

    /** 本行折标准原料总量 */
    @Column(name = "equivalent_qty", nullable = false, precision = 20, scale = 4)
    private BigDecimal equivalentQty;

    @OneToMany(mappedBy = "line", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IssueLineBatch> batches = new ArrayList<>();

    protected IssueLine() {
    }

    public IssueLine(String recipeMaterialCode) {
        this.recipeMaterialCode = recipeMaterialCode;
    }

    void setRecord(IssueRecord record) {
        this.record = record;
    }

    public void addBatch(IssueLineBatch batch) {
        batches.add(batch);
        batch.setLine(this);
    }

    public Long getId() {
        return id;
    }

    public IssueRecord getRecord() {
        return record;
    }

    public String getRecipeMaterialCode() {
        return recipeMaterialCode;
    }

    public BigDecimal getEquivalentQty() {
        return equivalentQty;
    }

    public void setEquivalentQty(BigDecimal equivalentQty) {
        this.equivalentQty = equivalentQty;
    }

    public List<IssueLineBatch> getBatches() {
        return Collections.unmodifiableList(batches);
    }
}
