package com.chris64233.recipeconsumption.web;

import com.chris64233.recipeconsumption.support.BusinessRuleException;
import com.chris64233.recipeconsumption.support.DuplicateBusinessNoException;
import com.chris64233.recipeconsumption.support.NotFoundException;
import com.chris64233.recipeconsumption.support.QuantityBalanceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常映射：
 * 404 不存在；409 编号冲突/状态冲突；422 数量关系不成立；400 参数错误；409 并发冲突。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(DuplicateBusinessNoException.class)
    public ResponseEntity<ApiError> handleDuplicate(DuplicateBusinessNoException ex) {
        return build(HttpStatus.CONFLICT, "DUPLICATE_BUSINESS_NO", ex.getMessage());
    }

    @ExceptionHandler(QuantityBalanceException.class)
    public ResponseEntity<ApiError> handleBalance(QuantityBalanceException ex) {
        // 领料/完工数量不平衡时事务回滚，返回 422 提示调用方调整。
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "QUANTITY_NOT_BALANCED", ex.getMessage());
    }

    @ExceptionHandler({BusinessRuleException.class, IllegalStateException.class})
    public ResponseEntity<ApiError> handleBusiness(Exception ex) {
        return build(HttpStatus.CONFLICT, "BUSINESS_RULE_VIOLATION", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> details = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(new ApiError(
                "VALIDATION_FAILED", "请求参数校验失败", Instant.now(), details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "请求体无法解析");
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return build(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", "并发冲突，请重试");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex) {
        // 业务编号唯一约束等并发兜底：应用层已先查重，走到这里通常是并发同号提交。
        return build(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION", "数据冲突（业务编号可能重复）");
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(new ApiError(error, message));
    }
}
