package com.chris64233.recipeconsumption.service;

/** 幂等业务号被用于不同的业务请求，映射 HTTP 409。 */
public class IdempotentConflictException extends RuntimeException {
    public IdempotentConflictException(String message) {
        super(message);
    }
}
