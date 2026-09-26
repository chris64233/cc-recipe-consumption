package com.chris64233.recipeconsumption.support;

/**
 * 业务编号（领料号、退料号、调整号、完工号）重复，映射为 HTTP 409。
 */
public class DuplicateBusinessNoException extends RuntimeException {
    public DuplicateBusinessNoException(String message) {
        super(message);
    }
}
