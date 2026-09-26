package com.chris64233.recipeconsumption.service;

/**
 * 业务规则不满足（如原料不足、替代比例超限、数量关系不成立），映射 HTTP 400。
 */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
