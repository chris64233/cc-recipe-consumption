package com.chris64233.recipeconsumption.domain;

/**
 * 原料批次的质量状态。只有 {@link #AVAILABLE} 的批次可以被领料。
 */
public enum QualityStatus {
    /** 合格可用，可参与领料。 */
    AVAILABLE,
    /** 待检，冻结，不可领料。 */
    QUARANTINED,
    /** 不合格/报废，不可领料。 */
    REJECTED
}
