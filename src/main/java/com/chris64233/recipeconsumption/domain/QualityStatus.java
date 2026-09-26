package com.chris64233.recipeconsumption.domain;

/**
 * 原料批次质量状态。只有 AVAILABLE 状态的批次允许领料。
 */
public enum QualityStatus {
    /** 合格可用 */
    AVAILABLE,
    /** 待检/隔离，禁止领料 */
    QUARANTINE,
    /** 不合格，禁止领料 */
    REJECTED
}
