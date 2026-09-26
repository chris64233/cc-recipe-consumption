package com.chris64233.recipeconsumption.support;

/**
 * 请求引用的业务对象不存在，或编号重复等请求层面的错误，映射为 HTTP 404 / 409。
 */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
