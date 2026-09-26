package com.chris64233.recipeconsumption.support;

/**
 * 数量关系不成立（库存不足、突破替代比例、完工核对不平衡等），映射为 HTTP 422。
 */
public class QuantityBalanceException extends RuntimeException {
    public QuantityBalanceException(String message) {
        super(message);
    }
}
