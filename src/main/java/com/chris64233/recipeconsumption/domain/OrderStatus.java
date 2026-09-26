package com.chris64233.recipeconsumption.domain;

/**
 * 生产工单状态。完工后不允许继续领料、退料或调整。
 */
public enum OrderStatus {
    /** 进行中，可领料 */
    OPEN,
    /** 已完工，数量关系已核对 */
    COMPLETED
}
