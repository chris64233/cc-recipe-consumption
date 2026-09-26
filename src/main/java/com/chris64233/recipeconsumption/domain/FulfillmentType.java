package com.chris64233.recipeconsumption.domain;

/**
 * 领料明细中，某一主料被满足的方式。
 */
public enum FulfillmentType {
    /** 直接使用配方主料批次。 */
    PRIMARY,
    /** 使用被允许的替代原料。 */
    SUBSTITUTE
}
