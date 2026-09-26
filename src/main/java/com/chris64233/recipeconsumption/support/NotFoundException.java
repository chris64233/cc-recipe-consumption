package com.chris64233.recipeconsumption.support;

/**
 * 业务对象不存在，映射为 HTTP 404。
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
