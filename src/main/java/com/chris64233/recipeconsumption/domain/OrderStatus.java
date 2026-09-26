package com.chris64233.recipeconsumption.domain;

/**
 * 生产工单状态。已完工工单拒绝一切领料、退料与库存调整。
 */
public enum OrderStatus {
    /** 已下达，可领料、退料、调整。 */
    OPEN,
    /** 已完工并通过产量核对，只允许查询。 */
    COMPLETED
}
