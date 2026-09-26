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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 领料单：一次领料事务的凭证，bizNo 为幂等业务号。
 * 领料记录创建后不允许修改，纠错通过退料或调整记录完成。
 */
@Entity
@Table(name = "issue_record")
public class IssueRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 幂等业务号，全局唯一 */
    @Column(name = "biz_no", nullable = false, unique = true)
    private String bizNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private ProductionOrder order;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IssueLine> lines = new ArrayList<>();

    protected IssueRecord() {
    }

    public IssueRecord(String bizNo, ProductionOrder order, Instant createdAt) {
        this.bizNo = bizNo;
        this.order = order;
        this.createdAt = createdAt;
    }

    public void addLine(IssueLine line) {
        lines.add(line);
        line.setRecord(this);
    }

    public Long getId() {
        return id;
    }

    public String getBizNo() {
        return bizNo;
    }

    public ProductionOrder getOrder() {
        return order;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<IssueLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
